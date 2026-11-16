package com.supercomplex.app.ui.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.supercomplex.app.data.DownloadEntity
import com.supercomplex.app.databinding.ItemDownloadBinding

class DownloadListAdapter(
    private val onClick: (DownloadEntity) -> Unit,
    private val onDelete: (DownloadEntity) -> Unit
) : ListAdapter<DownloadEntity, DownloadListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    fun submit(list: List<DownloadEntity>) {
        submitList(list.toList())
    }

    inner class VH(private val b: ItemDownloadBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: DownloadEntity) {
            b.dlTitle.text = e.title?.takeIf { it.isNotBlank() } ?: e.fileName
            val source = listOfNotNull(e.sourceType, e.sourceSlug, e.blockType).joinToString(" · ")
            b.dlSubtitle.text = source
            val pct = if (e.totalBytes > 0) (e.downloadedBytes * 100 / e.totalBytes).toInt() else 0
            b.dlProgress.progress = if (e.status == "DONE") 100 else pct
            val mb = String.format("%.2f / %.2f MB", e.downloadedBytes / 1048576.0, e.totalBytes / 1048576.0)
            b.dlStatus.text = when (e.status) {
                "DONE" -> "Done · ${mb} · ${e.fileName}"
                "RUNNING" -> "Downloading · $mb"
                "PAUSED" -> "Paused · $mb"
                "FAILED" -> "Failed · ${e.error ?: ""}"
                else -> "Queued · $mb"
            }
            b.root.setOnClickListener { onClick(e) }
            b.root.setOnLongClickListener {
                onDelete(e)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DownloadEntity>() {
            override fun areItemsTheSame(o: DownloadEntity, n: DownloadEntity) = o.rowId == n.rowId
            override fun areContentsTheSame(o: DownloadEntity, n: DownloadEntity) = o == n
        }
    }
}
