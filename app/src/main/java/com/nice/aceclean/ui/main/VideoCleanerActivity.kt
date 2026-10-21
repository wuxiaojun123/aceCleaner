package com.nice.aceclean.ui.main

import android.app.Activity
import android.app.Dialog
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.FragmentScreenshotCleanerBinding
import com.nice.aceclean.databinding.ItemScreenshotBinding
import com.nice.aceclean.databinding.ItemScreenshotGroupHeaderBinding
import com.nice.aceclean.ui.dialog.IosDeleteConfirmDialog
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.EventUtils
import com.nice.aceclean.util.MediaFileInfo
import com.nice.aceclean.util.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoCleanerActivity : StorageCleanerActivity<FragmentScreenshotCleanerBinding>(
    StorageCleanerType.VIDEO,
    { FragmentScreenshotCleanerBinding.inflate(it) },
    R.layout.fragment_screenshot_cleaner,
) {

    private sealed interface VideoRow {
        data class Header(val month: String, val files: List<MediaFileInfo>) : VideoRow
        data class Video(val file: MediaFileInfo) : VideoRow
    }

    private val selected = linkedSetOf<MediaFileInfo>()
    private var videos = emptyList<MediaFileInfo>()
    private lateinit var adapter: VideoAdapter
    private var deleteConfirmationDialog: Dialog? = null

    private var pendingDeleteCount = 0
    private var pendingDeleteBytes = 0L
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (pendingDeleteCount > 0) {
            EventUtils.track(if (result.resultCode == Activity.RESULT_OK) {
                EventUtils.CLICK_VIDEOS_CONFIRM_DELETE
            } else EventUtils.CLICK_VIDEOS_CANCEL_DELETE)
        }
        if (result.resultCode == Activity.RESULT_OK) {
            showDeleteResult(pendingDeleteCount, pendingDeleteBytes, 0)
            selected.clear()
            loadVideos()
        }
        pendingDeleteCount = 0
        pendingDeleteBytes = 0L
    }

    override fun initResultViews() {
        resultBinding.pictureTitle.setText(R.string.video_manage)
        resultBinding.pictureTabs.visibility = View.GONE
        resultBinding.pictureBack.setTrackedClickListener(EventUtils.CLICK_VIDEOS_BACK) { finishWithBackInterstitial() }
        adapter = VideoAdapter()
        val manager = GridLayoutManager(this, COLUMN_COUNT).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.rowAt(position) is VideoRow.Header) COLUMN_COUNT else 1
            }
        }
        resultBinding.pictureList.apply {
            layoutManager = manager
            adapter = this@VideoCleanerActivity.adapter
        }
        resultBinding.pictureSelectAll.setTrackedClickListener(EventUtils.CLICK_VIDEOS_SELECT_ALL) {
            if ((it as CheckBox).isChecked) selected.addAll(videos) else selected.clear()
            render()
        }
        resultBinding.pictureClean.setTrackedClickListener(EventUtils.CLICK_VIDEOS_CLEAN) { deleteSelected() }
        videos = (initialScanResult as FeatureScanResult.Files).files
        render()
    }

    private fun loadVideos() {
        lifecycleScope.launch {
            videos = withContext(Dispatchers.IO) { MediaStoreRepository.videos(this@VideoCleanerActivity) }
            updateScanResult(FeatureScanResult.Files(ScanFeature.VIDEO, videos))
            selected.clear()
            render()
        }
    }

    private fun render() {
        if (!isResultShown) return
        resultBinding.pictureSelectAll.isChecked = videos.isNotEmpty() && selected.containsAll(videos)
        resultBinding.pictureEmpty.apply {
            setText(R.string.no_video_files_found)
            visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
        }
        adapter.submit(buildList {
            videos.groupBy { it.monthLabel(this@VideoCleanerActivity) }.forEach { (month, files) ->
                add(VideoRow.Header(month, files))
                files.forEach { add(VideoRow.Video(it)) }
            }
        })
        resultBinding.pictureClean.text = if (selected.isEmpty()) {
            getString(R.string.clean)
        } else {
            getString(R.string.clean_selected, selected.size)
        }
    }

    private fun deleteSelected() {
        if (pendingDeleteCount > 0 || deleteConfirmationDialog?.isShowing == true) return
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.no_files_select, Toast.LENGTH_SHORT).show()
            return
        }
        val files = selected.toList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performDelete(files)
            return
        }
        deleteConfirmationDialog = IosDeleteConfirmDialog.show(
            activity = this,
            confirmEventName = EventUtils.CLICK_VIDEOS_CONFIRM_DELETE,
            cancelEventName = EventUtils.CLICK_VIDEOS_CANCEL_DELETE,
            title = getString(R.string.delete_files_title),
            message = getString(
                R.string.delete_files_message,
                files.size,
                DeviceStats.formatBytes(this, files.sumOf { it.sizeBytes }),
            ),
            onConfirm = { performDelete(files) },
            onDismiss = { deleteConfirmationDialog = null },
        )
    }

    private fun performDelete(files: List<MediaFileInfo>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingDeleteCount = files.size
            pendingDeleteBytes = files.sumOf { it.sizeBytes }
            runCatching {
                val request = MediaStore.createDeleteRequest(contentResolver, files.map { it.uri })
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure {
                pendingDeleteCount = 0
                pendingDeleteBytes = 0L
                Toast.makeText(this, R.string.dup_delete_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            val deleted = files.filter { runCatching { contentResolver.delete(it.uri, null, null) > 0 }.getOrDefault(false) }
            showDeleteResult(deleted.size, deleted.sumOf { it.sizeBytes }, files.size - deleted.size)
            selected.clear()
            loadVideos()
        }
    }

    private fun showDeleteResult(deleted: Int, bytes: Long, failed: Int) {
        DeleteSuccessActivity.show(
            context = this,
            removedCount = deleted,
            freedBytes = bytes,
            featureIcon = R.drawable.icon_video_cleaner,
            source = DeleteSuccessActivity.SOURCE_VIDEO,
        )
    }

    override fun onDestroy() {
        deleteConfirmationDialog?.dismiss()
        deleteConfirmationDialog = null
        super.onDestroy()
    }

    private inner class VideoAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows = emptyList<VideoRow>()
        private val thumbnailCache = object : LruCache<String, Bitmap>(CACHE_KB) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        }

        fun submit(newRows: List<VideoRow>) {
            rows = newRows
            notifyDataSetChanged()
        }

        fun rowAt(position: Int) = rows[position]
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is VideoRow.Header) TYPE_HEADER else TYPE_VIDEO

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderHolder(ItemScreenshotGroupHeaderBinding.inflate(inflater, parent, false))
            } else {
                VideoHolder(ItemScreenshotBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is VideoRow.Header -> (holder as HeaderHolder).bind(row)
                is VideoRow.Video -> (holder as VideoHolder).bind(row.file)
            }
        }

        private inner class HeaderHolder(binding: ItemScreenshotGroupHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
            private val month = binding.pictureGroupMonth
            private val summary = binding.pictureGroupSummary
            private val selectAll = binding.pictureGroupAll

            fun bind(header: VideoRow.Header) {
                month.text = header.month
                summary.text = getString(R.string.files_and_size, header.files.size, DeviceStats.formatBytes(itemView.context, header.files.sumOf { it.sizeBytes }))
                selectAll.setOnClickListener(null)
                selectAll.isChecked = header.files.isNotEmpty() && selected.containsAll(header.files)
                selectAll.setTrackedClickListener(EventUtils.CLICK_VIDEOS_GROUP_SELECT) {
                    if (selectAll.isChecked) selected.addAll(header.files) else selected.removeAll(header.files.toSet())
                    render()
                }
            }
        }

        private inner class VideoHolder(binding: ItemScreenshotBinding) : RecyclerView.ViewHolder(binding.root) {
            private val thumbnail = binding.pictureThumbnail
            private val size = binding.pictureSize
            private val check = binding.pictureCheck

            fun bind(file: MediaFileInfo) {
                val key = file.uri.toString()
                thumbnail.tag = key
                thumbnail.setImageResource(R.drawable.icon_file_type_video)
                thumbnailCache.get(key)?.let(thumbnail::setImageBitmap) ?: loadThumbnail(file, key)
                size.text = DeviceStats.formatBytes(itemView.context, file.sizeBytes)
                check.isChecked = file in selected
                itemView.setTrackedClickListener(EventUtils.CLICK_VIDEOS_SELECT) {
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
                                MediaStore.Video.Thumbnails.getThumbnail(
                                    contentResolver,
                                    file.uri.lastPathSegment?.toLongOrNull() ?: return@runCatching null,
                                    MediaStore.Video.Thumbnails.MINI_KIND,
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
        const val TYPE_VIDEO = 1
        const val CACHE_KB = 24 * 1024
    }
}
