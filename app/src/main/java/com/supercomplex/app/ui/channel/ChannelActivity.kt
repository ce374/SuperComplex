package com.supercomplex.app.ui.channel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.supercomplex.app.R
import com.supercomplex.app.data.ArenaRepository
import com.supercomplex.app.data.BlockDetail
import com.supercomplex.app.data.ChannelDetail
import com.supercomplex.app.data.IdRef
import com.supercomplex.app.databinding.ActivityChannelBinding
import com.supercomplex.app.download.BulkDownloader
import com.supercomplex.app.download.DownloadService
import com.supercomplex.app.ui.block.BlockActivity
import com.supercomplex.app.ui.common.GridAdapter
import com.supercomplex.app.ui.common.GridEntry
import com.supercomplex.app.util.openUrl
import com.supercomplex.app.util.spanForWidth
import com.supercomplex.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelActivity : AppCompatActivity() {

    private lateinit var b: ActivityChannelBinding
    private val repo = ArenaRepository()
    private lateinit var adapter: GridAdapter
    private var channelId: Long = 0
    private var channelSlug: String? = null
    private var channelTitle: String? = null
    private var loading = false
    private var currentPage = 1
    private var isLastPage = false
    private val perPage = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(b.root)

        channelId = intent.getLongExtra(EXTRA_ID, 0L)
        channelSlug = intent.getStringExtra(EXTRA_SLUG)
        channelTitle = intent.getStringExtra(EXTRA_TITLE)
        b.channelTitle.text = channelTitle ?: ""

        b.btnBack.setOnClickListener { finish() }
        b.btnOpenExternal.setOnClickListener {
            val url = if (channelSlug != null) "https://www.are.na/$channelSlug"
            else "https://www.are.na/channel/$channelId"
            openUrl(this, url)
        }
        b.btnDownload.setOnClickListener {
            val slug = channelSlug
            if (slug.isNullOrBlank()) {
                toast(this, "Channel slug unavailable; cannot bulk download")
                return@setOnClickListener
            }
            toast(this, "Bulk download started: $slug", long = true)
            lifecycleScope.launch {
                BulkDownloader.get(this@ChannelActivity).downloadChannel(slug)
            }
        }

        adapter = GridAdapter(
            onClickBlock = { block -> startActivity(BlockActivity.intent(this, block.id ?: 0)) },
            onClickChannel = { channel ->
                startActivity(intent(this, channel.id ?: 0, channel.title, channel.href?.replaceFirst("/", "")))
            }
        )
        val span = spanForWidth(this)
        val lm = GridLayoutManager(this, span)
        b.grid.layoutManager = lm
        b.grid.adapter = adapter
        b.grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || loading || isLastPage) return
                val total = lm.itemCount
                val last = lm.findLastVisibleItemPosition()
                if (last + 6 >= total) loadNext()
            }
        })

        loadFirst()
    }

    private fun loadFirst() {
        b.loader.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                if (channelSlug != null) {
                    // REST v2 supports slug-based channel contents without auth
                    loadViaRest(channelSlug!!)
                } else {
                    loadViaGraphQL(channelId)
                }
            } catch (t: Throwable) {
                toast(this@ChannelActivity, "Failed: ${t.message}")
            } finally {
                b.loader.visibility = android.view.View.GONE
            }
        }
    }

    private suspend fun loadViaGraphQL(id: Long) = withContext(Dispatchers.IO) {
        val ch = repo.channel(id) ?: return@withContext
        withContext(Dispatchers.Main) {
            channelSlug = ch.href?.trimStart('/')
            channelTitle = ch.title
            b.channelTitle.text = ch.title
            val count = ch.counts?.contents ?: 0
            b.channelMeta.text = "${ch.owner?.name ?: "?"} · $count blocks"
        }
        currentPage = 1
        loadNextGraphQL(id)
    }

    private suspend fun loadNextGraphQL(id: Long) = withContext(Dispatchers.IO) {
        loading = true
        val ch = repo.channelContents(id, currentPage, perPage) ?: return@withContext
        val contents = ch.contents.orEmpty()
        if (contents.isEmpty()) { isLastPage = true; loading = false; return@withContext }
        val entries = contents.map { GridEntry.BlockItem(it) }
        withContext(Dispatchers.Main) {
            val cur = adapter.currentList.toMutableList(); cur.addAll(entries); adapter.submitList(cur)
            currentPage++
            if (contents.size < perPage) isLastPage = true
            loading = false
        }
    }

    private suspend fun loadViaRest(slug: String) = withContext(Dispatchers.IO) {
        // pull channel metadata + first page
        val (blocks, total) = repo.restChannelContents(slug, 1, perPage)
        if (total > 0) b.channelMeta.text = "$total blocks · /$slug"
        val entries = blocks.map { it.toGridEntry() }
        withContext(Dispatchers.Main) {
            adapter.submitList(entries)
            currentPage = 2
            if (blocks.size < perPage) isLastPage = true
        }
    }

    private fun loadNext() {
        if (loading || isLastPage) return
        loading = true
        b.loader.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val slug = channelSlug
                if (slug != null) {
                    val (blocks, _) = withContext(Dispatchers.IO) { repo.restChannelContents(slug, currentPage, perPage) }
                    val entries = blocks.map { it.toGridEntry() }
                    val cur = adapter.currentList.toMutableList(); cur.addAll(entries); adapter.submitList(cur)
                    currentPage++
                    if (blocks.size < perPage) isLastPage = true
                } else {
                    withContext(Dispatchers.IO) { loadNextGraphQL(channelId) }
                }
            } catch (t: Throwable) {
                toast(this@ChannelActivity, "Failed: ${t.message}")
            } finally {
                loading = false
                b.loader.visibility = android.view.View.GONE
            }
        }
    }

    private fun com.supercomplex.app.data.RestBlock.toGridEntry(): GridEntry.BlockItem = GridEntry.BlockItem(
        BlockDetail(
            typename = when (klass) {
                "Image" -> "Image"
                "Text" -> "Text"
                "Link" -> "Link"
                "Embed" -> "Embed"
                "Attachment" -> "Attachment"
                else -> "Block"
            },
            id = id,
            title = title,
            href = "/block/$id",
            contentHtml = content,
            imageUrl = imageUrl,
            sourceUrl = sourceUrl,
            embedHtml = embedHtml,
            createdAt = createdAt
        )
    )

    companion object {
        private const val EXTRA_ID = "channel_id"
        private const val EXTRA_SLUG = "channel_slug"
        private const val EXTRA_TITLE = "channel_title"

        fun intent(context: Context, id: Long, title: String? = null, slug: String? = null): Intent =
            Intent(context, ChannelActivity::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SLUG, slug)
    }
}
