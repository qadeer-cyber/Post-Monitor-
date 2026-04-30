package com.affiliatemonitor.app.data

/**
 * Plain Kotlin DTOs the UI talks against. They used to be Retrofit response
 * shapes — now everything is local, and these are just the types the screens
 * read. Keeping the same field names so the screens didn't need rewriting.
 */

data class HealthOut(
    val ok: Boolean,
    val version: String,
    val testMode: Boolean = false,
    val playwrightFallback: Boolean = false,
)

data class SourceOut(
    val id: Int,
    val url: String,
    val name: String? = null,
    val enabled: Boolean,
    val status: String = if (enabled) "active" else "inactive",
    val lastCheckedAt: String? = null,
    val postsFound: Int = 0,
    val validAmazonPosts: Int = 0,
    val createdAt: String,
)

data class SourceValidateIn(val url: String)

data class SourcePreviewPost(
    val url: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val hasAmazonLink: Boolean = false,
)

data class SourcePreview(
    val url: String,
    val isReachable: Boolean,
    val isPublic: Boolean,
    val pageName: String? = null,
    val recentPostsCount: Int = 0,
    val amazonLinksDetected: Int = 0,
    val samplePosts: List<SourcePreviewPost> = emptyList(),
    val error: String? = null,
    val blocked: Boolean = false,
    val httpStatus: Int? = null,
    val htmlTitle: String? = null,
    val htmlSnippet: String? = null,
)

data class SourceCreate(val url: String, val name: String? = null, val enabled: Boolean = true)

data class SourceUpdate(val name: String? = null, val enabled: Boolean? = null)

data class PostOut(
    val id: Int,
    val sourceId: Int,
    val sourcePageName: String? = null,
    val sourcePostUrl: String,
    val originalDescription: String,
    val imageUrl: String? = null,
    val postTime: String? = null,
    val amazonUrl: String,
    val affiliateUrl: String,
    val asin: String,
    val marketplace: String,
    val finalCaption: String,
    val status: String,
    val postedAt: String? = null,
    val rejectedAt: String? = null,
    val createdAt: String,
)

data class DashboardOut(
    val totalMonitoredPages: Int,
    val totalSources: Int = 0,
    val activeSources: Int = 0,
    val newPostsToday: Int,
    val readyPosts: Int,
    val queueSize: Int = 0,
    val validAmazonPosts: Int = 0,
    val duplicatesSkipped: Int,
    val failedImports: Int,
    val lastScanAt: String? = null,
)

data class ScanResult(
    val ok: Boolean,
    val pagesScanned: Int,
    val postsFound: Int,
    val postsImported: Int,
    val duplicatesSkipped: Int,
    val failed: Int,
    val startedAt: String,
    val finishedAt: String,
    val notes: String? = null,
)

data class LogOut(
    val id: Int,
    val createdAt: String,
    val category: String,
    val level: String,
    val message: String,
    val sourceId: Int? = null,
    val detail: String? = null,
)

data class SettingsOut(
    val amazonAssociateTag: String,
    val scanIntervalMinutes: Int,
    val dailyImportLimit: Int,
    val delayBetweenPageScansSeconds: Int,
    val testMode: Boolean = false,
    val backendUrl: String? = null,
)

data class SettingsUpdate(
    val amazonAssociateTag: String? = null,
    val scanIntervalMinutes: Int? = null,
    val dailyImportLimit: Int? = null,
    val delayBetweenPageScansSeconds: Int? = null,
    val testMode: Boolean? = null,
)
