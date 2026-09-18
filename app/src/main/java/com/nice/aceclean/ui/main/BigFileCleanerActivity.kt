package com.nice.aceclean.ui.main

import android.app.Activity
import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.ui.dialog.IosDeleteConfirmDialog
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.MediaFileInfo
import com.nice.aceclean.util.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BigFileCleanerActivity : StorageCleanerActivity(
    StorageCleanerType.BIG_FILE,
    R.layout.fragment_big_file_cleaner,
) {

    private enum class FileType(@param:StringRes val titleRes: Int) {
        ALL(R.string.all_types), IMAGE(R.string.images), VIDEO(R.string.videos),
        AUDIO(R.string.audio), DOCUMENT(R.string.documents), ARCHIVE(R.string.archives),
        APK(R.string.apk_files), OTHER(R.string.other_files),
    }

    private enum class FilterKind { TYPE, SIZE, TIME }
    private data class SizeOption(@param:StringRes val labelRes: Int, val bytes: Long)
    private data class TimeOption(@param:StringRes val labelRes: Int, val days: Int)

    private val selected = linkedSetOf<MediaFileInfo>()
    private var allFiles = emptyList<MediaFileInfo>()
    private var fileType = FileType.ALL
    private var minimumBytes = 10L * 1024L * 1024L
    private var maximumAgeDays = 0
    private var filterPopup: PopupWindow? = null
    private lateinit var rootView: View
    private lateinit var adapter: BigFileAdapter
    private var deleteConfirmationDialog: Dialog? = null

    private var pendingMediaFiles = emptyList<MediaFileInfo>()
    private var pendingDirectDeletedCount = 0
    private var pendingDirectDeletedBytes = 0L
    private var pendingFailedCount = 0
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        var deletedCount = pendingDirectDeletedCount
        var deletedBytes = pendingDirectDeletedBytes
        if (result.resultCode == Activity.RESULT_OK) {
            deletedCount += pendingMediaFiles.size
            deletedBytes += pendingMediaFiles.sumOf { it.sizeBytes }
        }
        if (deletedCount > 0 || pendingFailedCount > 0) {
            showDeleteResult(deletedCount, deletedBytes, pendingFailedCount)
            selected.clear()
            loadFiles()
        }
        clearPendingDelete()
    }

    override fun initResultViews() {
        rootView = findViewById(android.R.id.content)
        adapter = BigFileAdapter()
        rootView.findViewById<RecyclerView>(R.id.large_file_list).apply {
            layoutManager = LinearLayoutManager(this@BigFileCleanerActivity)
            adapter = this@BigFileCleanerActivity.adapter
        }
        rootView.findViewById<View>(R.id.large_back).setOnClickListener { finish() }
        rootView.findViewById<View>(R.id.large_type_filter).setOnClickListener { showFilterDropdown(FilterKind.TYPE) }
        rootView.findViewById<View>(R.id.large_size_filter).setOnClickListener { showFilterDropdown(FilterKind.SIZE) }
        rootView.findViewById<View>(R.id.large_time_filter).setOnClickListener { showFilterDropdown(FilterKind.TIME) }
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
                FileType.DOCUMENT -> file.extension() in DOCUMENT_EXTENSIONS
                FileType.ARCHIVE -> file.extension() in ARCHIVE_EXTENSIONS
                FileType.APK -> file.extension() == "apk"
                FileType.OTHER -> !file.isKnownType()
            }
        }.sortedByDescending { it.sizeBytes }
    }

    private fun render() {
        if (!::rootView.isInitialized) return
        rootView.findViewById<TextView>(R.id.large_type_filter).text = getString(R.string.filter_value, getString(fileType.titleRes))
        val sizeLabel = sizeOptions.firstOrNull { it.bytes == minimumBytes }?.labelRes ?: R.string.size_10_mb
        rootView.findViewById<TextView>(R.id.large_size_filter).text = getString(R.string.filter_value, getString(sizeLabel))
        val timeLabel = timeOptions.firstOrNull { it.days == maximumAgeDays }?.labelRes ?: R.string.all_time
        rootView.findViewById<TextView>(R.id.large_time_filter).text = getString(R.string.filter_value, getString(timeLabel))
        val files = filteredFiles()
        val empty = rootView.findViewById<TextView>(R.id.large_empty)
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(files)
        updateCleanButton()
    }

    private fun showFilterDropdown(initialKind: FilterKind) {
        filterPopup?.dismiss()
        val content = layoutInflater.inflate(R.layout.popup_big_file_filter, null)
        val host = content.findViewById<View>(R.id.filter_popup_host)
        val panel = content.findViewById<View>(R.id.filter_popup_panel)
        val container = content.findViewById<LinearLayout>(R.id.filter_option_container)
        val popupType = content.findViewById<TextView>(R.id.popup_type_filter)
        val popupSize = content.findViewById<TextView>(R.id.popup_size_filter)
        val popupTime = content.findViewById<TextView>(R.id.popup_time_filter)
        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            animationStyle = 0
        }
        filterPopup = popup

        fun syncHeaderLabels() {
            val sizeLabel = sizeOptions.firstOrNull { it.bytes == minimumBytes }?.labelRes ?: R.string.size_10_mb
            val timeLabel = timeOptions.firstOrNull { it.days == maximumAgeDays }?.labelRes ?: R.string.all_time
            popupType.text = getString(R.string.filter_value, getString(fileType.titleRes))
            popupSize.text = getString(R.string.filter_value, getString(sizeLabel))
            popupTime.text = getString(R.string.filter_value, getString(timeLabel))
        }

        fun setActiveHeader(kind: FilterKind) {
            popupType.setTextColor(filterHeaderColor(kind == FilterKind.TYPE))
            popupSize.setTextColor(filterHeaderColor(kind == FilterKind.SIZE))
            popupTime.setTextColor(filterHeaderColor(kind == FilterKind.TIME))
        }

        fun renderOptions(kind: FilterKind) {
            syncHeaderLabels()
            setActiveHeader(kind)
            container.removeAllViews()
            val options: List<Pair<Int, Boolean>> = when (kind) {
                FilterKind.TYPE -> FileType.entries.map { it.titleRes to (it == fileType) }
                FilterKind.SIZE -> sizeOptions.map { it.labelRes to (it.bytes == minimumBytes) }
                FilterKind.TIME -> timeOptions.map { it.labelRes to (it.days == maximumAgeDays) }
            }
            options.forEachIndexed { index, (labelRes, isSelected) ->
                val row = layoutInflater.inflate(R.layout.item_big_file_filter_option, container, false)
                row.findViewById<TextView>(R.id.filter_option_label).apply {
                    setText(labelRes)
                    setTextColor(filterHeaderColor(isSelected))
                    setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                }
                row.findViewById<ImageView>(R.id.filter_option_check).visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                row.setOnClickListener {
                    if (!isSelected) {
                        when (kind) {
                            FilterKind.TYPE -> fileType = FileType.entries[index]
                            FilterKind.SIZE -> minimumBytes = sizeOptions[index].bytes
                            FilterKind.TIME -> maximumAgeDays = timeOptions[index].days
                        }
                        selected.clear()
                        render()
                    }
                    popup.dismiss()
                }
                container.addView(row)
            }
        }

        popupType.setOnClickListener { renderOptions(FilterKind.TYPE) }
        popupSize.setOnClickListener { renderOptions(FilterKind.SIZE) }
        popupTime.setOnClickListener { renderOptions(FilterKind.TIME) }
        host.setOnClickListener { popup.dismiss() }
        popup.setOnDismissListener { if (filterPopup === popup) filterPopup = null }

        val anchor = rootView.findViewById<View>(R.id.large_filter_bar)
        anchor.post {
            if (isFinishing || isDestroyed) return@post
            renderOptions(initialKind)
            val horizontalMargin = dp(FILTER_HORIZONTAL_MARGIN_DP)
            panel.visibility = View.INVISIBLE
            panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
                width = resources.displayMetrics.widthPixels - horizontalMargin * 2
                leftMargin = horizontalMargin
            }
            panel.elevation = dp(8).toFloat()
            popup.showAtLocation(rootView, Gravity.TOP or Gravity.START, 0, 0)
            dimBehind(popup)
            host.post {
                if (!popup.isShowing) return@post
                val anchorLocation = IntArray(2)
                val hostLocation = IntArray(2)
                anchor.getLocationOnScreen(anchorLocation)
                host.getLocationOnScreen(hostLocation)
                panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
                    topMargin = (anchorLocation[1] - hostLocation[1]).coerceAtLeast(0)
                }
                panel.apply {
                    visibility = View.VISIBLE
                    pivotY = 0f
                    alpha = 0f
                    scaleY = 0.96f
                    animate().alpha(1f).scaleY(1f).setDuration(160L).start()
                }
            }
        }
    }

    private fun filterHeaderColor(active: Boolean): Int = ContextCompat.getColor(
        this,
        if (active) R.color.language_check_bg else R.color.home_text,
    )

    private fun dimBehind(popup: PopupWindow) {
        val container = popup.contentView.rootView
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = container.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        params.dimAmount = FILTER_MASK_DIM_AMOUNT
        runCatching { windowManager.updateViewLayout(container, params) }
    }

    private fun MediaFileInfo.extension() = name.substringAfterLast('.', "").lowercase()

    private fun MediaFileInfo.isKnownType(): Boolean =
        mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/") ||
            extension() in DOCUMENT_EXTENSIONS || extension() in ARCHIVE_EXTENSIONS || extension() == "apk"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun deleteSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.no_files_select, Toast.LENGTH_SHORT).show()
            return
        }
        if (deleteConfirmationDialog?.isShowing == true) return
        val files = selected.toList()
        deleteConfirmationDialog = IosDeleteConfirmDialog.show(
            activity = this,
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
            deleteOnAndroidRAndAbove(files)
        } else {
            val deleted = deleteDirectly(files)
            showDeleteResult(deleted.size, deleted.sumOf { it.sizeBytes }, files.size - deleted.size)
            selected.clear()
            loadFiles()
        }
    }

    private fun deleteOnAndroidRAndAbove(files: List<MediaFileInfo>) {
        val mediaFiles = files.filter { mediaDeleteUriFor(it) != null }
        val mediaDeleteUris = mediaFiles.mapNotNull(::mediaDeleteUriFor)
        val directFiles = files.filter { mediaDeleteUriFor(it) == null }
        val directlyDeleted = deleteDirectly(directFiles)
        val directFailedCount = directFiles.size - directlyDeleted.size

        if (mediaDeleteUris.isEmpty()) {
            showDeleteResult(directlyDeleted.size, directlyDeleted.sumOf { it.sizeBytes }, directFailedCount)
            selected.clear()
            loadFiles()
            return
        }

        runCatching {
            pendingMediaFiles = mediaFiles
            pendingDirectDeletedCount = directlyDeleted.size
            pendingDirectDeletedBytes = directlyDeleted.sumOf { it.sizeBytes }
            pendingFailedCount = directFailedCount
            val request = MediaStore.createDeleteRequest(contentResolver, mediaDeleteUris)
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure {
            clearPendingDelete()
            val fallbackDeleted = deleteDirectly(mediaFiles)
            val allDeleted = directlyDeleted + fallbackDeleted
            showDeleteResult(allDeleted.size, allDeleted.sumOf { it.sizeBytes }, files.size - allDeleted.size)
            selected.clear()
            loadFiles()
        }
    }

    private fun mediaDeleteUriFor(file: MediaFileInfo): Uri? {
        if (file.uri.scheme != "content") return null
        val id = file.uri.lastPathSegment?.toLongOrNull() ?: return null
        val collection = when {
            file.mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            file.mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            file.mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> return null
        }
        return ContentUris.withAppendedId(collection, id)
    }

    private fun deleteDirectly(files: List<MediaFileInfo>): List<MediaFileInfo> = files.filter { file ->
        when (file.uri.scheme) {
            "file" -> runCatching { File(file.uri.path ?: return@runCatching false).delete() }.getOrDefault(false)
            else -> runCatching { contentResolver.delete(file.uri, null, null) > 0 }.getOrDefault(false)
        }
    }

    private fun clearPendingDelete() {
        pendingMediaFiles = emptyList()
        pendingDirectDeletedCount = 0
        pendingDirectDeletedBytes = 0L
        pendingFailedCount = 0
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

    override fun onDestroy() {
        deleteConfirmationDialog?.dismiss()
        deleteConfirmationDialog = null
        filterPopup?.dismiss()
        filterPopup = null
        super.onDestroy()
    }

    private companion object {
        const val FILTER_HORIZONTAL_MARGIN_DP = 19
        const val FILTER_MASK_DIM_AMOUNT = 0.48f
        val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
        val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz")
        val sizeOptions = listOf(
            SizeOption(R.string.size_10_mb, 10L * 1024L * 1024L),
            SizeOption(R.string.size_50_mb, 50L * 1024L * 1024L),
            SizeOption(R.string.size_100_mb, 100L * 1024L * 1024L),
            SizeOption(R.string.size_500_mb, 500L * 1024L * 1024L),
            SizeOption(R.string.size_1_gb, 1024L * 1024L * 1024L),
        )
        val timeOptions = listOf(
            TimeOption(R.string.all_time, 0),
            TimeOption(R.string.one_week, 7),
            TimeOption(R.string.one_month, 30),
            TimeOption(R.string.three_months, 90),
            TimeOption(R.string.six_months, 180),
            TimeOption(R.string.one_year, 365),
        )
    }
}
