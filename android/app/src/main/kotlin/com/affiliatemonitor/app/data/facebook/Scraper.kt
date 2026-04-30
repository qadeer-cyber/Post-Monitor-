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
 *   2. Parse Open Graph metadata via Jsoup (og:site_name, og:title, og:description, og:image).
 *   3. Walk the HTML for anything that looks like a public post permalink.
 *   4. Extract Amazon URLs from the page text + raw HTML so short links inside JSON
 *      blobs are picked up too.
 *
 * Stays shallow on purpose — never tries to defeat login walls or solve captchas,
 * never logs in, never touches private endpoints.
 */
object Scraper {

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

    fun parseHtml(html: String, pageUrl: String): ScrapedPage {
        val doc = Jsoup.parse(html)
        val pageName = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
        val pageDesc = doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
        val pageImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }

        val visibleText = doc.text()
        val amazonInPage = AmazonLink.findAmazonUrls("$visibleText $html")
        val permalinks = extractPermalinks(html)

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
        return ScrapedPage(pageUrl = pageUrl, pageName = pageName, posts = posts)
    }

    fun fetch(url: String, client: OkHttpClient, userAgent: String): ScrapedPage {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return ScrapedPage(pageUrl = url, error = "HTTP ${resp.code} from $url")
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) ScrapedPage(pageUrl = url, error = "empty response body")
                else parseHtml(body, url)
            }
        } catch (t: Throwable) {
            ScrapedPage(pageUrl = url, error = "network error: ${t.message ?: t.javaClass.simpleName}")
        }
    }
}
