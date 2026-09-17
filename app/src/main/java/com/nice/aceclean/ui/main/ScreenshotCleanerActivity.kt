package com.nice.aceclean.ui.main

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.MediaFileInfo
import com.nice.aceclean.util.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenshotCleanerActivity : StorageCleanerActivity(
    StorageCleanerType.SCREENSHOT,
    R.layout.fragment_screenshot_cleaner,
) {

    private sealed interface PictureRow {
        data class Header(val month: String, val files: List<MediaFileInfo>) : PictureRow
        data class Picture(val file: MediaFileInfo) : PictureRow
    }

    private val selected = linkedSetOf<MediaFileInfo>()
    private var screenshots = emptyList<MediaFileInfo>()
    private var others = emptyList<MediaFileInfo>()
    private var showingScreenshots = true
    private lateinit var rootView: View
    private lateinit var adapter: PictureAdapter

    private var pendingDeleteCount = 0
    private var pendingDeleteBytes = 0L
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            showDeleteResult(pendingDeleteCount, pendingDeleteBytes, 0)
            selected.clear()
            loadPictures()
        }
        pendingDeleteCount = 0
        pendingDeleteBytes = 0L
    }

    override fun initResultViews() {
        rootView = findViewById(android.R.id.content)
        adapter = PictureAdapter()
        val manager = GridLayoutManager(this, COLUMN_COUNT).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.rowAt(position) is PictureRow.Header) COLUMN_COUNT else 1
            }
        }
        rootView.findViewById<RecyclerView>(R.id.picture_list).apply {
            layoutManager = manager
            adapter = this@ScreenshotCleanerActivity.adapter
        }
        rootView.findViewById<View>(R.id.picture_back).setOnClickListener { finish() }
        rootView.findViewById<View>(R.id.picture_tab_screenshot).setOnClickListener {
            showingScreenshots = true
            selected.clear()
            render()
        }
        rootView.findViewById<View>(R.id.picture_tab_others).setOnClickListener {
            showingScreenshots = false
            selected.clear()
            render()
        }
        rootView.findViewById<CheckBox>(R.id.picture_select_all).setOnClickListener {
            val files = visibleFiles()
            if ((it as CheckBox).isChecked) selected.addAll(files) else selected.clear()
            render()
        }
        rootView.findViewById<View>(R.id.picture_clean).setOnClickListener { deleteSelected() }
        loadPictures()
    }

    private fun loadPictures() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { MediaStoreRepository.pictures(this@ScreenshotCleanerActivity) }
            screenshots = result.first
            others = result.second
            selected.clear()
            render()
        }
    }

    private fun render() {
        if (!::rootView.isInitialized) return
        rootView.findViewById<View>(R.id.picture_tab_screenshot).isSelected = showingScreenshots
        rootView.findViewById<View>(R.id.picture_tab_others).isSelected = !showingScreenshots
        val files = visibleFiles()
        rootView.findViewById<CheckBox>(R.id.picture_select_all).isChecked = files.isNotEmpty() && selected.containsAll(files)
        rootView.findViewById<TextView>(R.id.picture_empty).visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(buildList {
            files.groupBy { it.monthLabel() }.forEach { (month, monthFiles) ->
                add(PictureRow.Header(month, monthFiles))
                monthFiles.forEach { add(PictureRow.Picture(it)) }
            }
        })
        updateCleanButton()
    }

    private fun updateCleanButton() {
        rootView.findViewById<TextView>(R.id.picture_clean).text = if (selected.isEmpty()) {
            getString(R.string.clean)
        } else {
            getString(R.string.clean_selected, selected.size)
        }
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
            loadPictures()
        }
    }

    private fun showDeleteResult(deleted: Int, bytes: Long, failed: Int) {
        Toast.makeText(this, getString(R.string.files_deleted_result, deleted, DeviceStats.formatBytes(this, bytes), failed), Toast.LENGTH_LONG).show()
    }

    private fun visibleFiles() = if (showingScreenshots) screenshots else others

    private inner class PictureAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows = emptyList<PictureRow>()
        private val thumbnailCache = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_KB) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }

        fun submit(newRows: List<PictureRow>) {
            rows = newRows
            notifyDataSetChanged()
        }

        fun rowAt(position: Int): PictureRow = rows[position]

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is PictureRow.Header) TYPE_HEADER else TYPE_PICTURE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layout = if (viewType == TYPE_HEADER) R.layout.item_screenshot_group_header else R.layout.item_screenshot
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return if (viewType == TYPE_HEADER) HeaderHolder(view) else PictureHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is PictureRow.Header -> (holder as HeaderHolder).bind(row)
                is PictureRow.Picture -> (holder as PictureHolder).bind(row.file)
            }
        }

        private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val month = view.findViewById<TextView>(R.id.picture_group_month)
            private val summary = view.findViewById<TextView>(R.id.picture_group_summary)
            private val selectAll = view.findViewById<CheckBox>(R.id.picture_group_all)

            fun bind(header: PictureRow.Header) {
                month.text = header.month
                summary.text = getString(R.string.files_and_size, header.files.size, DeviceStats.formatBytes(itemView.context, header.files.sumOf { it.sizeBytes }))
                selectAll.setOnClickListener(null)
                selectAll.isChecked = header.files.isNotEmpty() && selected.containsAll(header.files)
                selectAll.setOnClickListener {
                    if (selectAll.isChecked) selected.addAll(header.files) else selected.removeAll(header.files.toSet())
                    render()
                }
            }
        }

        private inner class PictureHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val thumbnail = view.findViewById<ImageView>(R.id.picture_thumbnail)
            private val size = view.findViewById<TextView>(R.id.picture_size)
            private val check = view.findViewById<CheckBox>(R.id.picture_check)

            fun bind(file: MediaFileInfo) {
                val key = file.uri.toString()
                thumbnail.tag = key
                thumbnail.setImageResource(R.drawable.icon_file_type_image)
                thumbnailCache.get(key)?.let(thumbnail::setImageBitmap) ?: loadThumbnail(file, key)
                size.text = DeviceStats.formatBytes(itemView.context, file.sizeBytes)
                check.isChecked = file in selected
                itemView.setOnClickListener {
                    if (file in selected) selected.remove(file) else selected.add(file)
                    render()
                }
            }

            private fun loadThumbnail(file: MediaFileInfo, key: String) {
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                contentResolver.loadThumbnail(file.uri, Size(240, 240), null)
                            } else {
                                MediaStore.Images.Thumbnails.getThumbnail(
                                    contentResolver,
                                    file.uri.lastPathSegment?.toLongOrNull() ?: return@runCatching null,
                                    MediaStore.Images.Thumbnails.MINI_KIND,
                                    null,
                                )
                            }
                        }.getOrNull()
                    }
                    if (bitmap != null) {
                        thumbnailCache.put(key, bitmap)
                        if (thumbnail.tag == key) thumbnail.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private companion object {
        const val COLUMN_COUNT = 3
        const val TYPE_HEADER = 0
        const val TYPE_PICTURE = 1
        const val THUMBNAIL_CACHE_KB = 24 * 1024
    }
}
