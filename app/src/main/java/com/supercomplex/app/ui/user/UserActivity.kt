package com.supercomplex.app.ui.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.supercomplex.app.data.ArenaRepository
import com.supercomplex.app.data.ChannelDetail
import com.supercomplex.app.databinding.ActivityUserBinding
import com.supercomplex.app.download.BulkDownloader
import com.supercomplex.app.ui.channel.ChannelActivity
import com.supercomplex.app.ui.common.GridAdapter
import com.supercomplex.app.ui.common.GridEntry
import com.supercomplex.app.util.openUrl
import com.supercomplex.app.util.spanForWidth
import com.supercomplex.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserActivity : AppCompatActivity() {

    private lateinit var b: ActivityUserBinding
    private val repo = ArenaRepository()
    private lateinit var adapter: GridAdapter
    private var userSlug: String? = null
    private var loading = false
    private var currentPage = 1
    private var isLastPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityUserBinding.inflate(layoutInflater)
        setContentView(b.root)

        userSlug = intent.getStringExtra(EXTRA_SLUG)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: userSlug ?: "User"
        b.userTitle.text = title

        b.btnBack.setOnClickListener { finish() }
        b.btnDownload.setOnClickListener {
            val slug = userSlug
            if (slug.isNullOrBlank()) {
                toast(this, "No slug")
                return@setOnClickListener
            }
            toast(this, "Bulk download started: $slug", long = true)
            lifecycleScope.launch {
                BulkDownloader.get(this@UserActivity).downloadUser(slug)
            }
        }

        adapter = GridAdapter(
            onClickBlock = {},
            onClickChannel = { ch ->
                val slug = ch.href?.trimStart('/')
                startActivity(ChannelActivity.intent(this, ch.id ?: 0, ch.title, slug))
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
        val slug = userSlug ?: run {
            toast(this, "No user slug"); return
        }
        b.loader.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val (channels, total) = withContext(Dispatchers.IO) { repo.restUserChannels(slug, 1, 30) }
                val entries = channels.map { it.toGrid() }
                adapter.submitList(entries)
                currentPage = 2
                if (channels.size < 30) isLastPage = true
                if (total > 0) b.userTitle.text = "$slug · $total channels"
            } catch (t: Throwable) {
                toast(this@UserActivity, "Failed: ${t.message}")
            } finally {
                b.loader.visibility = android.view.View.GONE
            }
        }
    }

    private fun loadNext() {
        val slug = userSlug ?: return
        if (loading || isLastPage) return
        loading = true
        b.loader.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val (channels, _) = withContext(Dispatchers.IO) { repo.restUserChannels(slug, currentPage, 30) }
                val entries = channels.map { it.toGrid() }
                val cur = adapter.currentList.toMutableList(); cur.addAll(entries); adapter.submitList(cur)
                currentPage++
                if (channels.size < 30) isLastPage = true
            } catch (_: Throwable) {}
            finally {
                loading = false
                b.loader.visibility = android.view.View.GONE
            }
        }
    }

    private fun com.supercomplex.app.data.RestChannel.toGrid(): GridEntry.ChannelItem = GridEntry.ChannelItem(
        ChannelDetail(
            id = id,
            title = title,
            href = slug?.let { "/$it" },
            visibility = visibility,
            owner = user,
            counts = counts
        )
    )

    companion object {
        private const val EXTRA_SLUG = "user_slug"
        private const val EXTRA_TITLE = "user_title"
        fun intent(context: Context, slug: String, title: String? = null): Intent =
            Intent(context, UserActivity::class.java)
                .putExtra(EXTRA_SLUG, slug)
                .putExtra(EXTRA_TITLE, title)
    }
}
