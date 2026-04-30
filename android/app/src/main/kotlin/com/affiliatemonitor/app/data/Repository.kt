package com.affiliatemonitor.app.data

import android.content.Context
import com.affiliatemonitor.app.BuildConfig
import com.affiliatemonitor.app.data.facebook.Scraper
import com.affiliatemonitor.app.data.local.AppDatabase
import com.affiliatemonitor.app.data.local.LogEntity
import com.affiliatemonitor.app.data.local.PostEntity
import com.affiliatemonitor.app.data.local.SourceEntity
import com.affiliatemonitor.app.data.scanner.Scanner
import com.affiliatemonitor.app.data.scanner.formatIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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

    suspend fun validateSource(url: String): SourcePreview {
        val ua = Prefs.userAgentValue(context)
        val client = http()
        val outcome = Scraper.fetchWithFallback(context, url, client, ua)
        return withContext(Dispatchers.IO) {
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
    status = status,
    postedAt = postedAt?.let { formatIso(it) },
    rejectedAt = rejectedAt?.let { formatIso(it) },
    createdAt = formatIso(createdAt),
)
