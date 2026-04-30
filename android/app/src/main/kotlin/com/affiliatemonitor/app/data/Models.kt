package com.affiliatemonitor.app.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class HealthOut(
    val ok: Boolean,
    val version: String,
    @Json(name = "test_mode") val testMode: Boolean,
    @Json(name = "playwright_fallback") val playwrightFallback: Boolean,
)

@JsonClass(generateAdapter = false)
data class SourceOut(
    val id: Int,
    val url: String,
    val name: String? = null,
    val enabled: Boolean,
    @Json(name = "last_checked_at") val lastCheckedAt: String? = null,
    @Json(name = "posts_found") val postsFound: Int = 0,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = false)
data class SourceCreate(val url: String, val name: String? = null, val enabled: Boolean = true)

@JsonClass(generateAdapter = false)
data class SourceUpdate(val name: String? = null, val enabled: Boolean? = null)

@JsonClass(generateAdapter = false)
data class PostOut(
    val id: Int,
    @Json(name = "source_id") val sourceId: Int,
    @Json(name = "source_page_name") val sourcePageName: String? = null,
    @Json(name = "source_post_url") val sourcePostUrl: String,
    @Json(name = "original_description") val originalDescription: String,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "post_time") val postTime: String? = null,
    @Json(name = "amazon_url") val amazonUrl: String,
    @Json(name = "affiliate_url") val affiliateUrl: String,
    val asin: String,
    val marketplace: String,
    @Json(name = "final_caption") val finalCaption: String,
    val status: String,
    @Json(name = "posted_at") val postedAt: String? = null,
    @Json(name = "rejected_at") val rejectedAt: String? = null,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = false)
data class DashboardOut(
    @Json(name = "total_monitored_pages") val totalMonitoredPages: Int,
    @Json(name = "new_posts_today") val newPostsToday: Int,
    @Json(name = "ready_posts") val readyPosts: Int,
    @Json(name = "duplicates_skipped") val duplicatesSkipped: Int,
    @Json(name = "failed_imports") val failedImports: Int,
    @Json(name = "last_scan_at") val lastScanAt: String? = null,
)

@JsonClass(generateAdapter = false)
data class ScanResult(
    val ok: Boolean,
    @Json(name = "pages_scanned") val pagesScanned: Int,
    @Json(name = "posts_found") val postsFound: Int,
    @Json(name = "posts_imported") val postsImported: Int,
    @Json(name = "duplicates_skipped") val duplicatesSkipped: Int,
    val failed: Int,
    @Json(name = "started_at") val startedAt: String,
    @Json(name = "finished_at") val finishedAt: String,
    val notes: String? = null,
)

@JsonClass(generateAdapter = false)
data class LogOut(
    val id: Int,
    @Json(name = "created_at") val createdAt: String,
    val category: String,
    val level: String,
    val message: String,
    @Json(name = "source_id") val sourceId: Int? = null,
    val detail: String? = null,
)

@JsonClass(generateAdapter = false)
data class SettingsOut(
    @Json(name = "amazon_associate_tag") val amazonAssociateTag: String,
    @Json(name = "scan_interval_minutes") val scanIntervalMinutes: Int,
    @Json(name = "daily_import_limit") val dailyImportLimit: Int,
    @Json(name = "delay_between_page_scans_seconds") val delayBetweenPageScansSeconds: Int,
    @Json(name = "test_mode") val testMode: Boolean,
    @Json(name = "backend_url") val backendUrl: String? = null,
)

@JsonClass(generateAdapter = false)
data class SettingsUpdate(
    @Json(name = "amazon_associate_tag") val amazonAssociateTag: String? = null,
    @Json(name = "scan_interval_minutes") val scanIntervalMinutes: Int? = null,
    @Json(name = "daily_import_limit") val dailyImportLimit: Int? = null,
    @Json(name = "delay_between_page_scans_seconds") val delayBetweenPageScansSeconds: Int? = null,
    @Json(name = "test_mode") val testMode: Boolean? = null,
    @Json(name = "backend_url") val backendUrl: String? = null,
)
