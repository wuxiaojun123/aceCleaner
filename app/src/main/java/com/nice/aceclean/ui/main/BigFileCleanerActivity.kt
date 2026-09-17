package com.nice.aceclean.ui.main

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.MediaFileInfo
import com.nice.aceclean.util.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BigFileCleanerActivity : StorageCleanerActivity(
    StorageCleanerType.BIG_FILE,
    R.layout.fragment_big_file_cleaner,
) {

    private enum class FileType(@StringRes val titleRes: Int) {
        ALL(R.string.all_types), VIDEO(R.string.videos), IMAGE(R.string.images),
        AUDIO(R.string.audio), DOCUMENT(R.string.documents),
    }

    private val selected = linkedSetOf<MediaFileInfo>()
    private var allFiles = emptyList<MediaFileInfo>()
    private var fileType = FileType.ALL
    private var minimumBytes = 10L * 1024L * 1024L
    private var maximumAgeDays = 0
    private lateinit var rootView: View
    private lateinit var adapter: BigFileAdapter

    private var pendingDeleteCount = 0
    private var pendingDeleteBytes = 0L
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            showDeleteResult(pendingDeleteCount, pendingDeleteBytes, 0)
            selected.clear()
            loadFiles()
        }
        pendingDeleteCount = 0
        pendingDeleteBytes = 0L
    }

    override fun initResultViews() {
        rootView = findViewById(android.R.id.content)
        adapter = BigFileAdapter()
        rootView.findViewById<RecyclerView>(R.id.large_file_list).apply {
            layoutManager = LinearLayoutManager(this@BigFileCleanerActivity)
            adapter = this@BigFileCleanerActivity.adapter
        }
        rootView.findViewById<View>(R.id.large_back).setOnClickListener { finish() }
        rootView.findViewById<View>(R.id.large_type_filter).setOnClickListener {
            fileType = FileType.entries[(fileType.ordinal + 1) % FileType.entries.size]
            selected.clear()
            render()
        }
        rootView.findViewById<View>(R.id.large_size_filter).setOnClickListener {
            minimumBytes = when (minimumBytes) {
                10L * 1024L * 1024L -> 50L * 1024L * 1024L
                50L * 1024L * 1024L -> 100L * 1024L * 1024L
                else -> 10L * 1024L * 1024L
            }
            selected.clear()
            render()
        }
        rootView.findViewById<View>(R.id.large_time_filter).setOnClickListener {
            maximumAgeDays = when (maximumAgeDays) { 0 -> 30; 30 -> 180; 180 -> 365; else -> 0 }
            selected.clear()
            render()
        }
        rootView.findViewById<View>(R.id.large_clean).setOnClickListener { deleteSelected() }
        loadFiles()
    }

    private fun loadFiles() {
        lifecycleScope.launch {
            allFiles = withContext(Dispatchers.IO) { MediaStoreRepository.largeFiles(this@BigFileCleanerActivity) }
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
        rootView.findViewById<TextView>(R.id.large_type_filter).text = getString(R.string.filter_value, getString(fileType.titleRes))
        rootView.findViewById<TextView>(R.id.large_size_filter).text = getString(R.string.filter_value, DeviceStats.formatBytes(this, minimumBytes))
        val timeLabel = when (maximumAgeDays) {
            30 -> R.string.one_month
            180 -> R.string.six_months
            365 -> R.string.one_year
            else -> R.string.all_time
        }
        rootView.findViewById<TextView>(R.id.large_time_filter).text = getString(R.string.filter_value, getString(timeLabel))
        val files = filteredFiles()
        val empty = rootView.findViewById<TextView>(R.id.large_empty)
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(files)
        updateCleanButton()
    }

    private fun deleteSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.no_files_select, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_files_title)
            .setMessage(getString(R.string.delete_files_message, selected.size, DeviceStats.formatBytes(this, selected.sumOf { it.sizeBytes })))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> performDelete() }
            .show()
    }

    private fun performDelete() {
        val files = selected.toList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingDeleteCount = files.size
            pendingDeleteBytes = files.sumOf { it.sizeBytes }
            val request = MediaStore.createDeleteRequest(contentResolver, selected.map { it.uri })
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            val deleted = files.filter { runCatching { contentResolver.delete(it.uri, null, null) > 0 }.getOrDefault(false) }
            showDeleteResult(deleted.size, deleted.sumOf { it.sizeBytes }, files.size - deleted.size)
            selected.clear()
            loadFiles()
        }
    }

    private fun showDeleteResult(deleted: Int, bytes: Long, failed: Int) {
        Toast.makeText(this, getString(R.string.files_deleted_result, deleted, DeviceStats.formatBytes(this, bytes), failed), Toast.LENGTH_LONG).show()
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

    private inner class BigFileAdapter : RecyclerView.Adapter<BigFileHolder>() {
        private var files = emptyList<MediaFileInfo>()

        fun submit(newFiles: List<MediaFileInfo>) {
            files = newFiles
            notifyDataSetChanged()
        }

        override fun getItemCount() = files.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BigFileHolder = BigFileHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_big_file, parent, false),
        )

        override fun onBindViewHolder(holder: BigFileHolder, position: Int) = holder.bind(files[position])
    }

    private inner class BigFileHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(file: MediaFileInfo) {
            itemView.findViewById<TextView>(R.id.large_file_name).text = file.name
            itemView.findViewById<TextView>(R.id.large_file_size).text = DeviceStats.formatBytes(itemView.context, file.sizeBytes)
            itemView.findViewById<ImageView>(R.id.large_file_icon).setImageResource(fileIcon(file))
            itemView.findViewById<CheckBox>(R.id.large_file_check).isChecked = file in selected
            itemView.setOnClickListener {
                if (!selected.add(file)) selected.remove(file)
                bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(adapter::notifyItemChanged)
                updateCleanButton()
            }
        }
    }

    private fun updateCleanButton() {
        rootView.findViewById<TextView>(R.id.large_clean).text = if (selected.isEmpty()) {
            getString(R.string.clean)
        } else {
            getString(R.string.clean_selected, selected.size)
        }
    }
}
