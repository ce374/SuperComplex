package com.supercomplex.app.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.supercomplex.app.R
import com.supercomplex.app.data.ArenaRepository
import com.supercomplex.app.data.AppDatabase
import com.supercomplex.app.data.BlockDetail
import com.supercomplex.app.data.BlockType
import com.supercomplex.app.data.ChannelDetail
import com.supercomplex.app.data.Comment
import com.supercomplex.app.data.RestBlock
import com.supercomplex.app.databinding.ActivityBlockBinding
import com.supercomplex.app.download.DownloadManager
import com.supercomplex.app.download.DownloadService
import com.supercomplex.app.ui.channel.ChannelActivity
import com.supercomplex.app.ui.common.SimpleTextAdapter
import com.supercomplex.app.ui.common.SimpleTextItem
import com.supercomplex.app.util.openUrl
import com.supercomplex.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BlockActivity : AppCompatActivity() {

    private lateinit var b: ActivityBlockBinding
    private val repo = ArenaRepository()
    private var blockId: Long = 0
    private var blockDetail: BlockDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBlockBinding.inflate(layoutInflater)
        setContentView(b.root)

        blockId = intent.getLongExtra(EXTRA_ID, 0L)
        b.blockTitle.text = "Block $blockId"

        b.btnBack.setOnClickListener { finish() }
        b.btnShare.setOnClickListener { shareBlock() }
        b.btnOpenExternal.setOnClickListener {
            openUrl(this, "https://www.are.na/block/$blockId")
        }
        b.btnBookmark.setOnClickListener {
            val d = blockDetail ?: return@setOnClickListener
            lifecycleScope.launch {
                repo.toggleBookmark(d)
                toast(this@BlockActivity, "Toggled bookmark")
            }
        }
        b.btnDownload.setOnClickListener { downloadThis() }

        load()
    }

    private fun load() {
        b.connectionsList.layoutManager = LinearLayoutManager(this)
        b.commentsList.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch {
            try {
                val d = withContext(Dispatchers.IO) { repo.block(blockId) }
                if (d == null) {
                    toast(this@BlockActivity, "Block not found")
                    return@launch
                }
                blockDetail = d
                render(d)
                loadConnections(d.id ?: 0)
                loadComments(d.id ?: 0)
            } catch (t: Throwable) {
                toast(this@BlockActivity, "Failed: ${t.message}")
            }
        }
    }

    private fun render(d: BlockDetail) {
        b.blockTitle.text = d.title ?: d.fileName ?: "Block ${d.id}"
        b.blockMeta.text = buildString {
            d.typename?.let { append(it) }
            d.source?.providerName?.let { append(" · ").append(it) }
            d.createdAt?.let { append(" · ").append(it) }
        }

        // Show image preview for image / link / attachment
        val previewUrl = d.previewImageUrl()
        if (previewUrl != null) {
            b.blockImage.visibility = android.view.View.VISIBLE
            b.blockImage.load(previewUrl) {
                crossfade(true)
                placeholder(R.color.bg_image_ph)
                error(R.color.bg_image_ph)
            }
        } else {
            b.blockImage.visibility = android.view.View.GONE
        }

        if (!d.rawContent.isNullOrBlank() || !d.contentHtml.isNullOrBlank()) {
            b.blockText.visibility = android.view.View.VISIBLE
            b.blockText.text = d.rawContent ?: d.contentHtml
        } else {
            b.blockText.visibility = android.view.View.GONE
        }

        if (!d.description.isNullOrBlank()) {
            b.blockDescription.visibility = android.view.View.VISIBLE
            b.blockDescription.text = d.description
        } else {
            b.blockDescription.visibility = android.view.View.GONE
        }

        // Bookmark state
        lifecycleScope.launch {
            val on = try { repo.isBookmarked(d.id ?: 0).first() } catch (_: Throwable) { false }
            b.btnBookmark.setColorFilter(if (on) getColor(R.color.accent) else getColor(R.color.text_muted))
        }
    }

    private fun loadConnections(id: Long) {
        lifecycleScope.launch {
            try {
                val conns = withContext(Dispatchers.IO) { repo.blockConnections(id) }
                b.connectionsList.adapter = SimpleTextAdapter(
                    items = conns.map {
                        SimpleTextItem(it.title ?: "channel", "${it.counts?.contents ?: 0} blocks · ${it.owner?.name ?: ""}")
                    },
                    onClick = { idx ->
                        val c = conns[idx]
                        val slug = c.href?.trimStart('/')
                        startActivity(ChannelActivity.intent(this@BlockActivity, c.id ?: 0, c.title, slug))
                    }
                )
            } catch (_: Throwable) {}
        }
    }

    private fun loadComments(id: Long) {
        lifecycleScope.launch {
            try {
                val comments = withContext(Dispatchers.IO) { repo.blockComments(id) }
                b.commentsList.adapter = SimpleTextAdapter(
                    items = comments.map {
                        SimpleTextItem(
                            it.body?.let { stripHtml(it) } ?: "",
                            "${it.user?.name ?: "?"} · ${it.createdAt ?: ""}"
                        )
                    },
                    onClick = {}
                )
            } catch (_: Throwable) {}
        }
    }

    private fun stripHtml(s: String): String =
        s.replace(Regex("<[^>]+>"), "").trim()

    private fun shareBlock() {
        val d = blockDetail ?: return
        val url = "https://www.are.na${d.href ?: "/block/${d.id}"}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, d.title ?: "Are.na block")
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(send, "Share"))
    }

    private fun downloadThis() {
        val d = blockDetail ?: return
        lifecycleScope.launch {
            try {
                val url = d.downloadUrl()
                if (url.isNullOrBlank()) {
                    toast(this@BlockActivity, "Nothing to download")
                    return@launch
                }
                val root = DownloadManager.rootDir(this@BlockActivity)
                val parent = File(root, "_single")
                parent.mkdirs()
                val typeFolder = DownloadManager.typeFolder(parent, d.typename ?: "Block")
                typeFolder.mkdirs()
                val fileName = (d.title ?: d.id?.toString() ?: "block").replace(Regex("[^A-Za-z0-9 _\\-.]"), "_").take(80) + "." + d.downloadExtension()
                val dest = File(typeFolder, fileName)
                val rowId = AppDatabase.get().downloads().upsert(
                    com.supercomplex.app.data.DownloadEntity(
                        blockId = d.id ?: 0,
                        sourceType = "Block",
                        sourceSlug = null,
                        blockType = d.typename ?: "Block",
                        title = d.title,
                        originalUrl = url,
                        localPath = dest.absolutePath,
                        fileName = fileName,
                        status = "PENDING"
                    )
                )
                DownloadService.runOne(this@BlockActivity, rowId)
                toast(this@BlockActivity, "Download started", long = true)
            } catch (t: Throwable) {
                toast(this@BlockActivity, "Failed: ${t.message}")
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "block_id"
        fun intent(context: Context, id: Long): Intent =
            Intent(context, BlockActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
