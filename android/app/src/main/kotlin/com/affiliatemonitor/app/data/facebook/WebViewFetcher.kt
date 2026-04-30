package com.affiliatemonitor.app.data.facebook

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Hidden-WebView fallback for fetching public Facebook pages when the OkHttp
 * path is soft-blocked (HTTP 4xx, login-wall HTML, missing OG metadata).
 *
 * Strict rules:
 *   * No cookies are kept (no login state).
 *   * No clicks, no scrolls, no form interactions.
 *   * The WebView is destroyed immediately after the HTML is captured.
 *   * Total wall-clock budget: [TOTAL_TIMEOUT_MS]. After that we give up.
 *
 * The WebView only renders publicly-accessible HTML the same way a logged-out
 * browser would; we never attempt to defeat any protection.
 */
object WebViewFetcher {

    private const val TOTAL_TIMEOUT_MS = 10_000L
    private const val SETTLE_DELAY_MS = 2_500L

    /** Result of a WebView render. */
    data class Result(
        val html: String?,
        val finalUrl: String?,
        val error: String?,
    )

    /**
     * Loads [url] in a hidden, cookie-less WebView and returns the rendered HTML.
     *
     * MUST be called from a coroutine on a CoroutineDispatcher of your choice;
     * the WebView itself is created/destroyed on the main thread.
     */
    suspend fun fetch(context: Context, url: String, userAgent: String): Result {
        val mainHandler = Handler(Looper.getMainLooper())
        val appCtx = context.applicationContext
        return withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                renderOnMainThread(appCtx, url, userAgent, mainHandler)
            }
        } ?: Result(html = null, finalUrl = null, error = "WebView timeout after ${TOTAL_TIMEOUT_MS}ms")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun renderOnMainThread(
        appCtx: Context,
        url: String,
        userAgent: String,
        mainHandler: Handler,
    ): Result = suspendCancellableCoroutine { cont ->
        var webView: WebView? = null
        var settled = false
        var pageStartedAt = 0L
        // Snapshot the global CookieManager state so we can restore it on cleanup.
        // setAcceptCookie is process-global; without this, any other WebView in the
        // process (e.g. an OAuth flow) would silently lose cookies for the rest of
        // the app lifetime.
        val previousAcceptCookie =
            runCatching { CookieManager.getInstance().acceptCookie() }.getOrDefault(true)

        fun cleanup() {
            mainHandler.post {
                runCatching {
                    webView?.stopLoading()
                    webView?.destroy()
                }
                webView = null
                runCatching {
                    CookieManager.getInstance().setAcceptCookie(previousAcceptCookie)
                }
            }
        }

        fun finishWith(result: Result) {
            if (settled) return
            settled = true
            cleanup()
            if (cont.isActive) cont.resume(result)
        }

        try {
            // Disable cookies so we never carry over login state. Even if the
            // global CookieManager accepts cookies elsewhere, we explicitly
            // refuse them on this view.
            runCatching { CookieManager.getInstance().setAcceptCookie(false) }

            val view = WebView(appCtx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    userAgentString = userAgent
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    mediaPlaybackRequiresUserGesture = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    runCatching {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url2: String?, favicon: android.graphics.Bitmap?) {
                        pageStartedAt = System.currentTimeMillis()
                    }

                    override fun onPageFinished(view: WebView?, url2: String?) {
                        if (settled) return
                        // Allow dynamic content to settle, then snapshot.
                        mainHandler.postDelayed({
                            if (settled) return@postDelayed
                            val v = view ?: webView
                            if (v == null) {
                                finishWith(Result(null, url2, "WebView destroyed before snapshot"))
                                return@postDelayed
                            }
                            v.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { jsonish ->
                                val html = decodeJsString(jsonish)
                                finishWith(
                                    Result(
                                        html = html,
                                        finalUrl = url2 ?: url,
                                        error = if (html.isNullOrBlank()) "Empty HTML from WebView" else null,
                                    ),
                                )
                            }
                        }, SETTLE_DELAY_MS)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        if (failingUrl == null || failingUrl == url) {
                            finishWith(Result(null, failingUrl, "WebView error $errorCode: ${description ?: ""}"))
                        }
                    }
                }
            }
            webView = view
            view.loadUrl(url)
        } catch (t: Throwable) {
            finishWith(Result(null, null, "WebView setup failed: ${t.message ?: t.javaClass.simpleName}"))
        }

        cont.invokeOnCancellation { cleanup() }
    }

    /**
     * `evaluateJavascript` returns a JSON-encoded string (with surrounding
     * quotes and `\u…`/`\"` escapes). Decode it back to plain HTML.
     */
    private fun decodeJsString(raw: String?): String? {
        if (raw == null) return null
        val s = raw.trim()
        if (s == "null") return null
        if (s.length < 2 || s.first() != '"' || s.last() != '"') return s
        val body = s.substring(1, s.length - 1)
        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                when (val next = body[i + 1]) {
                    '"', '\\', '/' -> { sb.append(next); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    'u' -> {
                        if (i + 5 < body.length) {
                            val hex = body.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar()); i += 6
                            } else {
                                sb.append(c); i += 1
                            }
                        } else {
                            sb.append(c); i += 1
                        }
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c); i += 1
            }
        }
        return sb.toString()
    }
}
