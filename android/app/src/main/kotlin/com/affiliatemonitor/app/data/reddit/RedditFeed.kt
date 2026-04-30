package com.affiliatemonitor.app.data.reddit

import com.affiliatemonitor.app.data.amazon.AmazonLink
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Tiny client for Reddit's public JSON listing endpoint:
 *
 *     https://www.reddit.com/r/<sub>.json
 *
 * No login, no OAuth — Reddit serves anonymous JSON to any UA. We pull a single
 * page (default 25 posts) and return every post that contains an Amazon URL
 * either in the `selftext`, `url`, or `body`.
 */
object RedditFeed {

    /** A single Reddit post we successfully parsed. */
    data class Item(
        val subreddit: String,
        val title: String,
        val permalink: String,
        val imageUrl: String?,
        val text: String,
        val amazonUrls: List<String>,
    )

    /**
     * Fetch [subreddits] and return all items containing an Amazon URL, in the
     * order Reddit served them. Each subreddit is fetched independently — a
     * single sub failing does not abort the rest.
     */
    fun fetchHotItems(
        subreddits: List<String>,
        client: OkHttpClient,
        userAgent: String,
        limit: Int = 25,
    ): List<Item> {
        val out = mutableListOf<Item>()
        for (sub in subreddits.map { it.trim().trimStart('r', '/').trim('/') }.filter { it.isNotEmpty() }) {
            runCatching {
                val items = fetchOne(sub, client, userAgent, limit)
                out += items
            }
        }
        return out
    }

    private fun fetchOne(
        sub: String,
        client: OkHttpClient,
        userAgent: String,
        limit: Int,
    ): List<Item> {
        val url = "https://www.reddit.com/r/$sub/hot.json?limit=$limit&raw_json=1"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            return parseListing(sub, body)
        }
    }

    internal fun parseListing(sub: String, json: String): List<Item> {
        val out = mutableListOf<Item>()
        val root = JSONObject(json)
        val data = root.optJSONObject("data") ?: return emptyList()
        val children = data.optJSONArray("children") ?: return emptyList()
        for (i in 0 until children.length()) {
            val child = children.optJSONObject(i) ?: continue
            val item = child.optJSONObject("data") ?: continue
            val title = item.optString("title").orEmpty()
            val selftext = item.optString("selftext").orEmpty()
            val permalink = item.optString("permalink").orEmpty()
            val externalUrl = item.optString("url").orEmpty()
            val image = pickImage(item)

            val text = listOf(title, selftext, externalUrl).filter { it.isNotBlank() }.joinToString("\n\n")
            val amazon = AmazonLink.findAmazonUrls(text)
            if (amazon.isEmpty()) continue

            out += Item(
                subreddit = sub,
                title = title,
                permalink = if (permalink.startsWith("/")) "https://www.reddit.com$permalink" else permalink,
                imageUrl = image,
                text = text,
                amazonUrls = amazon,
            )
        }
        return out
    }

    private fun pickImage(item: JSONObject): String? {
        val preview = item.optJSONObject("preview")
        val previewImages = preview?.optJSONArray("images")
        if (previewImages != null && previewImages.length() > 0) {
            val first = previewImages.optJSONObject(0)
            val source = first?.optJSONObject("source")
            val url = source?.optString("url")
            if (!url.isNullOrBlank()) return url
        }
        val thumb = item.optString("thumbnail")
        if (thumb.startsWith("http")) return thumb
        return null
    }
}
