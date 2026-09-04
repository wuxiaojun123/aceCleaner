package com.nice.aceclean.ui.main

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
import com.nice.aceclean.ui.base.BaseFragment
import com.nice.aceclean.util.DeviceStats
import com.nice.aceclean.util.MediaFileInfo
import com.nice.aceclean.util.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoCleanerFragment : BaseFragment(R.layout.fragment_screenshot_cleaner) {

    private sealed interface VideoRow {
        data class Header(val month: String, val files: List<MediaFileInfo>) : VideoRow
        data class Video(val file: MediaFileInfo) : VideoRow
    }

    private val selected = linkedSetOf<MediaFileInfo>()
    private var videos = emptyList<MediaFileInfo>()
    private lateinit var rootView: View
    private lateinit var adapter: VideoAdapter

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        selected.clear()
        loadVideos()
    }

    override fun initViews(root: View) {
        rootView = root
        root.findViewById<TextView>(R.id.picture_title).setText(R.string.video_manage)
        root.findViewById<View>(R.id.picture_tabs).visibility = View.GONE
        root.findViewById<View>(R.id.picture_back).setOnClickListener { requireActivity().finish() }
        adapter = VideoAdapter()
        val manager = GridLayoutManager(requireContext(), COLUMN_COUNT).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.rowAt(position) is VideoRow.Header) COLUMN_COUNT else 1
            }
        }
        root.findViewById<RecyclerView>(R.id.picture_list).apply {
            layoutManager = manager
            adapter = this@VideoCleanerFragment.adapter
        }
        root.findViewById<CheckBox>(R.id.picture_select_all).setOnClickListener {
            if ((it as CheckBox).isChecked) selected.addAll(videos) else selected.clear()
            render()
        }
        root.findViewById<View>(R.id.picture_clean).setOnClickListener { deleteSelected() }
        loadVideos()
    }

    private fun loadVideos() {
        viewLifecycleOwner.lifecycleScope.launch {
            videos = withContext(Dispatchers.IO) { MediaStoreRepository.videos(requireContext()) }
            selected.clear()
            render()
        }
    }

    private fun render() {
        if (!::rootView.isInitialized) return
        rootView.findViewById<CheckBox>(R.id.picture_select_all).isChecked = videos.isNotEmpty() && selected.containsAll(videos)
        rootView.findViewById<TextView>(R.id.picture_empty).apply {
            setText(R.string.no_video_files_found)
            visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
        }
        adapter.submit(buildList {
            videos.groupBy { it.monthLabel() }.forEach { (month, files) ->
                add(VideoRow.Header(month, files))
                files.forEach { add(VideoRow.Video(it)) }
            }
        })
        rootView.findViewById<TextView>(R.id.picture_clean).text = if (selected.isEmpty()) {
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
            loadVideos()
        }
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
            val layout = if (viewType == TYPE_HEADER) R.layout.item_screenshot_group_header else R.layout.item_screenshot
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return if (viewType == TYPE_HEADER) HeaderHolder(view) else VideoHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is VideoRow.Header -> (holder as HeaderHolder).bind(row)
                is VideoRow.Video -> (holder as VideoHolder).bind(row.file)
            }
        }

        private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val month = view.findViewById<TextView>(R.id.picture_group_month)
            private val summary = view.findViewById<TextView>(R.id.picture_group_summary)
            private val selectAll = view.findViewById<CheckBox>(R.id.picture_group_all)

            fun bind(header: VideoRow.Header) {
                month.text = header.month
                summary.text = getString(R.string.files_and_size, header.files.size, DeviceStats.formatBytes(header.files.sumOf { it.sizeBytes }))
                selectAll.setOnClickListener(null)
                selectAll.isChecked = header.files.isNotEmpty() && selected.containsAll(header.files)
                selectAll.setOnClickListener {
                    if (selectAll.isChecked) selected.addAll(header.files) else selected.removeAll(header.files.toSet())
                    render()
                }
            }
        }

        private inner class VideoHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val thumbnail = view.findViewById<ImageView>(R.id.picture_thumbnail)
            private val size = view.findViewById<TextView>(R.id.picture_size)
            private val check = view.findViewById<CheckBox>(R.id.picture_check)

            fun bind(file: MediaFileInfo) {
                val key = file.uri.toString()
                thumbnail.tag = key
                thumbnail.setImageResource(R.drawable.icon_file_type_video)
                thumbnailCache.get(key)?.let(thumbnail::setImageBitmap) ?: loadThumbnail(file, key)
                size.text = DeviceStats.formatBytes(file.sizeBytes)
                check.isChecked = file in selected
                itemView.setOnClickListener {
                    if (file in selected) selected.remove(file) else selected.add(file)
                    render()
                }
            }

            private fun loadThumbnail(file: MediaFileInfo, key: String) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                requireContext().contentResolver.loadThumbnail(file.uri, Size(240, 240), null)
                            } else {
                                MediaStore.Video.Thumbnails.getThumbnail(
                                    requireContext().contentResolver,
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
