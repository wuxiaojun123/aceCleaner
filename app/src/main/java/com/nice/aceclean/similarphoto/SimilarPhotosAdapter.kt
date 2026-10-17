package com.nice.aceclean.similarphoto

import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ItemSimilarPhotoBinding
import com.nice.aceclean.databinding.ItemSimilarPhotoSectionBinding
import com.nice.aceclean.util.EventUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimilarPhotosAdapter(
    private val scope: CoroutineScope,
    private val onSelectionChanged: (List<SimilarPhotoItem>) -> Unit,
    private val onPhotoOpen: (SimilarPhotoItem) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows = emptyList<SimilarPhotoRow>()
    private val thumbnailCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    init { setHasStableIds(true) }

    fun submit(newRows: List<SimilarPhotoRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    fun rowAt(position: Int): SimilarPhotoRow = rows[position]

    fun setKeepBestSelection(enabled: Boolean) {
        rows = rows.map { row ->
            if (row is SimilarPhotoRow.Photo) row.copy(selected = enabled && !row.isBestPhoto) else row
        }
        notifyDataSetChanged()
        notifySelection()
    }

    fun selectedItems(): List<SimilarPhotoItem> = rows.mapNotNull {
        (it as? SimilarPhotoRow.Photo)?.takeIf(SimilarPhotoRow.Photo::selected)?.item
    }

    fun matchesKeepBestSelection(selectedItems: List<SimilarPhotoItem>): Boolean {
        val ids = selectedItems.mapTo(hashSetOf()) { it.id }
        val photos = rows.mapNotNull { it as? SimilarPhotoRow.Photo }
        return photos.isNotEmpty() && photos.all { if (it.isBestPhoto) it.item.id !in ids else it.item.id in ids }
    }

    override fun getItemCount(): Int = rows.size
    override fun getItemViewType(position: Int): Int = if (rows[position] is SimilarPhotoRow.SectionHeader) TYPE_HEADER else TYPE_PHOTO
    override fun getItemId(position: Int): Long = when (val row = rows[position]) {
        is SimilarPhotoRow.SectionHeader -> Long.MIN_VALUE + row.clusterId
        is SimilarPhotoRow.Photo -> row.item.id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemSimilarPhotoSectionBinding.inflate(inflater, parent, false))
        } else {
            PhotoHolder(ItemSimilarPhotoBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is SimilarPhotoRow.SectionHeader -> (holder as HeaderHolder).bind(row)
            is SimilarPhotoRow.Photo -> (holder as PhotoHolder).bind(row)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is PhotoHolder) holder.cancelLoad()
        super.onViewRecycled(holder)
    }

    private inner class HeaderHolder(binding: ItemSimilarPhotoSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        private val title = binding.similarGroupTitle
        private val count = binding.similarGroupCount
        fun bind(row: SimilarPhotoRow.SectionHeader) {
            title.text = itemView.context.getString(R.string.dup_cluster_title, row.clusterId)
            count.text = itemView.context.getString(R.string.dup_cluster_count, row.count)
        }
    }

    private inner class PhotoHolder(binding: ItemSimilarPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        private val thumbnail = binding.similarPhotoThumbnail
        private val size = binding.similarPhotoSize
        private val best = binding.similarPhotoBest
        private val unchecked = binding.similarPhotoUnchecked
        private val checked = binding.similarPhotoChecked
        private val border = binding.similarPhotoBorder
        private var loadJob: Job? = null

        fun bind(row: SimilarPhotoRow.Photo) {
            loadJob?.cancel()
            val key = row.item.uri.toString()
            thumbnail.tag = key
            thumbnail.setImageResource(R.drawable.icon_file_type_image)
            thumbnailCache.get(key)?.let(thumbnail::setImageBitmap) ?: loadThumbnail(row.item, key)
            size.text = Formatter.formatFileSize(itemView.context, row.item.sizeBytes)
            best.visibility = if (row.isBestPhoto) View.VISIBLE else View.GONE
            unchecked.visibility = if (row.selected) View.GONE else View.VISIBLE
            checked.visibility = if (row.selected) View.VISIBLE else View.GONE
            border.visibility = if (row.selected) View.VISIBLE else View.GONE
            unchecked.setTrackedClickListener(EventUtils.CLICK_DUPLICATES_SELECT) { toggle(row.item.id) }
            checked.setTrackedClickListener(EventUtils.CLICK_DUPLICATES_DESELECT) { toggle(row.item.id) }
            thumbnail.setTrackedClickListener(EventUtils.CLICK_DUPLICATES_PREVIEW) { onPhotoOpen(row.item) }
        }

        fun cancelLoad() { loadJob?.cancel() }

        private fun loadThumbnail(item: SimilarPhotoItem, key: String) {
            loadJob = scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        val resolver = itemView.context.contentResolver
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            resolver.loadThumbnail(item.uri, Size(320, 320), null)
                        } else {
                            MediaStore.Images.Thumbnails.getThumbnail(
                                resolver,
                                item.id,
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

    private fun toggle(id: Long) {
        rows = rows.map { row ->
            if (row is SimilarPhotoRow.Photo && row.item.id == id) row.copy(selected = !row.selected) else row
        }
        notifyDataSetChanged()
        notifySelection()
    }

    private fun notifySelection() = onSelectionChanged(selectedItems())

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PHOTO = 1
    }
}
