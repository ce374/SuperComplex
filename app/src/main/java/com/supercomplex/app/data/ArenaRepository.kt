package com.supercomplex.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Thin facade over ArenaApiClient + AppDatabase. Provides the operations
 * consumed by UI layers: explore with filters, hydrate blocks/channels,
 * search via REST v2 (no key), channel/user bulk fetches, and history.
 */
class ArenaRepository(
    private val api: ArenaApiClient = ArenaApiClient.get(),
    private val db: AppDatabase = AppDatabase.get()
) {

    suspend fun explore(
        facets: List<String>,
        page: Int,
        per: Int,
        timestamp: String,
        seed: Int? = null,
        random: Boolean = false
    ): SearchResults = api.explore(facets, page, per, timestamp, seed, random)

    suspend fun block(id: Long) = api.block(id)
    suspend fun channel(id: Long) = api.channel(id)
    suspend fun channelContents(id: Long, page: Int, per: Int) = api.channelContents(id, page, per)
    suspend fun blockConnections(id: Long) = api.blockConnections(id)
    suspend fun blockComments(id: Long) = api.blockComments(id)

    suspend fun search(query: String, page: Int = 1, per: Int = 20) = api.search(query, page, per)
    suspend fun restChannelContents(slug: String, page: Int = 1, per: Int = 100) = api.restChannelContents(slug, page, per)
    suspend fun restUserChannels(slug: String, page: Int = 1, per: Int = 100) = api.restUserChannels(slug, page, per)

    // ---- history ----
    fun recentHistory(limit: Int = 50): Flow<List<HistoryEntity>> = db.history().recent(limit)

    suspend fun rememberChannel(id: Long, title: String?) = withContext(Dispatchers.IO) {
        db.history().insert(HistoryEntity(kind = "channel", refId = id, title = title))
    }

    suspend fun rememberChannelBySlug(slug: String, title: String?) = withContext(Dispatchers.IO) {
        db.history().insert(HistoryEntity(kind = "channel", slug = slug, title = title))
    }

    suspend fun rememberUser(slug: String, title: String?) = withContext(Dispatchers.IO) {
        db.history().insert(HistoryEntity(kind = "user", slug = slug, title = title))
    }

    suspend fun rememberBlock(id: Long, title: String?) = withContext(Dispatchers.IO) {
        db.history().insert(HistoryEntity(kind = "block", refId = id, title = title))
    }

    suspend fun clearHistory() = db.history().clear()

    // ---- bookmarks ----
    fun bookmarks(): Flow<List<BookmarkEntity>> = db.bookmarks().all()
    fun isBookmarked(id: Long): Flow<Boolean> = db.bookmarks().isBookmarked(id)
    suspend fun toggleBookmark(b: BlockDetail) = withContext(Dispatchers.IO) {
        val exists = db.bookmarks().isBookmarked(b.id ?: 0).first()
        if (!exists) {
            db.bookmarks().add(
                BookmarkEntity(
                    blockId = b.id ?: 0,
                    title = b.title,
                    href = b.href,
                    typename = b.typename,
                    previewUrl = b.previewImageUrl()
                )
            )
        } else {
            db.bookmarks().remove(b.id ?: 0)
        }
    }

    companion object {
        fun newTimestampIso(): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
    }
}
