package com.nice.aceclean.ui.main

import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.MediaFileInfo
import com.nice.aceclean.util.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BigFileCleanerFragment : BaseFragment(R.layout.fragment_big_file_cleaner) {

    private enum class FileType(val title: String) { ALL("All types"), VIDEO("Videos"), IMAGE("Images"), AUDIO("Audio"), DOCUMENT("Documents") }

    private val selected = linkedSetOf<MediaFileInfo>()
    private var allFiles = emptyList<MediaFileInfo>()
    private var fileType = FileType.ALL
    private var minimumBytes = 10L * 1024L * 1024L
    private var maximumAgeDays = 0
    private lateinit var rootView: View

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        selected.clear()
        loadFiles()
    }

    override fun initViews(root: View) {
        rootView = root
        root.findViewById<View>(R.id.large_back).setOnClickListener { requireActivity().finish() }
        root.findViewById<View>(R.id.large_type_filter).setOnClickListener {
            fileType = FileType.entries[(fileType.ordinal + 1) % FileType.entries.size]
            selected.clear()
            render()
        }
        root.findViewById<View>(R.id.large_size_filter).setOnClickListener {
            minimumBytes = when (minimumBytes) {
                10L * 1024L * 1024L -> 50L * 1024L * 1024L
                50L * 1024L * 1024L -> 100L * 1024L * 1024L
                else -> 10L * 1024L * 1024L
            }
            selected.clear()
            render()
        }
        root.findViewById<View>(R.id.large_time_filter).setOnClickListener {
            maximumAgeDays = when (maximumAgeDays) { 0 -> 30; 30 -> 180; 180 -> 365; else -> 0 }
            selected.clear()
            render()
        }
        root.findViewById<View>(R.id.large_clean).setOnClickListener { deleteSelected() }
        loadFiles()
    }

    private fun loadFiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            allFiles = withContext(Dispatchers.IO) { MediaStoreRepository.largeFiles(requireContext()) }
            selected.clear()
            render()
        }
    }

    private fun filteredFiles(): List<MediaFileInfo> {
        val newestAllowed = if (maximumAgeDays == 0) Long.MIN_VALUE else
            System.currentTimeMillis() / 1000L - maximumAgeDays * 24L * 60L * 60L
        return allFiles.filter { file ->
            file.sizeBytes >= minimumBytes && file.modifiedSeconds >= newestAllowed && when (fileType) {
                FileType.ALL -> true
                FileType.VIDEO -> file.mimeType.startsWith("video/")
                FileType.IMAGE -> file.mimeType.startsWith("image/")
                FileType.AUDIO -> file.mimeType.startsWith("audio/")
                FileType.DOCUMENT -> !file.mimeType.startsWith("video/") && !file.mimeType.startsWith("image/") && !file.mimeType.startsWith("audio/")
            }
        }.sortedByDescending { it.sizeBytes }
    }

    private fun render() {
        if (!::rootView.isInitialized) return
        rootView.findViewById<TextView>(R.id.large_type_filter).text = fileType.title + "  ▾"
        rootView.findViewById<TextView>(R.id.large_size_filter).text = DeviceStats.formatBytes(minimumBytes) + "  ▾"
        rootView.findViewById<TextView>(R.id.large_time_filter).text = when (maximumAgeDays) {
            30 -> "1 month  ▾"
            180 -> "6 months  ▾"
            365 -> "1 year  ▾"
            else -> "All time  ▾"
        }
        val files = filteredFiles()
        val container = rootView.findViewById<LinearLayout>(R.id.large_file_list)
        val empty = rootView.findViewById<TextView>(R.id.large_empty)
        container.removeAllViews()
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        files.forEach { file ->
            val item = LayoutInflater.from(requireContext()).inflate(R.layout.item_big_file, container, false)
            item.findViewById<TextView>(R.id.large_file_name).text = file.name
            item.findViewById<TextView>(R.id.large_file_size).text = DeviceStats.formatBytes(file.sizeBytes)
            val icon = item.findViewById<ImageView>(R.id.large_file_icon)
            icon.setImageResource(fileIcon(file))
            val check = item.findViewById<CheckBox>(R.id.large_file_check)
            check.isChecked = file in selected
            item.setOnClickListener {
                if (file in selected) selected.remove(file) else selected.add(file)
                render()
            }
            container.addView(item)
        }
        rootView.findViewById<TextView>(R.id.large_clean).text = if (selected.isEmpty()) {
            getString(R.string.clean)
        } else {
            getString(R.string.clean_selected, selected.size)
        }
    }

    private fun deleteSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_files_select, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createDeleteRequest(requireContext().contentResolver, selected.map { it.uri })
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            selected.forEach { runCatching { requireContext().contentResolver.delete(it.uri, null, null) } }
            selected.clear()
            loadFiles()
        }
    }

    private fun fileIcon(file: MediaFileInfo): Int {
        val extension = file.name.substringAfterLast('.', "").lowercase()
        return when {
            file.mimeType.startsWith("video/") -> R.drawable.icon_file_type_video
            file.mimeType.startsWith("image/") -> R.drawable.icon_file_type_image
            file.mimeType.startsWith("audio/") -> R.drawable.icon_file_type_audio
            extension == "apk" -> R.drawable.icon_file_type_apk
            extension in setOf("zip", "rar", "7z", "tar", "gz") -> R.drawable.icon_file_type_archives
            extension in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt") -> R.drawable.icon_file_type_document
            else -> R.drawable.icon_file_type_other
        }
    }
}
