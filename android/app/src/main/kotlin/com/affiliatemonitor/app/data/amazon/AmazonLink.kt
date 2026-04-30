package com.affiliatemonitor.app.data.amazon

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin port of the Python amazon.py service. Detects Amazon URLs in
 * arbitrary text, extracts ASINs, resolves amzn.to short links, and rewrites
 * canonical product URLs with the user's associate tag.
 */
object AmazonLink {

    val SUPPORTED_DOMAINS = listOf(
        "amazon.com",
        "amazon.co.uk",
        "amazon.in",
        "amazon.ae",
        "amazon.ca",
        "amazon.com.au",
        "amazon.de",
        "amazon.fr",
        "amazon.it",
        "amazon.es",
        "amazon.co.jp",
        "amazon.com.mx",
        "amazon.com.br",
        "amazon.sg",
        "amazon.nl",
        "amazon.se",
        "amazon.pl",
    )

    val SHORT_DOMAINS = listOf("amzn.to", "amzn.eu", "a.co")

    private val ASIN_PATH_RE = Regex(
        "/(?:dp|gp/product|gp/aw/d|product|exec/obidos/asin|o/ASIN)/([A-Z0-9]{10})(?:[/?]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val ASIN_ALT_RE = Regex(
        "/ASIN/([A-Z0-9]{10})(?:[/?]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val ASIN_QUERY_RE = Regex(
        "(?:^|[?&])asin=([A-Z0-9]{10})",
        RegexOption.IGNORE_CASE,
    )

    val URL_RE = Regex(
        "https?://[^\\s<>\"')]+",
        RegexOption.IGNORE_CASE,
    )

    data class Parsed(
        val originalUrl: String,
        val normalizedUrl: String,
        val affiliateUrl: String,
        val asin: String,
        val marketplace: String,
    )

    private fun host(url: String): String = try {
        val u = URI(url)
        var n = (u.host ?: "").lowercase()
        if (n.startsWith("www.")) n = n.removePrefix("www.")
        n.substringBefore(":")
    } catch (_: Throwable) {
        ""
    }

    fun isAmazonHost(url: String): Boolean {
        val h = host(url)
        return h in SUPPORTED_DOMAINS || h in SHORT_DOMAINS
    }

    fun isShortLink(url: String): Boolean = host(url) in SHORT_DOMAINS

    /** All Amazon URLs (incl. short links) found in arbitrary text, deduped, in order. */
    fun findAmazonUrls(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        val seen = LinkedHashSet<String>()
        URL_RE.findAll(text).forEach { m ->
            val raw = m.value.trimEnd(')', ',', '.', ';', '!', '?', '"', '\'')
            if (isAmazonHost(raw)) seen.add(raw)
        }
        return seen.toList()
    }

    /** Best-effort ASIN extraction. */
    fun extractAsin(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val u = try { URI(url) } catch (_: Throwable) { return null }
        val path = u.rawPath ?: ""
        ASIN_PATH_RE.find(path)?.let { return it.groupValues[1].uppercase() }
        ASIN_ALT_RE.find(path)?.let { return it.groupValues[1].uppercase() }
        ASIN_QUERY_RE.find(u.rawQuery ?: "")?.let { return it.groupValues[1].uppercase() }
        return null
    }

    fun detectMarketplace(url: String): String {
        val h = host(url)
        return if (h in SUPPORTED_DOMAINS) h else "amazon.com"
    }

    fun buildAffiliateUrl(marketplace: String, asin: String, tag: String): String {
        val asinSafe = URLEncoder.encode(asin, "UTF-8")
        val tagSafe = URLEncoder.encode(tag, "UTF-8")
        return "https://www.$marketplace/dp/$asinSafe?tag=$tagSafe"
    }

    private val DROP_PREFIXES = listOf("utm_", "pf_rd_", "pd_rd_", "ref_")
    private val DROP_EXACT = setOf("ref", "tag", "linkCode", "linkId", "ascsubtag", "ascsubtagreserved")

    /** Return URL with common tracking params stripped; path untouched. */
    fun stripTracking(url: String): String {
        val u = try { URI(url) } catch (_: Throwable) { return url }
        val q = u.rawQuery ?: return url
        val kept = q.split("&").filter { pair ->
            if (pair.isEmpty()) return@filter false
            val k = pair.substringBefore("=")
            k !in DROP_EXACT && DROP_PREFIXES.none { k.startsWith(it) }
        }
        val newQuery = if (kept.isEmpty()) null else kept.joinToString("&")
        return URI(u.scheme, u.userInfo, u.host, u.port, u.rawPath, newQuery, u.fragment).toString()
    }

    /** Follow HEAD/GET redirects on amzn.to / a.co short links. */
    fun resolveShortLink(url: String, client: OkHttpClient): String {
        if (!isShortLink(url)) return url
        return try {
            val req = Request.Builder().url(url).head().build()
            client.newCall(req).execute().use { resp ->
                resp.request.url.toString()
            }
        } catch (_: Throwable) {
            try {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { it.request.url.toString() }
            } catch (_: Throwable) {
                url
            }
        }
    }

    fun parse(
        url: String,
        associateTag: String,
        client: OkHttpClient,
    ): Parsed? {
        if (url.isBlank()) return null
        val resolved = if (isShortLink(url)) resolveShortLink(url, client) else url
        val asin = extractAsin(resolved) ?: return null
        val marketplace = detectMarketplace(resolved)
        val normalized = stripTracking(resolved)
        val affiliate = buildAffiliateUrl(marketplace, asin, associateTag)
        return Parsed(
            originalUrl = url,
            normalizedUrl = normalized,
            affiliateUrl = affiliate,
            asin = asin,
            marketplace = marketplace,
        )
    }

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
