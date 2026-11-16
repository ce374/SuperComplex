package com.supercomplex.app.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.supercomplex.app.databinding.ItemConnectionBinding

/** A simple 2-line list entry: title + subtitle. */
data class SimpleTextItem(val title: String, val subtitle: String)

/**
 * Minimal 2-line text list adapter used for connections and comments inside
 * the Block detail screen. Intentionally non-Material: just two TextViews
 * in a vertical LinearLayout.
 */
class SimpleTextAdapter(
    private val items: List<SimpleTextItem>,
    private val onClick: (Int) -> Unit
) : ListAdapter<SimpleTextItem, SimpleTextAdapter.VH>(DIFF) {

    init { submitList(items.toList()) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemConnectionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SimpleTextItem) {
            b.connTitle.text = item.title
            b.connMeta.text = item.subtitle
            b.root.setOnClickListener { onClick(bindingAdapterPosition) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SimpleTextItem>() {
            override fun areItemsTheSame(o: SimpleTextItem, n: SimpleTextItem) = o.title == n.title && o.subtitle == n.subtitle
            override fun areContentsTheSame(o: SimpleTextItem, n: SimpleTextItem) = o == n
        }
    }
}
