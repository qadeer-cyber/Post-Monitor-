package com.affiliatemonitor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY createdAt DESC")
    suspend fun all(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY id ASC")
    suspend fun activeSources(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun byId(id: Int): SourceEntity?

    @Query("SELECT * FROM sources WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SourceEntity): Long

    @Update
    suspend fun update(entity: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM sources WHERE enabled = 1")
    suspend fun activeCount(): Int

    @Query("UPDATE sources SET lastCheckedAt = :ts WHERE id = :id")
    suspend fun touchLastCheckedAt(id: Int, ts: Long)

    @Query("UPDATE sources SET postsFound = postsFound + 1 WHERE id = :id")
    suspend fun bumpPostsFound(id: Int)

    @Query("UPDATE sources SET validAmazonPosts = validAmazonPosts + 1 WHERE id = :id")
    suspend fun bumpValidAmazonPosts(id: Int)

    @Query("UPDATE sources SET name = :name WHERE id = :id AND (name IS NULL OR name = '')")
    suspend fun setNameIfMissing(id: Int, name: String)
}

@Dao
interface PostDao {
    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun byId(id: Int): PostEntity?

    @Query("SELECT * FROM posts WHERE status = 'queue' ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun queue(limit: Int = 100, offset: Int = 0): List<PostEntity>

    @Query("SELECT * FROM posts WHERE status = 'posted' ORDER BY postedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun posted(limit: Int = 100, offset: Int = 0): List<PostEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PostEntity): Long

    @Update
    suspend fun update(entity: PostEntity)

    @Query("SELECT * FROM posts WHERE sourcePostUrl = :url OR asin = :asin OR captionHash = :captionHash LIMIT 1")
    suspend fun findDuplicate(url: String?, asin: String?, captionHash: String?): PostEntity?

    @Query("SELECT * FROM posts WHERE sourceId = :sourceId AND (sourcePostUrl = :url OR asin = :asin) LIMIT 1")
    suspend fun findDuplicateForSource(sourceId: Int, url: String?, asin: String?): PostEntity?

    @Query("SELECT COUNT(*) FROM posts WHERE status = 'queue'")
    suspend fun queueSize(): Int

    @Query("SELECT COUNT(*) FROM posts WHERE createdAt >= :since")
    suspend fun countNewSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM posts WHERE createdAt >= :since AND asin IS NOT NULL AND asin != ''")
    suspend fun countAmazonImportsSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM posts WHERE asin IS NOT NULL AND asin != ''")
    suspend fun countAmazonImports(): Int
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entity: LogEntity): Long

    @Query("""
        SELECT * FROM logs
        WHERE (:category IS NULL OR category = :category)
          AND (:level IS NULL OR level = :level)
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun list(
        category: String?,
        level: String?,
        limit: Int = 200,
        offset: Int = 0,
    ): List<LogEntity>

    @Query("DELETE FROM logs WHERE createdAt < :cutoff")
    suspend fun trim(cutoff: Long)
}

@Dao
interface ScanHistoryDao {
    @Insert
    suspend fun insert(entity: ScanHistoryEntity): Long

    @Update
    suspend fun update(entity: ScanHistoryEntity)

    @Query("SELECT * FROM scan_history ORDER BY id DESC LIMIT 1")
    suspend fun latest(): ScanHistoryEntity?

    @Query("SELECT MAX(finishedAt) FROM scan_history WHERE finishedAt IS NOT NULL")
    suspend fun lastFinishedAt(): Long?

    @Query("SELECT IFNULL(SUM(duplicatesSkipped), 0) FROM scan_history")
    suspend fun totalDuplicates(): Int

    @Query("SELECT IFNULL(SUM(failed), 0) FROM scan_history")
    suspend fun totalFailed(): Int

    @Query("SELECT IFNULL(SUM(postsImported), 0) FROM scan_history WHERE startedAt >= :since")
    suspend fun importsSince(since: Long): Int
}
