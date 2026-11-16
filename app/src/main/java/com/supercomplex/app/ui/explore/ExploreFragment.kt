package com.supercomplex.app.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.supercomplex.app.R
import com.supercomplex.app.data.ArenaRepository
import com.supercomplex.app.data.BlockDetail
import com.supercomplex.app.data.BlockType
import com.supercomplex.app.data.ChannelDetail
import com.supercomplex.app.data.IdRef
import com.supercomplex.app.databinding.FragmentExploreBinding
import com.supercomplex.app.ui.channel.ChannelActivity
import com.supercomplex.app.ui.block.BlockActivity
import com.supercomplex.app.ui.common.GridAdapter
import com.supercomplex.app.ui.common.GridEntry
import com.supercomplex.app.util.spanForWidth
import com.supercomplex.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tier-1 Explore feed. Paginates ExploreResultsQuery by page+timestamp and
 * lazily hydrates each { id, type } pair in batches to avoid N+1 round trips.
 */
class ExploreFragment : Fragment() {

    private var _b: FragmentExploreBinding? = null
    private val b get() = _b!!
    private val repo = ArenaRepository()
    private lateinit var adapter: GridAdapter

    private val pageSize = 24
    private var currentPage = 1
    private var loading = false
    private var isLastPage = false
    private lateinit var sessionTimestamp: String
    private var seed: Int? = null

    private val filters = listOf(
        Filter("All", listOf("ALL")),
        Filter("Channels", listOf("CHANNEL")),
        Filter("Blocks", listOf("BLOCK")),
        Filter("Images", listOf("IMAGE")),
        Filter("Text", listOf("TEXT")),
        Filter("Links", listOf("LINK")),
        Filter("Embeds", listOf("MEDIA")),
        Filter("Files", listOf("ATTACHMENT"))
    )
    private var selectedFilter = 0
    private var pendingJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentExploreBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = GridAdapter(
            onClickBlock = { block ->
                startActivity(BlockActivity.intent(requireContext(), block.id ?: 0))
            },
            onClickChannel = { channel ->
                startActivity(ChannelActivity.intent(requireContext(), channel.id ?: 0, channel.title))
            }
        )
        val span = spanForWidth(requireContext())
        val lm = GridLayoutManager(requireContext(), span)
        b.grid.layoutManager = lm
        b.grid.adapter = adapter
        b.grid.setHasFixedSize(false)
        b.grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || loading || isLastPage) return
                val total = lm.itemCount
                val last = lm.findLastVisibleItemPosition()
                if (last + 6 >= total) loadNextPage()
            }
        })

        b.swipe.setOnRefreshListener {
            resetAndLoad()
        }

        buildFilterChips()
        resetAndLoad()
    }

    private fun buildFilterChips() {
        b.filterChips.removeAllViews()
        filters.forEachIndexed { idx, f ->
            val chip = layoutInflater.inflate(R.layout.item_chip, b.filterChips, false) as TextView
            chip.text = f.label
            chip.isSelected = (idx == selectedFilter)
            chip.setOnClickListener {
                if (selectedFilter == idx) return@setOnClickListener
                selectedFilter = idx
                for (i in 0 until b.filterChips.childCount) {
                    (b.filterChips.getChildAt(i) as TextView).isSelected = (i == idx)
                }
                resetAndLoad()
            }
            b.filterChips.addView(chip)
        }
    }

    private fun resetAndLoad() {
        pendingJob?.cancel()
        currentPage = 1
        isLastPage = false
        sessionTimestamp = ArenaRepository.newTimestampIso()
        seed = (System.currentTimeMillis() % 100_000_000).toInt()
        adapter.submitList(emptyList())
        loadNextPage()
    }

    private fun loadNextPage() {
        if (loading || isLastPage) return
        loading = true
        b.loader.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        b.empty.visibility = View.GONE
        val filter = filters[selectedFilter]

        pendingJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = repo.explore(
                    facets = filter.facets,
                    page = currentPage,
                    per = pageSize,
                    timestamp = sessionTimestamp,
                    seed = seed
                )
                val refs = res.results.orEmpty()
                if (refs.isEmpty()) {
                    isLastPage = true
                    if (adapter.itemCount == 0) b.empty.visibility = View.VISIBLE
                } else {
                    hydrate(refs)
                    currentPage++
                    if (refs.size < pageSize) isLastPage = true
                }
            } catch (t: Throwable) {
                toast(requireContext(), "Failed: ${t.message ?: "network"}")
                if (adapter.itemCount == 0) b.empty.visibility = View.VISIBLE
            } finally {
                loading = false
                b.loader.visibility = View.GONE
                b.swipe.isRefreshing = false
            }
        }
    }

    private suspend fun hydrate(refs: List<IdRef>) = withContext(Dispatchers.IO) {
        val entries = mutableListOf<GridEntry>()
        for (ref in refs) {
            try {
                if (ref.type == "Channel") {
                    repo.channel(ref.id)?.let { entries.add(GridEntry.ChannelItem(it)) }
                } else {
                    repo.block(ref.id)?.let { entries.add(GridEntry.BlockItem(it)) }
                }
            } catch (_: Throwable) {}
        }
        withContext(Dispatchers.Main) {
            val cur = adapter.currentList.toMutableList()
            cur.addAll(entries)
            adapter.submitList(cur)
            b.empty.visibility = if (cur.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private data class Filter(val label: String, val facets: List<String>)
}
