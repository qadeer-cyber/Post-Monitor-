package com.affiliatemonitor.app.data.facebook

import com.affiliatemonitor.app.data.amazon.AmazonLink
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Public Facebook page scraper.
 *
 * Strategy:
 *   1. Fetch HTML with OkHttp using a desktop-style UA.
 *   2. Detect login walls / blocked content explicitly so the scanner can
 *      surface "Facebook blocked content. Try another public page." instead
 *      of failing silently.
 *   3. Parse Open Graph metadata via Jsoup (og:site_name, og:title,
 *      og:description, og:image).
 *   4. Walk the HTML for anything that looks like a public post permalink.
 *   5. Extract Amazon URLs from the page text + raw HTML so short links
 *      inside JSON blobs are picked up too.
 *
 * Stays shallow on purpose — never tries to defeat login walls or solve
 * captchas, never logs in, never touches private endpoints.
 */
object Scraper {

    /** Markers that indicate Facebook has served a login/blocked page rather than real content. */
    private val LOGIN_WALL_TITLE_HINTS = listOf(
        "log in or sign up",
        "log into facebook",
        "you must log in",
        "facebook - log in",
    )

    private val LOGIN_WALL_BODY_HINTS = listOf(
        "you must log in to continue",
        "login required",
        "this content isn't available",
        "this content isn't available right now",
        "page not found",
    )

    data class ScrapedPost(
        val sourcePostUrl: String,
        val description: String = "",
        val imageUrl: String? = null,
        val amazonUrls: List<String> = emptyList(),
    )

    data class ScrapedPage(
        val pageUrl: String,
        val pageName: String? = null,
        val posts: List<ScrapedPost> = emptyList(),
        val error: String? = null,
        /** True when Facebook served a login-wall / blocked HTML rather than real content. */
        val blocked: Boolean = false,
        /** HTTP status code observed on the fetch (null if the call threw before a response). */
        val httpStatus: Int? = null,
        /** Contents of `<title>` in the served HTML, if any. */
        val htmlTitle: String? = null,
        /** First 200 chars of the served HTML, useful for diagnostics. */
        val htmlSnippet: String? = null,
        /** Amazon URLs detected anywhere in the HTML (same content fed into ScrapedPost). */
        val amazonUrlsOnPage: List<String> = emptyList(),
    )

    private val PERMALINK_RE = Regex(
        "(?:https?:)?//(?:www\\.|m\\.|web\\.)?facebook\\.com/" +
            "(?:[^/\\s\"']+/(?:posts|videos|photos)/[^\\s\"'?#]+|" +
            "permalink\\.php\\?story_fbid=[^\\s\"']+|" +
            "story\\.php\\?story_fbid=[^\\s\"']+|" +
            "[^/\\s\"']+/pfbid[0-9A-Za-z]+)",
        RegexOption.IGNORE_CASE,
    )

    fun extractPermalinks(html: String): List<String> {
        val out = LinkedHashSet<String>()
        for (m in PERMALINK_RE.findAll(html)) {
            var raw = m.value
            if (raw.startsWith("//")) raw = "https:$raw"
            out.add(raw)
        }
        return out.toList()
    }

    /** Cheap login-wall / blocked-content detector. */
    private fun looksBlocked(title: String?, bodyText: String, hasOg: Boolean, permalinkCount: Int): Boolean {
        val titleLower = title?.lowercase().orEmpty()
        if (LOGIN_WALL_TITLE_HINTS.any { it in titleLower }) return true
        val bodyLower = bodyText.take(2000).lowercase()
        if (LOGIN_WALL_BODY_HINTS.any { it in bodyLower }) return true
        // No OG metadata AND no detectable post permalinks → almost certainly a login wall or
        // an interstitial page rather than a real public page.
        if (!hasOg && permalinkCount == 0) return true
        return false
    }

    fun parseHtml(html: String, pageUrl: String, httpStatus: Int? = null): ScrapedPage {
        val doc = Jsoup.parse(html)
        val htmlTitle = doc.selectFirst("title")?.text()?.takeIf { it.isNotBlank() }
        val pageName = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
        val pageDesc = doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
        val pageImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
        val hasOg = pageName != null || pageDesc.isNotBlank() || pageImage != null

        val visibleText = doc.text()
        val amazonInPage = AmazonLink.findAmazonUrls("$visibleText $html")
        val permalinks = extractPermalinks(html)
        val htmlSnippet = html.take(200).replace('\n', ' ').replace('\r', ' ').trim()

        val blocked = looksBlocked(htmlTitle, visibleText, hasOg, permalinks.size)

        if (blocked) {
            return ScrapedPage(
                pageUrl = pageUrl,
                pageName = pageName,
                posts = emptyList(),
                error = "Facebook blocked content. Try another public page.",
                blocked = true,
                httpStatus = httpStatus,
                htmlTitle = htmlTitle,
                htmlSnippet = htmlSnippet,
                amazonUrlsOnPage = amazonInPage,
            )
        }

        val posts = when {
            permalinks.isNotEmpty() -> permalinks.map { link ->
                ScrapedPost(
                    sourcePostUrl = link,
                    description = pageDesc,
                    imageUrl = pageImage,
                    amazonUrls = amazonInPage,
                )
            }
            amazonInPage.isNotEmpty() -> listOf(
                ScrapedPost(
                    sourcePostUrl = pageUrl,
                    description = pageDesc,
                    imageUrl = pageImage,
                    amazonUrls = amazonInPage,
                ),
            )
            else -> emptyList()
        }
        return ScrapedPage(
            pageUrl = pageUrl,
            pageName = pageName,
            posts = posts,
            httpStatus = httpStatus,
            htmlTitle = htmlTitle,
            htmlSnippet = htmlSnippet,
            amazonUrlsOnPage = amazonInPage,
        )
    }

    fun fetch(url: String, client: OkHttpClient, userAgent: String): ScrapedPage {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val status = resp.code
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val titleFromHtml = if (body.isNotBlank()) {
                        runCatching { Jsoup.parse(body).selectFirst("title")?.text() }.getOrNull()
                    } else null
                    return ScrapedPage(
                        pageUrl = url,
                        error = "HTTP $status from Facebook",
                        httpStatus = status,
                        htmlTitle = titleFromHtml,
                        htmlSnippet = body.take(200).replace('\n', ' ').replace('\r', ' ').trim(),
                    )
                }
                if (body.isBlank()) {
                    return ScrapedPage(
                        pageUrl = url,
                        error = "Facebook returned an empty response body",
                        httpStatus = status,
                    )
                }
                parseHtml(body, url, httpStatus = status)
            }
        } catch (t: Throwable) {
            ScrapedPage(
                pageUrl = url,
                error = "Network error: ${t.message ?: t.javaClass.simpleName}",
            )
        }
    }
}
