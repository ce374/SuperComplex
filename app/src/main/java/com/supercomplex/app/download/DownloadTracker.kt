package com.supercomplex.app.download

import android.content.Context
import com.supercomplex.app.data.AppDatabase
import com.supercomplex.app.data.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tracks every download (active + historical) via the Room downloads table,
 * and exports/imports a JSON backup of "what I downloaded" so a user can
 * restore their history across reinstalls.
 */
class DownloadTracker private constructor(private val context: Context) {

    private val dao get() = AppDatabase.get().downloads()
    fun all(): Flow<List<DownloadEntity>> = dao.all()
    fun doneCount(): Flow<Int> = dao.doneCount()
    fun totalBytes(): Flow<Long?> = dao.totalBytes()

    suspend fun delete(rowId: Long) = withContext(Dispatchers.IO) {
        val e = dao.get(rowId) ?: return@withContext
        try { File(e.localPath).delete() } catch (_: Throwable) {}
        dao.delete(rowId)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.done().forEach { runCatching { File(it.localPath).delete() } }
        dao.queue().forEach { runCatching { File(it.localPath).delete() } }
        // delete everything
        dao.done().forEach { dao.delete(it) }
    }

    /** Export done downloads as JSON to the app's external files dir. */
    suspend fun exportBackup(): File = withContext(Dispatchers.IO) {
        val list = dao.done()
        val arr = JSONArray()
        for (e in list) {
            arr.put(JSONObject().apply {
                put("blockId", e.blockId)
                put("sourceType", e.sourceType)
                put("sourceSlug", e.sourceSlug)
                put("blockType", e.blockType)
                put("title", e.title)
                put("originalUrl", e.originalUrl)
                put("localPath", e.localPath)
                put("fileName", e.fileName)
                put("totalBytes", e.totalBytes)
                put("downloadedBytes", e.downloadedBytes)
                put("createdAt", e.createdAt)
            })
        }
        val backupDir = File(DownloadManager.rootDir(context), "_backup")
        backupDir.mkdirs()
        val backup = File(backupDir, "supercomplex-backup-${System.currentTimeMillis()}.json")
        backup.writeText(arr.toString(2))
        backup
    }

    suspend fun importBackup(file: File): Int = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext 0
        val arr = JSONArray(file.readText())
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val e = DownloadEntity(
                blockId = o.optLong("blockId"),
                sourceType = o.optString("sourceType", "Channel"),
                sourceSlug = if (o.has("sourceSlug") && !o.isNull("sourceSlug")) o.getString("sourceSlug") else null,
                blockType = o.optString("blockType", "Block"),
                title = if (o.has("title") && !o.isNull("title")) o.getString("title") else null,
                originalUrl = o.optString("originalUrl"),
                localPath = o.optString("localPath"),
                fileName = o.optString("fileName"),
                totalBytes = o.optLong("totalBytes"),
                downloadedBytes = o.optLong("downloadedBytes"),
                status = "DONE",
                createdAt = o.optLong("createdAt")
            )
            dao.upsert(e)
            n++
        }
        n
    }

    companion object {
        @Volatile private var INSTANCE: DownloadTracker? = null
        fun get(context: Context): DownloadTracker =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadTracker(context.applicationContext).also { INSTANCE = it }
            }
    }
}
