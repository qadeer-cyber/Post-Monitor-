package com.affiliatemonitor.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sources we monitor. The user adds these manually via the Sources screen — the
 * scanner never touches anything not in this table.
 */
@Entity(
    tableName = "sources",
    indices = [Index(value = ["url"], unique = true)],
)
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val name: String? = null,
    val enabled: Boolean = true,
    val lastCheckedAt: Long? = null,
    val postsFound: Int = 0,
    val validAmazonPosts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Ready-to-copy post in queue / posted / rejected state. */
@Entity(
    tableName = "posts",
    indices = [
        Index(value = ["sourcePostUrl"]),
        Index(value = ["asin"]),
        Index(value = ["captionHash"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
    ],
)
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceId: Int,
    val sourcePageName: String? = null,
    val sourcePostUrl: String,
    val originalDescription: String,
    val imageUrl: String? = null,
    val postTime: Long? = null,
    val amazonUrl: String,
    val affiliateUrl: String,
    val asin: String,
    val marketplace: String,
    val finalCaption: String,
    val captionHash: String,
    val couponCode: String? = null,
    val status: String = "queue", // queue | posted | rejected
    val postedAt: Long? = null,
    val rejectedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A single log line shown in the Logs screen. */
@Entity(
    tableName = "logs",
    indices = [Index(value = ["createdAt"]), Index(value = ["category"])],
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val category: String, // scan | import | link | error
    val level: String = "info", // info | warn | error
    val message: String,
    val sourceId: Int? = null,
    val detail: String? = null,
)

/** One row per scan invocation, used to compute "last scan at" on the dashboard. */
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceId: Int? = null,
    val pagesScanned: Int = 0,
    val postsFound: Int = 0,
    val postsImported: Int = 0,
    val duplicatesSkipped: Int = 0,
    val failed: Int = 0,
    val notes: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)
