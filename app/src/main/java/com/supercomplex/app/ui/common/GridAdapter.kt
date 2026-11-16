package com.supercomplex.app.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.supercomplex.app.R
import com.supercomplex.app.data.BlockDetail
import com.supercomplex.app.data.ChannelDetail
import com.supercomplex.app.databinding.ItemBlockGridBinding
import com.supercomplex.app.util.hexSubTypeColor

/**
 * RecyclerView adapter for mixed Block + Channel cells in a grid.
 *
 * Each entry is sealed: either a Block (with hydrated detail) or a
 * Channel. The cell renders the appropriate preview - image, text,
 * channel thumbnail with overlay - and hides everything else, so the
 * grid remains visually quiet.
 */
sealed class GridEntry {
    data class BlockItem(val block: BlockDetail) : GridEntry()
    data class ChannelItem(val channel: ChannelDetail) : GridEntry()
}

class GridAdapter(
    private val onClickBlock: (BlockDetail) -> Unit,
    private val onClickChannel: (ChannelDetail) -> Unit
) : ListAdapter<GridEntry, GridAdapter.VH>(DIFF) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is GridEntry.ChannelItem) TYPE_CHANNEL else TYPE_BLOCK

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemBlockGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemBlockGridBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(entry: GridEntry) {
            when (entry) {
                is GridEntry.BlockItem -> bindBlock(entry.block)
                is GridEntry.ChannelItem -> bindChannel(entry.channel)
            }
        }

        private fun bindBlock(block: BlockDetail) {
            b.channelOverlay.visibility = android.view.View.GONE
            b.channelInfo.visibility = android.view.View.GONE
            b.text.visibility = android.view.View.GONE
            b.typeTint.visibility = android.view.View.GONE
            b.typeIcon.visibility = android.view.View.GONE

            val hexColor = hexSubTypeColor(block)
            if (hexColor != null) {
                b.image.visibility = android.view.View.GONE
                b.typeTint.visibility = android.view.View.VISIBLE
                b.typeTint.setBackgroundColor(hexColor)
                if (!block.rawContent.isNullOrBlank()) {
                    b.text.visibility = android.view.View.VISIBLE
                    b.text.text = block.rawContent
                    b.text.setTextColor(if (isDark(hexColor)) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
                }
            } else if (block.typename == "Text") {
                b.image.visibility = android.view.View.GONE
                b.text.visibility = android.view.View.VISIBLE
                b.text.text = block.rawContent ?: block.truncatedContent ?: block.title ?: ""
            } else {
                val img = block.previewImageUrl()
                if (img.isNullOrBlank()) {
                    b.image.visibility = android.view.View.GONE
                    b.text.visibility = android.view.View.VISIBLE
                    b.text.text = block.title ?: block.fileName ?: ""
                } else {
                    b.image.visibility = android.view.View.VISIBLE
                    b.image.load(img) {
                        crossfade(true)
                        placeholder(R.color.bg_image_ph)
                        error(R.color.bg_image_ph)
                    }
                }
            }
            b.root.setOnClickListener { onClickBlock(block) }
        }

        private fun bindChannel(channel: ChannelDetail) {
            b.text.visibility = android.view.View.GONE
            b.typeIcon.visibility = android.view.View.GONE
            b.channelOverlay.visibility = android.view.View.VISIBLE
            b.channelInfo.visibility = android.view.View.VISIBLE
            b.channelTitle.text = channel.title ?: ""
            b.channelCount.text = "${channel.counts?.contents ?: 0} blocks"
            // No background image - the channel cell is a tinted flat color
            b.image.visibility = android.view.View.GONE
            b.typeTint.visibility = android.view.View.VISIBLE
            b.typeTint.setBackgroundColor(0xFFC04848.toInt())
            b.root.setOnClickListener { onClickChannel(channel) }
        }

        private fun isDark(color: Int): Boolean {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            // perceived luminance
            return (0.299 * r + 0.587 * g + 0.114 * b) < 128
        }
    }

    companion object {
        private const val TYPE_BLOCK = 1
        private const val TYPE_CHANNEL = 2

        private val DIFF = object : DiffUtil.ItemCallback<GridEntry>() {
            override fun areItemsTheSame(oldItem: GridEntry, newItem: GridEntry): Boolean = when {
                oldItem is GridEntry.BlockItem && newItem is GridEntry.BlockItem ->
                    oldItem.block.id == newItem.block.id
                oldItem is GridEntry.ChannelItem && newItem is GridEntry.ChannelItem ->
                    oldItem.channel.id == newItem.channel.id
                else -> false
            }
            override fun areContentsTheSame(oldItem: GridEntry, newItem: GridEntry): Boolean =
                oldItem == newItem
        }
    }
}
