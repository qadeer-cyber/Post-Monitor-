package com.affiliatemonitor.app.data.scanner

import android.content.Context
import com.affiliatemonitor.app.data.Prefs
import com.affiliatemonitor.app.data.amazon.AmazonLink
import com.affiliatemonitor.app.data.amazon.buildCaption
import com.affiliatemonitor.app.data.amazon.captionHash
import com.affiliatemonitor.app.data.facebook.Scraper
import com.affiliatemonitor.app.data.local.AppDatabase
import com.affiliatemonitor.app.data.local.LogEntity
import com.affiliatemonitor.app.data.local.PostEntity
import com.affiliatemonitor.app.data.local.ScanHistoryEntity
import com.affiliatemonitor.app.data.local.SourceEntity
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Orchestrates a scan: iterate enabled sources, fetch each public page, parse
 * out posts, dedup against existing rows, build affiliate URLs and captions,
 * insert into the queue.  All while:
 *   - respecting an inter-source delay so we're polite,
 *   - stopping early if the daily import cap is reached,
 *   - emitting per-source log lines so the user can see what happened.
 */
class Scanner(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val sourceDao = db.sourceDao()
    private val postDao = db.postDao()
    private val logDao = db.logDao()
    private val historyDao = db.scanHistoryDao()

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun scanAll(): com.affiliatemonitor.app.data.ScanResult {
        val tag = Prefs.amazonTagValue(context)
        val dailyLimit = Prefs.dailyLimitValue(context)
        val delaySec = Prefs.delayBetweenScansSecValue(context).coerceAtLeast(0)
        val ua = Prefs.userAgentValue(context)
        val started = System.currentTimeMillis()
        val historyId = historyDao.insert(ScanHistoryEntity(startedAt = started)).toInt()
        val sources = sourceDao.activeSources()
        val http = client()

        var pagesScanned = 0
        var totalFound = 0
        var totalImported = 0
        var totalDuplicates = 0
        var totalFailed = 0

        val twentyFourHoursAgo = started - 24L * 60 * 60 * 1000
        val alreadyImported = postDao.countAmazonImportsSince(twentyFourHoursAgo)
        var remainingBudget = (dailyLimit - alreadyImported).coerceAtLeast(0)

        if (sources.isEmpty()) {
            logDao.insert(
                LogEntity(
                    category = "scan",
                    level = "info",
                    message = "Scan started: 0 active sources",
                ),
            )
        }

        for ((index, src) in sources.withIndex()) {
            if (remainingBudget <= 0) {
                logDao.insert(
                    LogEntity(
                        category = "scan",
                        level = "warn",
                        message = "Daily import limit ($dailyLimit) reached — stopping scan",
                        sourceId = src.id,
                    ),
                )
                break
            }
            try {
                val outcome = scanOne(src, tag, ua, http, remainingBudget)
                pagesScanned += 1
                totalFound += outcome.found
                totalImported += outcome.imported
                totalDuplicates += outcome.duplicates
                totalFailed += outcome.failed
                remainingBudget = (remainingBudget - outcome.imported).coerceAtLeast(0)
            } catch (t: Throwable) {
                totalFailed += 1
                logDao.insert(
                    LogEntity(
                        category = "error",
                        level = "error",
                        message = "Scan failed for ${src.url}: ${t.message ?: t.javaClass.simpleName}",
                        sourceId = src.id,
                    ),
                )
            }
            if (index < sources.lastIndex && delaySec > 0) {
                delay(delaySec * 1000L)
            }
        }

        val finished = System.currentTimeMillis()
        historyDao.update(
            ScanHistoryEntity(
                id = historyId,
                pagesScanned = pagesScanned,
                postsFound = totalFound,
                postsImported = totalImported,
                duplicatesSkipped = totalDuplicates,
                failed = totalFailed,
                startedAt = started,
                finishedAt = finished,
            ),
        )

        return com.affiliatemonitor.app.data.ScanResult(
            ok = totalFailed == 0,
            pagesScanned = pagesScanned,
            postsFound = totalFound,
            postsImported = totalImported,
            duplicatesSkipped = totalDuplicates,
            failed = totalFailed,
            startedAt = formatIso(started),
            finishedAt = formatIso(finished),
        )
    }

    suspend fun scanOneById(sourceId: Int): com.affiliatemonitor.app.data.ScanResult {
        val src = sourceDao.byId(sourceId)
            ?: return com.affiliatemonitor.app.data.ScanResult(
                ok = false,
                pagesScanned = 0,
                postsFound = 0,
                postsImported = 0,
                duplicatesSkipped = 0,
                failed = 1,
                startedAt = formatIso(System.currentTimeMillis()),
                finishedAt = formatIso(System.currentTimeMillis()),
                notes = "Source not found",
            )
        if (!src.enabled) {
            return com.affiliatemonitor.app.data.ScanResult(
                ok = false,
                pagesScanned = 0,
                postsFound = 0,
                postsImported = 0,
                duplicatesSkipped = 0,
                failed = 1,
                startedAt = formatIso(System.currentTimeMillis()),
                finishedAt = formatIso(System.currentTimeMillis()),
                notes = "Source is disabled",
            )
        }

        val tag = Prefs.amazonTagValue(context)
        val ua = Prefs.userAgentValue(context)
        val dailyLimit = Prefs.dailyLimitValue(context)
        val twentyFourHoursAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val alreadyImported = postDao.countAmazonImportsSince(twentyFourHoursAgo)
        val budget = (dailyLimit - alreadyImported).coerceAtLeast(0)
        val started = System.currentTimeMillis()
        val historyId = historyDao.insert(ScanHistoryEntity(sourceId = src.id, startedAt = started)).toInt()
        val http = client()

        val outcome = try {
            scanOne(src, tag, ua, http, budget)
        } catch (t: Throwable) {
            logDao.insert(
                LogEntity(
                    category = "error",
                    level = "error",
                    message = "Scan failed for ${src.url}: ${t.message ?: t.javaClass.simpleName}",
                    sourceId = src.id,
                ),
            )
            ScanOutcome(found = 0, imported = 0, duplicates = 0, failed = 1)
        }

        val finished = System.currentTimeMillis()
        historyDao.update(
            ScanHistoryEntity(
                id = historyId,
                sourceId = src.id,
                pagesScanned = 1,
                postsFound = outcome.found,
                postsImported = outcome.imported,
                duplicatesSkipped = outcome.duplicates,
                failed = outcome.failed,
                startedAt = started,
                finishedAt = finished,
            ),
        )
        return com.affiliatemonitor.app.data.ScanResult(
            ok = outcome.failed == 0,
            pagesScanned = 1,
            postsFound = outcome.found,
            postsImported = outcome.imported,
            duplicatesSkipped = outcome.duplicates,
            failed = outcome.failed,
            startedAt = formatIso(started),
            finishedAt = formatIso(finished),
        )
    }

    private data class ScanOutcome(val found: Int, val imported: Int, val duplicates: Int, val failed: Int)

    private suspend fun scanOne(
        src: SourceEntity,
        tag: String,
        userAgent: String,
        http: OkHttpClient,
        budgetRemaining: Int,
    ): ScanOutcome {
        logDao.insert(
            LogEntity(
                category = "scan_started",
                level = "info",
                message = "Scan started for ${src.name ?: src.url}",
                sourceId = src.id,
            ),
        )

        var found = 0
        var imported = 0
        var duplicates = 0
        var failed = 0

        try {
            val outcome = Scraper.fetchWithFallback(context, src.url, http, userAgent)
            val page = outcome.page
            sourceDao.touchLastCheckedAt(src.id, System.currentTimeMillis())

            // Replay structured fetch events into the per-source log so users can
            // see the okhttp_fetch_failed → webview_fallback_started → webview_parse_*
            // chain as it happened.
            outcome.events.forEach { ev ->
                logDao.insert(
                    LogEntity(
                        category = ev.category,
                        level = ev.level,
                        message = ev.message,
                        sourceId = src.id,
                        detail = ev.detail,
                    ),
                )
            }

            // Always emit a diagnostic line with HTTP status / title / first 200 chars.
            logDao.insert(
                LogEntity(
                    category = "scan",
                    level = if (page.error != null || page.blocked) "warn" else "info",
                    message = "Fetched ${src.url}: status=${page.httpStatus ?: "n/a"}, " +
                        "title='${page.htmlTitle?.take(80) ?: ""}', " +
                        "permalinks=${page.posts.size}, amazonLinks=${page.amazonUrlsOnPage.size}" +
                        (if (outcome.usedWebView) " (via WebView fallback)" else ""),
                    sourceId = src.id,
                    detail = page.htmlSnippet,
                ),
            )

            if (page.blocked) {
                failed += 1
                logDao.insert(
                    LogEntity(
                        category = "blocked_page_detected",
                        level = "error",
                        message = "Facebook blocked content for ${src.url}. Try another public page.",
                        sourceId = src.id,
                        detail = "title='${page.htmlTitle?.take(120) ?: ""}' " +
                            "snippet='${page.htmlSnippet?.take(200) ?: ""}'",
                    ),
                )
                return ScanOutcome(found, imported, duplicates, failed)
            }

            if (page.error != null) {
                failed += 1
                logDao.insert(
                    LogEntity(
                        category = "error",
                        level = "error",
                        message = "Failed to fetch ${src.url}: ${page.error}",
                        sourceId = src.id,
                        detail = "status=${page.httpStatus ?: "n/a"} title='${page.htmlTitle?.take(120) ?: ""}'",
                    ),
                )
                return ScanOutcome(found, imported, duplicates, failed)
            }

            page.pageName?.takeIf { it.isNotBlank() }?.let { sourceDao.setNameIfMissing(src.id, it) }

            // posts_found is "posts on the page that contain Amazon links" — emit a single
            // summary line up-front so the Logs feed has a clear marker before per-post detail.
            val postsWithAmazon = page.posts.count { it.amazonUrls.isNotEmpty() }
            logDao.insert(
                LogEntity(
                    category = "posts_found",
                    level = "info",
                    message = "Found ${page.posts.size} permalinks on ${src.name ?: src.url}; " +
                        "$postsWithAmazon contain Amazon links",
                    sourceId = src.id,
                ),
            )

            if (postsWithAmazon == 0) {
                logDao.insert(
                    LogEntity(
                        category = "no_amazon_links_found",
                        level = "warn",
                        message = "No Amazon links detected on ${src.name ?: src.url}",
                        sourceId = src.id,
                    ),
                )
            }

            for (post in page.posts) {
                if (imported >= budgetRemaining) {
                    logDao.insert(
                        LogEntity(
                            category = "scan",
                            level = "warn",
                            message = "Daily limit reached during scan of ${src.url}",
                            sourceId = src.id,
                        ),
                    )
                    break
                }
                if (post.amazonUrls.isEmpty()) continue
                val firstAmazon = post.amazonUrls.firstOrNull() ?: continue
                found += 1
                sourceDao.bumpPostsFound(src.id)
                logDao.insert(
                    LogEntity(
                        category = "import",
                        level = "info",
                        message = "Amazon link detected on ${src.name ?: src.url}",
                        sourceId = src.id,
                        detail = firstAmazon,
                    ),
                )
                val parsed = AmazonLink.parse(firstAmazon, tag, http)
                if (parsed == null) {
                    failed += 1
                    logDao.insert(
                        LogEntity(
                            category = "link",
                            level = "warn",
                            message = "Could not extract ASIN from $firstAmazon",
                            sourceId = src.id,
                        ),
                    )
                    continue
                }
                val caption = buildCaption(post.description, parsed.affiliateUrl)
                val cHash = captionHash(caption)
                val dup = postDao.findDuplicate(post.sourcePostUrl, parsed.asin, cHash)
                if (dup != null) {
                    duplicates += 1
                    logDao.insert(
                        LogEntity(
                            category = "import",
                            level = "info",
                            message = "Duplicate skipped (post ${dup.id})",
                            sourceId = src.id,
                            detail = "matched on ${matchReason(dup, post.sourcePostUrl, parsed.asin, cHash)}",
                        ),
                    )
                    continue
                }
                postDao.insert(
                    PostEntity(
                        sourceId = src.id,
                        sourcePageName = page.pageName ?: src.name,
                        sourcePostUrl = post.sourcePostUrl,
                        originalDescription = post.description,
                        imageUrl = post.imageUrl,
                        postTime = null,
                        amazonUrl = parsed.normalizedUrl,
                        affiliateUrl = parsed.affiliateUrl,
                        asin = parsed.asin,
                        marketplace = parsed.marketplace,
                        finalCaption = caption,
                        captionHash = cHash,
                    ),
                )
                imported += 1
                sourceDao.bumpValidAmazonPosts(src.id)
                logDao.insert(
                    LogEntity(
                        category = "amazon_posts_extracted",
                        level = "info",
                        message = "Imported ASIN ${parsed.asin} (${parsed.marketplace})",
                        sourceId = src.id,
                        detail = parsed.affiliateUrl,
                    ),
                )
            }
        } finally {
            logDao.insert(
                LogEntity(
                    category = "scan_finished",
                    level = if (failed == 0) "info" else "warn",
                    message = "Scan finished for ${src.name ?: src.url}: " +
                        "posts_found=$found, amazon_posts_extracted=$imported, errors=$failed",
                    sourceId = src.id,
                ),
            )
        }

        return ScanOutcome(found, imported, duplicates, failed)
    }

    private fun matchReason(dup: PostEntity, url: String?, asin: String?, hash: String?): String {
        val reasons = mutableListOf<String>()
        if (url != null && dup.sourcePostUrl == url) reasons += "source_post_url"
        if (asin != null && dup.asin == asin) reasons += "asin"
        if (hash != null && dup.captionHash == hash) reasons += "caption_hash"
        return reasons.joinToString(",").ifBlank { "unknown" }
    }
}

internal fun formatIso(epochMs: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    return instant.toString()
}
