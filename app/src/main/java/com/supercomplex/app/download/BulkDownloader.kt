package com.supercomplex.app.download

import android.content.Context
import android.util.Log
import com.supercomplex.app.data.ArenaRepository
import com.supercomplex.app.data.AppDatabase
import com.supercomplex.app.data.RestBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads an entire Are.na channel or user into a structured folder:
 *
 *   SuperComplex/<user-slug>/<channel-slug>/<images|texts|links|embeds|attachments>/
 *
 * Pagination is done with the legacy REST v2 endpoint which does not require
 * authentication. Each individual block is handed to DownloadManager which
 * performs resumable multi-connection downloads.
 */
class BulkDownloader private constructor(
    private val context: Context,
    private val repo: ArenaRepository = ArenaRepository(),
    private val mgr: DownloadManager = DownloadManager.get(context)
) {

    data class Job(
        val kind: String,          // "channel" | "user"
        val slug: String,
        val title: String?,
        val total: Int,
        val done: Int
    )

    private val _jobs = MutableStateFlow<Map<String, Job>>(emptyMap())
    val jobs: StateFlow<Map<String, Job>> = _jobs.asStateFlow()

    suspend fun downloadChannel(slug: String): File = withContext(Dispatchers.IO) {
        val root = DownloadManager.rootDir(context)
        // For a standalone channel: SuperComplex/<channel-slug>/<type>/
        val target = File(root, slug)
        target.mkdirs()

        // Discover total
        val (_, totalContents) = repo.restChannelContents(slug, 1, 1)
        val total = if (totalContents > 0) totalContents else 50
        _jobs.value = _jobs.value + (slug to Job("channel", slug, slug, total, 0))

        var page = 1
        var done = 0
        while (true) {
            val (blocks, _) = repo.restChannelContents(slug, page, PER_PAGE)
            if (blocks.isEmpty()) break
            for (block in blocks) {
                try {
                    val rowId = mgr.enqueueBlock(block, slug, "Channel", target)
                    mgr.runDownload(rowId)
                } catch (t: Throwable) {
                    Log.w(TAG, "block ${block.id} failed: ${t.message}")
                }
                done++
                _jobs.value = _jobs.value + (slug to Job("channel", slug, slug, total, done))
            }
            if (blocks.size < PER_PAGE) break
            page++
        }
        target
    }

    suspend fun downloadUser(userSlug: String): File = withContext(Dispatchers.IO) {
        val root = DownloadManager.rootDir(context)
        val target = File(root, userSlug)
        target.mkdirs()

        val (_, totalChannels) = repo.restUserChannels(userSlug, 1, 1)
        val total = if (totalChannels > 0) totalChannels else 10
        _jobs.value = _jobs.value + (userSlug to Job("user", userSlug, userSlug, total, 0))

        var page = 1
        var done = 0
        while (true) {
            val (channels, _) = repo.restUserChannels(userSlug, page, PER_PAGE)
            if (channels.isEmpty()) break
            for (channel in channels) {
                val slug = channel.slug ?: continue
                try {
                    val subTarget = File(target, sanitizeFolder(slug))
                    subTarget.mkdirs()
                    val (_, totalContents) = repo.restChannelContents(slug, 1, 1)
                    val total2 = if (totalContents > 0) totalContents else 50
                    var cPage = 1
                    var cDone = 0
                    while (true) {
                        val (blocks, _) = repo.restChannelContents(slug, cPage, PER_PAGE)
                        if (blocks.isEmpty()) break
                        for (block in blocks) {
                            try {
                                val rowId = mgr.enqueueBlock(block, slug, "User", subTarget)
                                mgr.runDownload(rowId)
                            } catch (_: Throwable) {}
                            cDone++
                        }
                        if (blocks.size < PER_PAGE) break
                        cPage++
                    }
                    done++
                    _jobs.value = _jobs.value + (userSlug to Job("user", userSlug, userSlug, total, done))
                } catch (t: Throwable) {
                    Log.w(TAG, "channel $slug failed: ${t.message}")
                }
            }
            if (channels.size < PER_PAGE) break
            page++
        }
        target
    }

    private fun sanitizeFolder(s: String): String =
        s.replace(Regex("[^A-Za-z0-9 _\\-.]"), "_").trim().take(80).ifBlank { "channel" }

    companion object {
        private const val TAG = "BulkDownloader"
        private const val PER_PAGE = 50

        @Volatile private var INSTANCE: BulkDownloader? = null
        fun get(context: Context): BulkDownloader =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BulkDownloader(context.applicationContext).also { INSTANCE = it }
            }
    }
}
