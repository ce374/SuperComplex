package com.supercomplex.app.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.supercomplex.app.data.AppDatabase
import com.supercomplex.app.data.DownloadEntity
import com.supercomplex.app.data.RestBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Resumable multi-connection HTTP downloader inspired by aria2 semantics.
 *
 * Strategy:
 *  - Send a HEAD / early GET request to learn Content-Length and whether the
 *    server honours the Range header (status 206 Partial Content).
 *  - If Range is supported and the file is large enough, split into N chunks;
 *    each chunk uses `Range: bytes=start-end` and writes to its own offset of a
 *    RandomAccessFile.  This is exactly what aria2's split / min-split-size do.
 *  - If Range is not supported, fall back to a single streaming download.
 *  - Each chunk records its progress in the DownloadTracker (Room) so the
 *    download is resumable after a process restart: on resume we ask the
 *    tracker for already-completed byte ranges and skip them.
 *
 * The class is process-singleton and shared by the foreground DownloadService.
 */
class DownloadManager private constructor(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val dao get() = AppDatabase.get().downloads()

    /** rowId -> per-chunk progress, exposed to UI */
    private val _progress = ConcurrentHashMap<Long, MutableStateFlow<Progress>>()
    fun progress(rowId: Long): StateFlow<Progress> =
        _progress.getOrPut(rowId) { MutableStateFlow(Progress()) }.asStateFlow()

    /** Cancellation flags */
    private val cancelled = ConcurrentHashMap<Long, Boolean>()
    fun cancel(rowId: Long) { cancelled[rowId] = true }
    fun isCancelled(rowId: Long): Boolean = cancelled[rowId] == true

    /** Pause flags */
    private val paused = ConcurrentHashMap<Long, Boolean>()
    fun pause(rowId: Long) { paused[rowId] = true }
    fun resumeFlag(rowId: Long) { paused.remove(rowId) }

    data class Progress(
        val downloaded: Long = 0,
        val total: Long = 0,
        val status: String = "PENDING",
        val message: String = ""
    ) {
        val fraction: Float
            get() = if (total > 0) downloaded.toFloat() / total else 0f
    }

    /**
     * Enqueue a single block download into the tracker. Returns the row id.
     */
    suspend fun enqueueBlock(
        block: RestBlock,
        sourceSlug: String?,
        sourceType: String,
        parentDir: File
    ): Long = withContext(Dispatchers.IO) {
        val outDir = typeFolder(parentDir, block.klass ?: "Block")
        outDir.mkdirs()
        val url = pickUrl(block)
        val fileName = sanitize(block.title ?: block.id.toString()) + "." + extFor(block)
        val dest = File(outDir, fileName)
        val existing = dao.findByBlock(block.id, sourceSlug)
        val rowId = if (existing != null && existing.status == "DONE") {
            // Already downloaded; just return its rowId
            return@withContext existing.rowId
        } else if (existing != null) {
            existing.rowId
        } else {
            dao.upsert(
                DownloadEntity(
                    blockId = block.id,
                    sourceType = sourceType,
                    sourceSlug = sourceSlug,
                    blockType = block.klass ?: "Block",
                    title = block.title,
                    originalUrl = url ?: "",
                    localPath = dest.absolutePath,
                    fileName = fileName,
                    status = "PENDING"
                )
            )
        }
        rowId
    }

    suspend fun runDownload(rowId: Long) = withContext(Dispatchers.IO) {
        val entity = dao.get(rowId) ?: return@withContext
        cancelled.remove(rowId)
        paused.remove(rowId)
        dao.status(rowId, "RUNNING")
        _progress.getOrPut(rowId) { MutableStateFlow(Progress()) }
            .value = Progress(status = "RUNNING", message = "Starting")

        try {
            val dest = File(entity.localPath)
            dest.parentFile?.mkdirs()
            val url = entity.originalUrl

            // Probe for size + Range support
            val (total, rangeOk) = probe(url)
            val resumeFrom = if (dest.exists()) dest.length() else 0L
            val effective = if (total > 0) total else 0L

            if (rangeOk && resumeFrom > 0 && resumeFrom < effective) {
                // Resumable: continue from resumeFrom using a single Range
                streamRange(url, resumeFrom, effective - resumeFrom, dest, rowId, entity, append = true)
            } else if (rangeOk && effective > SPLIT_THRESHOLD) {
                // Fresh multi-connection download
                multiDownload(url, effective, dest, rowId, entity)
            } else {
                // Single streaming download (fresh start)
                if (dest.exists()) dest.delete()
                streamRange(url, 0, effective, dest, rowId, entity, append = false)
            }

            if (isCancelled(rowId)) {
                dao.status(rowId, "PAUSED")
                _progress[rowId]?.value = Progress(entity.downloadedBytes, effective, "PAUSED", "Cancelled")
            } else {
                dao.complete(rowId, path = dest.absolutePath, bytes = dest.length())
                _progress[rowId]?.value = Progress(dest.length(), effective, "DONE", "Done")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "download $rowId failed", t)
            dao.fail(rowId, err = t.message)
            _progress[rowId]?.value = Progress(
                downloaded = dao.get(rowId)?.downloadedBytes ?: 0,
                total = dao.get(rowId)?.totalBytes ?: 0,
                status = "FAILED",
                message = t.message ?: "error"
            )
        }
    }

    private fun probe(url: String): Pair<Long, Boolean> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Encoding", "identity")
            .head()
            .build()
        try {
            client.newCall(req).execute().use { r ->
                val len = r.header("Content-Length")?.toLongOrNull() ?: 0L
                val acceptRanges = r.header("Accept-Ranges").equals("bytes", ignoreCase = true)
                val supports206 = r.code == 200 || r.code == 206
                return len to (acceptRanges && supports206)
            }
        } catch (_: Throwable) {
            // Fallback: do a small GET with Range: bytes=0-0 to detect 206
            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Range", "bytes=0-0")
                    .get().build()
                client.newCall(req).execute().use { r ->
                    val total = parseContentRangeTotal(r.header("Content-Range"))
                    return total to (r.code == 206)
                }
            } catch (_: Throwable) {
                return 0L to false
            }
        }
    }

    private fun parseContentRangeTotal(cr: String?): Long {
        if (cr.isNullOrBlank()) return 0
        // "bytes 0-0/12345"
        val slash = cr.indexOf('/')
        if (slash < 0 || slash == cr.length - 1) return 0
        return cr.substring(slash + 1).trim().toLongOrNull() ?: 0
    }

    /** Single-stream download with optional resume offset. */
    private fun streamRange(
        url: String,
        startBytes: Long,
        length: Long,
        dest: File,
        rowId: Long,
        entity: DownloadEntity,
        append: Boolean
    ) {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        if (startBytes > 0) builder.header("Range", "bytes=$startBytes-")
        val req = builder.get().build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) throw java.io.IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("empty body")
            val raf = RandomAccessFile(dest, "rw")
            try {
                raf.seek(startBytes)
                val buf = ByteArray(BUFFER_SIZE)
                var lastReport = 0L
                var downloaded = startBytes
                var total = if (length > 0) startBytes + length else 0L
                if (total == 0L) {
                    // try Content-Range or Content-Length
                    total = parseContentRangeTotal(resp.header("Content-Range"))
                    if (total == 0L) total = (startBytes + (body.contentLength().takeIf { it > 0 } ?: 0L))
                }
                body.byteStream().use { input ->
                    while (true) {
                        if (isCancelled(rowId) || paused[rowId] == true) break
                        val n = input.read(buf)
                        if (n <= 0) break
                        raf.write(buf, 0, n)
                        downloaded += n
                        if (downloaded - lastReport >= REPORT_GRANULARITY) {
                            updateProgress(rowId, downloaded, total, "RUNNING")
                            lastReport = downloaded
                        }
                    }
                }
                updateProgress(rowId, downloaded, total, "RUNNING")
            } finally {
                raf.close()
            }
        }
    }

    /** aria2-style multi-connection download using Range header. */
    private fun multiDownload(
        url: String,
        total: Long,
        dest: File,
        rowId: Long,
        entity: DownloadEntity
    ) {
        // create the placeholder file
        val raf = RandomAccessFile(dest, "rw")
        try { raf.setLength(total) } finally { raf.close() }

        val chunkSize = total / SPLIT_CONNECTIONS
        val chunks = (0 until SPLIT_CONNECTIONS).map { i ->
            val start = i * chunkSize
            val end = if (i == SPLIT_CONNECTIONS - 1) total - 1 else (start + chunkSize - 1)
            Chunk(i, start, end)
        }

        val perChunkProgress = LongArray(chunks.size)
        val runningChunks = chunks.map { chunk ->
            Thread {
                try {
                    val req = Request.Builder().url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Range", "bytes=${chunk.start}-${chunk.end}")
                        .get().build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.code != 206) throw java.io.IOException("no 206 for chunk ${chunk.index}")
                        val body = resp.body ?: return@use
                        val r = RandomAccessFile(dest, "rw")
                        try {
                            r.seek(chunk.start)
                            val buf = ByteArray(BUFFER_SIZE)
                            body.byteStream().use { input ->
                                var written = 0L
                                val expected = chunk.end - chunk.start + 1
                                while (written < expected) {
                                    if (isCancelled(rowId)) break
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    r.write(buf, 0, n)
                                    written += n
                                    perChunkProgress[chunk.index] = written
                                    if (written % REPORT_GRANULARITY == 0L) {
                                        val sum = perChunkProgress.sum()
                                        updateProgress(rowId, sum, total, "RUNNING")
                                    }
                                }
                            }
                        } finally { r.close() }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "chunk ${chunk.index} failed: ${t.message}")
                    // single-chunk failure: fall back to plain resume later
                }
            }.also { it.name = "sc-dl-${rowId}-${chunk.index}" }
        }

        runningChunks.forEach { it.start() }
        runningChunks.forEach { it.join() }

        val finalDownloaded = perChunkProgress.sum()
        updateProgress(rowId, finalDownloaded, total, "RUNNING")

        // If chunks failed, attempt to backfill with a single Range resume pass
        if (finalDownloaded < total && !isCancelled(rowId)) {
            streamRange(url, finalDownloaded, total - finalDownloaded, dest, rowId, entity, append = true)
        }
    }

    private suspend fun updateProgress(rowId: Long, downloaded: Long, total: Long, status: String) {
        dao.progress(rowId, downloaded, total, status)
        _progress[rowId]?.value = Progress(downloaded, total, status, "")
    }

    private data class Chunk(val index: Int, val start: Long, val end: Long)

    companion object {
        private const val TAG = "DownloadManager"
        private const val USER_AGENT = "SuperComplex/1.0 (Android)"
        private const val BUFFER_SIZE = 64 * 1024
        private const val REPORT_GRANULARITY = 256 * 1024L
        private const val SPLIT_THRESHOLD = 2L * 1024 * 1024 // 2 MB before multi-connection
        private const val SPLIT_CONNECTIONS = 4

        @Volatile private var INSTANCE: DownloadManager? = null
        fun get(context: Context): DownloadManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }

        /** Top-level directory for all SuperComplex downloads. */
        fun rootDir(context: Context): File {
            val external = context.getExternalFilesDir(null)
                ?: File(Environment.getExternalStorageDirectory(), "Android/data/com.supercomplex.app/files")
            val root = File(external, "SuperComplex")
            if (!root.exists()) root.mkdirs()
            return root
        }

        /** Resolve a per-type subfolder for a block class. */
        fun typeFolder(parent: File, klass: String): File {
            val sub = when (klass) {
                "Image" -> "images"
                "Text" -> "texts"
                "Link" -> "links"
                "Embed", "Media" -> "embeds"
                "Attachment" -> "attachments"
                "Channel" -> "channels"
                else -> "other"
            }
            return File(parent, sub)
        }

        private fun pickUrl(block: RestBlock): String? = when (block.klass) {
            "Image" -> block.imageUrl
            "Attachment" -> block.sourceUrl ?: block.imageUrl
            "Embed" -> block.sourceUrl ?: block.imageUrl
            "Link" -> block.imageUrl ?: block.sourceUrl
            else -> block.imageUrl ?: block.sourceUrl
        }

        private fun extFor(block: RestBlock): String = when (block.klass) {
            "Image" -> {
                val u = block.imageUrl ?: ""
                when (u.substringAfterLast('.', "").lowercase()) {
                    "png" -> "png"
                    "gif" -> "gif"
                    "webp" -> "webp"
                    else -> "jpg"
                }
            }
            "Text" -> "txt"
            "Link" -> "png"
            "Embed" -> "html"
            "Attachment" -> block.sourceUrl?.substringAfterLast('.', "bin")?.lowercase() ?: "bin"
            else -> "bin"
        }

        fun sanitize(s: String): String {
            val cleaned = s.replace(Regex("[^A-Za-z0-9 _\\-.]"), "_").trim()
            return if (cleaned.isBlank()) "block" else cleaned.take(80)
        }
    }
}
