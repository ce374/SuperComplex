package com.supercomplex.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes=:downloaded, totalBytes=:total, status=:status, updatedAt=:ts WHERE rowId=:id")
    suspend fun progress(id: Long, downloaded: Long, total: Long, status: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status=:status, updatedAt=:ts WHERE rowId=:id")
    suspend fun status(id: Long, status: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status=:status, error=:err, updatedAt=:ts WHERE rowId=:id")
    suspend fun fail(id: Long, status: String = "FAILED", err: String?, ts: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status=:status, localPath=:path, downloadedBytes=:bytes, updatedAt=:ts WHERE rowId=:id")
    suspend fun complete(id: Long, status: String = "DONE", path: String, bytes: Long, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM downloads WHERE rowId=:id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE blockId=:blockId AND sourceSlug=:slug LIMIT 1")
    suspend fun findByBlock(blockId: Long, slug: String?): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING','RUNNING','PAUSED') ORDER BY createdAt ASC")
    suspend fun queue(): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status='DONE'")
    fun doneCount(): Flow<Int>

    @Query("SELECT SUM(downloadedBytes) FROM downloads")
    fun totalBytes(): Flow<Long?>

    @Query("DELETE FROM downloads WHERE rowId=:id")
    suspend fun delete(id: Long)

    @Delete
    suspend fun delete(entity: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE status='DONE' ORDER BY updatedAt DESC")
    suspend fun done(): List<DownloadEntity>
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE rowId=:id")
    suspend fun delete(id: Long)
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(b: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE blockId=:id")
    suspend fun remove(id: Long)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun all(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE blockId=:id)")
    fun isBookmarked(id: Long): Flow<Boolean>
}
