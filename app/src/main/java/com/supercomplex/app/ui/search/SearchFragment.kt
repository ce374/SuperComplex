package com.supercomplex.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.supercomplex.app.R
import com.supercomplex.app.data.ArenaRepository
import com.supercomplex.app.data.BlockDetail
import com.supercomplex.app.data.ChannelDetail
import com.supercomplex.app.data.RestBlock
import com.supercomplex.app.data.RestChannel
import com.supercomplex.app.databinding.FragmentSearchBinding
import com.supercomplex.app.ui.block.BlockActivity
import com.supercomplex.app.ui.channel.ChannelActivity
import com.supercomplex.app.ui.common.GridAdapter
import com.supercomplex.app.ui.common.GridEntry
import com.supercomplex.app.util.spanForWidth
import com.supercomplex.app.util.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Unauthenticated search via the legacy REST v2 endpoint
 * https://api.are.na/v2/search?q=...
 */
class SearchFragment : Fragment() {

    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!
    private val repo = ArenaRepository()
    private lateinit var adapter: GridAdapter

    private var searchJob: Job? = null
    private var lastQuery: String = ""

    private val typeFilters = listOf("Everything", "Blocks", "Channels")
    private var selectedType = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSearchBinding.inflate(inflater, container, false)
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
        b.grid.layoutManager = GridLayoutManager(requireContext(), span)
        b.grid.adapter = adapter

        buildTypeChips()

        b.queryInput.doAfterTextChanged { s ->
            val q = s?.toString().orEmpty().trim()
            b.clearBtn.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
            searchJob?.cancel()
            if (q.isEmpty()) {
                lastQuery = ""
                adapter.submitList(emptyList())
                b.empty.visibility = View.VISIBLE
                return@doAfterTextChanged
            }
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(350)
                performSearch(q)
            }
        }

        b.clearBtn.setOnClickListener {
            b.queryInput.text = null
        }
    }

    private fun buildTypeChips() {
        b.typeChips.removeAllViews()
        typeFilters.forEachIndexed { idx, label ->
            val chip = layoutInflater.inflate(R.layout.item_chip, b.typeChips, false) as TextView
            chip.text = label
            chip.isSelected = (idx == selectedType)
            chip.setOnClickListener {
                if (selectedType == idx) return@setOnClickListener
                selectedType = idx
                for (i in 0 until b.typeChips.childCount) {
                    (b.typeChips.getChildAt(i) as TextView).isSelected = (i == idx)
                }
                if (lastQuery.isNotEmpty()) performSearch(lastQuery)
            }
            b.typeChips.addView(chip)
        }
    }

    private fun performSearch(query: String) {
        lastQuery = query
        b.loader.visibility = View.VISIBLE
        b.empty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = repo.search(query, page = 1, per = 40)
                val entries = mutableListOf<GridEntry>()
                if (selectedType == 0 || selectedType == 2) {
                    res.channels.orEmpty().forEach { c -> entries.add(c.toGridChannel()) }
                }
                if (selectedType == 0 || selectedType == 1) {
                    res.blocks.orEmpty().forEach { blk -> entries.add(blk.toGridBlock()) }
                }
                adapter.submitList(entries)
                b.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            } catch (t: Throwable) {
                toast(requireContext(), "Search failed: ${t.message ?: "error"}")
            } finally {
                b.loader.visibility = View.GONE
            }
        }
    }

    private fun RestChannel.toGridChannel(): GridEntry.ChannelItem = GridEntry.ChannelItem(
        ChannelDetail(
            id = id,
            title = title,
            href = slug?.let { "/$slug" },
            visibility = visibility,
            owner = user,
            counts = counts
        )
    )

    private fun RestBlock.toGridBlock(): GridEntry.BlockItem = GridEntry.BlockItem(
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

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
