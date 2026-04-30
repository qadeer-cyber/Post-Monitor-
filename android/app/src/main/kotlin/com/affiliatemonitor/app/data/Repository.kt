package com.affiliatemonitor.app.data

import android.content.Context
import com.affiliatemonitor.app.BuildConfig
import com.affiliatemonitor.app.data.amazon.AmazonLink
import com.affiliatemonitor.app.data.facebook.Scraper
import com.affiliatemonitor.app.data.local.AppDatabase
import com.affiliatemonitor.app.data.local.LogEntity
import com.affiliatemonitor.app.data.local.PostEntity
import com.affiliatemonitor.app.data.local.SourceEntity
import com.affiliatemonitor.app.data.reddit.RedditFeed
import com.affiliatemonitor.app.data.scanner.Importer
import com.affiliatemonitor.app.data.scanner.Scanner
import com.affiliatemonitor.app.data.scanner.formatIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Local-only repository.  Replaces the Retrofit-backed repository used in
 * earlier versions; everything now lives in Room + DataStore on the device.
 *
 * Keeps the same method names the screens already call so the UI didn't have
 * to be rewritten.
 */
class Repository(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val sourceDao = db.sourceDao()
    private val postDao = db.postDao()
    private val logDao = db.logDao()
    private val historyDao = db.scanHistoryDao()

    private val scanner = Scanner(context)
    private val importer = Importer(postDao, sourceDao, logDao)

    private companion object {
        const val VS_FACEBOOK_URL = "internal:facebook-imports"
        const val VS_AMAZON_URL = "internal:amazon-imports"
        fun redditUrl(sub: String) = "internal:reddit:$sub"
    }

    /**
     * Look up (or create) a "virtual" source row used to attribute imported
     * posts that don't come from a user-added Facebook page (e.g. one-off
     * Facebook post URLs, direct Amazon URLs, Reddit deal feed entries).
     *
     * Virtual sources are flagged with `enabled = false` so the legacy page
     * scanner ignores them entirely.
     */
    private suspend fun getOrCreateVirtualSource(url: String, name: String): SourceEntity {
        val existing = sourceDao.byUrl(url)
        if (existing != null) return existing
        val id = sourceDao.insert(
            SourceEntity(url = url, name = name, enabled = false),
        ).toInt()
        return sourceDao.byId(id)!!
    }

    private fun http() = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun health(): HealthOut = withContext(Dispatchers.IO) {
        HealthOut(ok = true, version = BuildConfig.VERSION_NAME, testMode = false)
    }

    suspend fun listSources(): List<SourceOut> = withContext(Dispatchers.IO) {
        sourceDao.all().map { it.toOut() }
    }

    suspend fun validateSource(url: String): SourcePreview = withContext(Dispatchers.IO) {
        val ua = Prefs.userAgentValue(context)
        val client = http()
        val outcome = Scraper.fetchWithFallback(context, url, client, ua)
        val page = outcome.page
        // Persist all structured fetch events first so the Logs tab shows the
        // full okhttp_fetch_failed → webview_fallback_started → webview_parse_*
        // chain in order.
        outcome.events.forEach { ev ->
            logDao.insert(
                LogEntity(
                    category = ev.category,
                    level = ev.level,
                    message = ev.message,
                    detail = ev.detail,
                ),
            )
        }
        // Always log the final preview outcome so the user can see what happened.
        logDao.insert(
            LogEntity(
                category = if (page.blocked) "blocked_page_detected" else "scan",
                level = if (page.error != null || page.blocked) "warn" else "info",
                message = if (page.blocked) {
                    "blocked_page_detected: ${page.htmlTitle ?: "no <title>"}"
                } else if (page.error != null) {
                    "preview_failed: ${page.error}"
                } else {
                    "preview_ok: ${page.posts.size} permalinks, ${page.amazonUrlsOnPage.size} Amazon links" +
                        (if (outcome.usedWebView) " (via WebView fallback)" else "")
                },
                detail = buildScrapeDiagDetail(page),
            ),
        )
        if (page.error != null || page.blocked) {
            SourcePreview(
                url = url,
                isReachable = page.httpStatus != null,
                isPublic = false,
                pageName = page.pageName,
                recentPostsCount = 0,
                amazonLinksDetected = page.amazonUrlsOnPage.size,
                samplePosts = emptyList(),
                error = page.error,
                blocked = page.blocked,
                httpStatus = page.httpStatus,
                htmlTitle = page.htmlTitle,
                htmlSnippet = page.htmlSnippet,
            )
        } else {
            val isPublic = page.pageName != null || page.posts.isNotEmpty()
            SourcePreview(
                url = url,
                isReachable = true,
                isPublic = isPublic,
                pageName = page.pageName,
                recentPostsCount = page.posts.size,
                amazonLinksDetected = page.amazonUrlsOnPage.size,
                samplePosts = page.posts.take(5).map { p ->
                    SourcePreviewPost(
                        url = p.sourcePostUrl,
                        description = p.description,
                        imageUrl = p.imageUrl,
                        hasAmazonLink = p.amazonUrls.isNotEmpty(),
                    )
                },
                error = if (!isPublic) "No public metadata or post permalinks found" else null,
                httpStatus = page.httpStatus,
                htmlTitle = page.htmlTitle,
                htmlSnippet = page.htmlSnippet,
            )
        }
    }

    private fun buildScrapeDiagDetail(page: Scraper.ScrapedPage): String = buildString {
        append("status=").append(page.httpStatus ?: "n/a")
        append(" title=").append(page.htmlTitle?.take(120) ?: "")
        page.htmlSnippet?.takeIf { it.isNotBlank() }?.let {
            append(" snippet=").append(it.take(200))
        }
    }

    suspend fun addSource(url: String, name: String? = null): SourceOut = withContext(Dispatchers.IO) {
        val existing = sourceDao.byUrl(url)
        if (existing != null) return@withContext existing.toOut()
        val now = System.currentTimeMillis()
        val id = sourceDao.insert(
            SourceEntity(url = url, name = name, enabled = true, createdAt = now),
        ).toInt()
        sourceDao.byId(id)!!.toOut()
    }

    suspend fun updateSource(id: Int, update: SourceUpdate): SourceOut = withContext(Dispatchers.IO) {
        val current = sourceDao.byId(id) ?: error("Source $id not found")
        val updated = current.copy(
            name = update.name ?: current.name,
            enabled = update.enabled ?: current.enabled,
        )
        sourceDao.update(updated)
        updated.toOut()
    }

    suspend fun deleteSource(id: Int) = withContext(Dispatchers.IO) {
        sourceDao.delete(id)
    }

    suspend fun scanSource(id: Int): ScanResult = withContext(Dispatchers.IO) {
        scanner.scanOneById(id)
    }

    suspend fun scanAll(): ScanResult = withContext(Dispatchers.IO) {
        scanner.scanAll()
    }

    suspend fun dashboard(): DashboardOut = withContext(Dispatchers.IO) {
        val totalSources = sourceDao.count()
        val activeSources = sourceDao.activeCount()
        val sinceStartOfDay = startOfDayMs()
        val newToday = postDao.countNewSince(sinceStartOfDay)
        val readyPosts = postDao.queueSize()
        val validAmazon = postDao.countAmazonImports()
        val duplicates = historyDao.totalDuplicates()
        val failed = historyDao.totalFailed()
        val lastScan = historyDao.lastFinishedAt()
        DashboardOut(
            totalMonitoredPages = totalSources,
            totalSources = totalSources,
            activeSources = activeSources,
            newPostsToday = newToday,
            readyPosts = readyPosts,
            queueSize = readyPosts,
            validAmazonPosts = validAmazon,
            duplicatesSkipped = duplicates,
            failedImports = failed,
            lastScanAt = lastScan?.let { formatIso(it) },
        )
    }

    suspend fun queue(limit: Int = 100, offset: Int = 0): List<PostOut> = withContext(Dispatchers.IO) {
        postDao.queue(limit, offset).map { it.toOut() }
    }

    suspend fun posted(limit: Int = 100, offset: Int = 0): List<PostOut> = withContext(Dispatchers.IO) {
        postDao.posted(limit, offset).map { it.toOut() }
    }

    suspend fun getPost(id: Int): PostOut = withContext(Dispatchers.IO) {
        postDao.byId(id)?.toOut() ?: error("Post $id not found")
    }

    suspend fun markPosted(id: Int): PostOut = withContext(Dispatchers.IO) {
        val current = postDao.byId(id) ?: error("Post $id not found")
        val updated = current.copy(status = "posted", postedAt = System.currentTimeMillis())
        postDao.update(updated)
        updated.toOut()
    }

    suspend fun rejectPost(id: Int): PostOut = withContext(Dispatchers.IO) {
        val current = postDao.byId(id) ?: error("Post $id not found")
        val updated = current.copy(status = "rejected", rejectedAt = System.currentTimeMillis())
        postDao.update(updated)
        updated.toOut()
    }

    suspend fun logs(category: String? = null, level: String? = null): List<LogOut> =
        withContext(Dispatchers.IO) {
            // Old chip names ("scan", "import", "link", "error") map to multiple new
            // structured categories so filtering still does what users expect.
            val mapped: List<String>? = when (category) {
                null -> null
                "scan" -> listOf(
                    "scan", "scan_started", "scan_finished", "posts_found",
                    "okhttp_fetch_failed", "webview_fallback_started",
                    "webview_html_extracted", "webview_parse_success",
                )
                "import" -> listOf("import", "no_amazon_links_found", "amazon_posts_extracted")
                "link" -> listOf("link", "amazon_posts_extracted")
                "error" -> listOf(
                    "error", "blocked_page_detected", "facebook_block_detected",
                    "webview_parse_failed",
                )
                else -> listOf(category)
            }
            val rows = if (mapped == null) {
                logDao.list(null, level)
            } else {
                logDao.listIn(mapped, level)
            }
            rows.map {
                LogOut(
                    id = it.id,
                    createdAt = formatIso(it.createdAt),
                    category = it.category,
                    level = it.level,
                    message = it.message,
                    sourceId = it.sourceId,
                    detail = it.detail,
                )
            }
        }

    suspend fun getSettings(): SettingsOut = withContext(Dispatchers.IO) {
        SettingsOut(
            amazonAssociateTag = Prefs.amazonTagValue(context),
            scanIntervalMinutes = Prefs.scanIntervalMinValue(context),
            dailyImportLimit = Prefs.dailyLimitValue(context),
            delayBetweenPageScansSeconds = Prefs.delayBetweenScansSecValue(context),
            testMode = false,
        )
    }

    suspend fun patchSettings(update: SettingsUpdate): SettingsOut = withContext(Dispatchers.IO) {
        update.amazonAssociateTag?.let { if (it.isNotBlank()) Prefs.setAmazonTag(context, it.trim()) }
        update.scanIntervalMinutes?.let { Prefs.setScanIntervalMin(context, it) }
        update.dailyImportLimit?.let { Prefs.setDailyLimit(context, it) }
        update.delayBetweenPageScansSeconds?.let { Prefs.setDelayBetweenScansSec(context, it) }
        getSettings()
    }

    private fun startOfDayMs(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // ─── Hybrid input mode (Input Hub) ────────────────────────────────────

    /**
     * Render a single Facebook post URL via the WebView fallback (OkHttp first,
     * WebView if blocked) and import every Amazon link we find on it.
     */
    suspend fun importFacebookPostUrl(postUrl: String): ImportFeedback {
        val url = postUrl.trim()
        if (!looksLikeFacebookPostUrl(url)) {
            return ImportFeedback(false, 0, 0, 1, "That doesn't look like a public Facebook post URL.")
        }
        val ua = Prefs.userAgentValue(context)
        val tag = Prefs.amazonTagValue(context)
        val style = Prefs.captionStyleValue(context)
        val client = http()
        return withContext(Dispatchers.IO) {
            // OkHttp + Jsoup is blocking; the WebView leg internally hops to
            // Dispatchers.Main, so wrapping the whole thing in IO is safe.
            val outcome = Scraper.fetchWithFallback(context, url, client, ua)
            val virtual = getOrCreateVirtualSource(VS_FACEBOOK_URL, "Manual: Facebook posts")
            outcome.events.forEach { ev ->
                logDao.insert(
                    LogEntity(
                        category = ev.category,
                        level = ev.level,
                        message = ev.message,
                        sourceId = virtual.id,
                        detail = ev.detail,
                    ),
                )
            }
            val page = outcome.page
            if (page.blocked || page.error != null) {
                logDao.insert(
                    LogEntity(
                        category = "blocked_page_detected",
                        level = "error",
                        message = "Facebook blocked the post URL — ${page.error ?: "no public content"}",
                        sourceId = virtual.id,
                        detail = "title='${page.htmlTitle?.take(120) ?: ""}'",
                    ),
                )
                return@withContext ImportFeedback(
                    ok = false, imported = 0, duplicates = 0, failed = 1,
                    message = page.error ?: "Facebook blocked content. Try another post URL.",
                )
            }
            val amazonUrls = page.amazonUrlsOnPage
            if (amazonUrls.isEmpty()) {
                logDao.insert(
                    LogEntity(
                        category = "no_amazon_links_found",
                        level = "warn",
                        message = "No Amazon links found on $url",
                        sourceId = virtual.id,
                    ),
                )
                return@withContext ImportFeedback(false, 0, 0, 1, "No Amazon links found on that post.")
            }
            val description = page.posts.firstOrNull()?.description
                ?: page.htmlTitle.orEmpty()
            val imageUrl = page.posts.firstOrNull()?.imageUrl
            var imported = 0
            var duplicates = 0
            var failed = 0
            var lastPostId: Int? = null
            for (amazonUrl in amazonUrls.distinct()) {
                val res = importer.importPost(
                    sourceId = virtual.id,
                    sourcePageName = page.pageName ?: "Facebook post",
                    sourcePostUrl = url,
                    description = description,
                    imageUrl = imageUrl,
                    amazonUrl = amazonUrl,
                    tag = tag,
                    style = style,
                    productTitleOverride = null,
                    http = client,
                )
                when (res.outcome) {
                    Importer.Outcome.Imported -> { imported += 1; lastPostId = res.postId }
                    Importer.Outcome.Duplicate -> duplicates += 1
                    Importer.Outcome.NoAsin -> failed += 1
                    Importer.Outcome.AlreadySeen -> duplicates += 1
                }
            }
            ImportFeedback(
                ok = imported > 0,
                imported = imported,
                duplicates = duplicates,
                failed = failed,
                message = when {
                    imported > 0 -> "Imported $imported post(s) from Facebook URL."
                    duplicates > 0 -> "Already in queue ($duplicates duplicate)."
                    else -> "Could not extract any ASINs from this post."
                },
                postId = lastPostId,
            )
        }
    }

    /**
     * Import a direct Amazon product URL. Tries to fetch the page's `og:title`
     * (or `<title>`) so the deal caption has a real product name; if Amazon
     * blocks the request we fall back to "Amazon deal".
     */
    suspend fun importAmazonUrl(amazonUrl: String): ImportFeedback {
        val url = amazonUrl.trim()
        if (url.isEmpty() || AmazonLink.findAmazonUrls(url).isEmpty()) {
            return ImportFeedback(false, 0, 0, 1, "That doesn't look like an Amazon URL.")
        }
        val tag = Prefs.amazonTagValue(context)
        val style = Prefs.captionStyleValue(context)
        val ua = Prefs.userAgentValue(context)
        val client = http()
        return withContext(Dispatchers.IO) {
            val productTitle = runCatching { fetchAmazonOgTitle(url, client, ua) }.getOrNull()
            val virtual = getOrCreateVirtualSource(VS_AMAZON_URL, "Manual: Amazon URLs")
            val res = importer.importPost(
                sourceId = virtual.id,
                sourcePageName = "Amazon",
                sourcePostUrl = url,
                description = productTitle.orEmpty(),
                imageUrl = null,
                amazonUrl = url,
                tag = tag,
                style = style,
                productTitleOverride = productTitle,
                http = client,
            )
            when (res.outcome) {
                Importer.Outcome.Imported ->
                    ImportFeedback(true, 1, 0, 0, "Imported \"${productTitle ?: "Amazon deal"}\".", res.postId)
                Importer.Outcome.Duplicate ->
                    ImportFeedback(false, 0, 1, 0, "Already in queue.", res.postId)
                Importer.Outcome.NoAsin ->
                    ImportFeedback(false, 0, 0, 1, "Could not extract an ASIN from that URL.")
                Importer.Outcome.AlreadySeen ->
                    ImportFeedback(false, 0, 1, 0, "Already in queue.")
            }
        }
    }

    /**
     * Pull the configured Reddit subs, filter to posts that contain Amazon
     * links, and import each one. Safe to call from WorkManager — it never
     * touches Facebook.
     */
    suspend fun runRedditFeedScan(): ImportFeedback = withContext(Dispatchers.IO) {
        if (!Prefs.redditFeedEnabledValue(context)) {
            return@withContext ImportFeedback(false, 0, 0, 0, "Reddit deals feed is disabled.")
        }
        val tag = Prefs.amazonTagValue(context)
        val style = Prefs.captionStyleValue(context)
        val ua = Prefs.userAgentValue(context)
        val client = http()
        val subs = Prefs.redditSubsValue(context).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (subs.isEmpty()) {
            return@withContext ImportFeedback(false, 0, 0, 0, "No subreddits configured.")
        }
        val items = RedditFeed.fetchHotItems(subs, client, ua)
        var imported = 0
        var duplicates = 0
        var failed = 0
        for (item in items) {
            val virtual = getOrCreateVirtualSource(redditUrl(item.subreddit), "Reddit r/${item.subreddit}")
            for (amazonUrl in item.amazonUrls.distinct()) {
                val res = importer.importPost(
                    sourceId = virtual.id,
                    sourcePageName = "r/${item.subreddit}",
                    sourcePostUrl = item.permalink,
                    description = item.text,
                    imageUrl = item.imageUrl,
                    amazonUrl = amazonUrl,
                    tag = tag,
                    style = style,
                    productTitleOverride = item.title.takeIf { it.isNotBlank() },
                    http = client,
                )
                when (res.outcome) {
                    Importer.Outcome.Imported -> imported += 1
                    Importer.Outcome.Duplicate -> duplicates += 1
                    Importer.Outcome.NoAsin -> failed += 1
                    Importer.Outcome.AlreadySeen -> duplicates += 1
                }
            }
        }
        logDao.insert(
            LogEntity(
                category = "scan_finished",
                level = "info",
                message = "Reddit feed: imported=$imported, duplicates=$duplicates, failed=$failed " +
                    "(${items.size} posts across ${subs.size} subs)",
            ),
        )
        ImportFeedback(
            ok = imported > 0,
            imported = imported,
            duplicates = duplicates,
            failed = failed,
            message = "Reddit: $imported imported, $duplicates duplicate, $failed failed",
        )
    }

    private fun looksLikeFacebookPostUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return false
        if (!lower.contains("facebook.com") && !lower.contains("fb.com") && !lower.contains("fb.watch")) return false
        return true
    }

    /** Fetch a public Amazon product page and return its `og:title` / `<title>`. */
    private fun fetchAmazonOgTitle(url: String, client: OkHttpClient, userAgent: String): String? {
        val req = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val doc = Jsoup.parse(body)
                doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
                    ?: doc.selectFirst("title")?.text()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}

private fun SourceEntity.toOut(): SourceOut = SourceOut(
    id = id,
    url = url,
    name = name,
    enabled = enabled,
    status = if (enabled) "active" else "inactive",
    lastCheckedAt = lastCheckedAt?.let { formatIso(it) },
    postsFound = postsFound,
    validAmazonPosts = validAmazonPosts,
    createdAt = formatIso(createdAt),
)

private fun PostEntity.toOut(): PostOut = PostOut(
    id = id,
    sourceId = sourceId,
    sourcePageName = sourcePageName,
    sourcePostUrl = sourcePostUrl,
    originalDescription = originalDescription,
    imageUrl = imageUrl,
    postTime = postTime?.let { formatIso(it) },
    amazonUrl = amazonUrl,
    affiliateUrl = affiliateUrl,
    asin = asin,
    marketplace = marketplace,
    finalCaption = finalCaption,
    couponCode = couponCode,
    status = status,
    postedAt = postedAt?.let { formatIso(it) },
    rejectedAt = rejectedAt?.let { formatIso(it) },
    createdAt = formatIso(createdAt),
)
