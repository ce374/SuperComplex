package com.supercomplex.app.ui.downloads

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.supercomplex.app.R
import com.supercomplex.app.data.DownloadEntity
import com.supercomplex.app.databinding.FragmentDownloadsBinding
import com.supercomplex.app.download.DownloadTracker
import com.supercomplex.app.util.toast
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class DownloadsFragment : Fragment() {

    private var _b: FragmentDownloadsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: DownloadListAdapter
    private val tracker get() = DownloadTracker.get(requireContext())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentDownloadsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DownloadListAdapter(
            onClick = { entity -> openFile(entity) },
            onDelete = { entity ->
                viewLifecycleOwner.lifecycleScope.launch {
                    tracker.delete(entity.rowId)
                    toast(requireContext(), "Deleted")
                }
            }
        )
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            tracker.all().collect { list ->
                adapter.submit(list)
                b.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            tracker.doneCount().collect { count ->
                b.doneCount.text = "$count done"
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            tracker.totalBytes().collect { bytes ->
                val mb = (bytes ?: 0L) / (1024.0 * 1024.0)
                b.totalBytes.text = String.format("%.1f MB", mb)
            }
        }
        b.btnBackup.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val file = tracker.exportBackup()
                    toast(requireContext(), "Backup saved: ${file.name}", long = true)
                } catch (t: Throwable) {
                    toast(requireContext(), "Backup failed: ${t.message}")
                }
            }
        }
        b.btnRestore.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val root = com.supercomplex.app.download.DownloadManager.rootDir(requireContext())
                    val backupDir = File(root, "_backup")
                    val files = backupDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() }.orEmpty()
                    if (files.isEmpty()) {
                        toast(requireContext(), "No backup found")
                        return@launch
                    }
                    val n = tracker.importBackup(files.first())
                    toast(requireContext(), "Restored $n entries", long = true)
                } catch (t: Throwable) {
                    toast(requireContext(), "Restore failed: ${t.message}")
                }
            }
        }
    }

    private fun openFile(entity: DownloadEntity) {
        val file = File(entity.localPath)
        if (!file.exists()) {
            toast(requireContext(), "File missing")
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, entityMimeType(entity))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Open"))
        } catch (t: Throwable) {
            toast(requireContext(), "Cannot open: ${t.message}")
        }
    }

    private fun entityMimeType(e: DownloadEntity): String {
        val ext = e.fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp" -> "image/*"
            "mp4", "mov", "avi", "mkv" -> "video/*"
            "mp3", "wav", "flac", "aac", "ogg" -> "audio/*"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            "html" -> "text/html"
            else -> "*/*"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
