package com.nice.aceclean.ui.main

import androidx.activity.viewModels
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ActivityDuplicatePhotoCleanerBinding
import com.nice.aceclean.similarphoto.SimilarPhotoCluster
import com.nice.aceclean.similarphoto.SimilarPhotoItem
import com.nice.aceclean.similarphoto.SimilarPhotoRow
import com.nice.aceclean.similarphoto.SimilarPhotoScanner
import com.nice.aceclean.similarphoto.SimilarPhotosAdapter
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.ui.dialog.IosDeleteConfirmDialog
import com.nice.aceclean.ui.widget.IosSwitchView
import com.nice.aceclean.util.EventUtils
import kotlinx.coroutines.launch

class SimilarPhotoCleanerActivity : BaseActivity<ActivityDuplicatePhotoCleanerBinding>({ ActivityDuplicatePhotoCleanerBinding.inflate(it) }, R.layout.activity_duplicate_photo_cleaner) {
    private lateinit var adapter: SimilarPhotosAdapter
    private lateinit var gridLayoutManager: GridLayoutManager
    private lateinit var keepBestSwitch: IosSwitchView
    private var syncingSwitch = false
    private var pendingDeleteItems = emptyList<SimilarPhotoItem>()
    private var deleteConfirmationDialog: Dialog? = null

    private val handoff: FeatureResultViewModel by viewModels()

    private val deleteLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onPhotosDeleted(pendingDeleteItems)
        pendingDeleteItems = emptyList()
    }

    override fun initViews() {
        val result = scanResult(handoff, ScanFeature.DUPLICATE_PHOTO) as? FeatureScanResult.Photos ?: return
        binding.duplicateBack.setTrackedClickListener(EventUtils.CLICK_DUPLICATES_BACK) { finish() }
        keepBestSwitch = binding.duplicateKeepBestSwitch
        keepBestSwitch.isChecked = true
        keepBestSwitch.setTrackOnColor(ContextCompat.getColor(this, R.color.color_FF42B8FF))

        adapter = SimilarPhotosAdapter(
            lifecycleScope,
            onSelectionChanged = ::updateSelection,
            onPhotoOpen = ::openPhoto,
        )
        gridLayoutManager = GridLayoutManager(this, COLUMN_COUNT).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.getItemViewType(position) == SimilarPhotosAdapter.TYPE_HEADER) COLUMN_COUNT else 1
            }
        }
        binding.duplicatePhotoList.apply {
            layoutManager = gridLayoutManager
            adapter = this@SimilarPhotoCleanerActivity.adapter
            addItemDecoration(GridSpacingDecoration(resources.getDimensionPixelSize(R.dimen.dup_grid_spacing)))
        }
        keepBestSwitch.setTrackedClickListener(EventUtils.CLICK_DUPLICATES_KEEP_BEST)
        keepBestSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!syncingSwitch) adapter.setKeepBestSelection(enabled)
        }
        binding.duplicateDelete.setTrackedClickListener(EventUtils.CLICK_DUPLICATES_DELETE) { confirmDelete() }
        renderClusters(result.clusters)
    }

    private fun loadPhotos() {
        showLoading(true)
        lifecycleScope.launch {
            val clusters = runCatching { SimilarPhotoScanner.scan(this@SimilarPhotoCleanerActivity) }
                .onFailure { Toast.makeText(this@SimilarPhotoCleanerActivity, R.string.dup_scan_failed, Toast.LENGTH_SHORT).show() }
                .getOrDefault(emptyList())
            renderClusters(clusters)
        }
    }

    private fun renderClusters(clusters: List<SimilarPhotoCluster>) {
        handoff.update(FeatureScanResult.Photos(clusters))
        val rows = buildRows(clusters)
        adapter.submit(rows)
        showLoading(false)
        showEmpty(rows.isEmpty())
        keepBestSwitch.isEnabled = rows.isNotEmpty()
        keepBestSwitch.alpha = if (rows.isEmpty()) 0.5f else 1f
        updateSelection(adapter.selectedItems())
    }

    private fun buildRows(clusters: List<SimilarPhotoCluster>): List<SimilarPhotoRow> = buildList {
        clusters.forEach { cluster ->
            add(SimilarPhotoRow.SectionHeader(cluster.id, cluster.items.size))
            cluster.items.forEach { photo ->
                add(photo.copy(selected = keepBestSwitch.isChecked && !photo.isBestPhoto))
            }
        }
    }

    private fun updateSelection(selected: List<SimilarPhotoItem>) {
        val count = selected.size
        binding.duplicateSelectedCount.text = getString(R.string.dup_selected_count, count)
        binding.duplicateSelectedSize.text = if (count == 0) {
            getString(R.string.dup_action_note)
        } else {
            getString(R.string.dup_selected_size, Formatter.formatFileSize(this, selected.sumOf { it.sizeBytes }))
        }
        binding.duplicateDelete.apply {
            isEnabled = count > 0
            alpha = if (count > 0) 1f else 0.6f
        }
        val matches = adapter.matchesKeepBestSelection(selected)
        if (keepBestSwitch.isChecked != matches && adapter.itemCount > 0) {
            syncingSwitch = true
            keepBestSwitch.isChecked = matches
            syncingSwitch = false
        }
    }

    private fun confirmDelete() {
        val selected = adapter.selectedItems()
        if (selected.isEmpty()) return
        if (deleteConfirmationDialog?.isShowing == true) return
        deleteConfirmationDialog = IosDeleteConfirmDialog.show(
            activity = this,
            confirmEventName = EventUtils.CLICK_DUPLICATES_CONFIRM_DELETE,
            cancelEventName = EventUtils.CLICK_DUPLICATES_CANCEL_DELETE,
            title = getString(R.string.dup_delete_confirm_title),
            message = getString(
                R.string.dup_delete_confirm_message,
                selected.size,
                Formatter.formatFileSize(this, selected.sumOf { it.sizeBytes }),
            ),
            confirmText = getString(R.string.dup_delete),
            onConfirm = { delete(selected) },
            onDismiss = { deleteConfirmationDialog = null },
        )
    }

    private fun delete(items: List<SimilarPhotoItem>) {
        val uris = items.map { it.uri }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                pendingDeleteItems = items
                val request = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure {
                pendingDeleteItems = emptyList()
                Toast.makeText(this, R.string.dup_delete_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            val deleted = items.filter { runCatching { contentResolver.delete(it.uri, null, null) > 0 }.getOrDefault(false) }
            onPhotosDeleted(deleted)
            if (deleted.size != items.size) Toast.makeText(this, R.string.dup_delete_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPhotosDeleted(items: List<SimilarPhotoItem>) {
        if (items.isEmpty()) return
        DeleteSuccessActivity.show(
            context = this,
            removedCount = items.size,
            freedBytes = items.sumOf { it.sizeBytes },
            featureIcon = R.drawable.icon_duplicate_photo_cleaner,
            source = DeleteSuccessActivity.SOURCE_DUPLICATE,
        )
        SimilarPhotoScanner.invalidateCache()
        loadPhotos()
    }

    private fun openPhoto(item: SimilarPhotoItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.dup_preview_unavailable, Toast.LENGTH_SHORT).show() }
    }

    private fun showLoading(show: Boolean) {
        binding.duplicateLoading.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmpty(show: Boolean) {
        binding.duplicateEmpty.visibility = if (show) View.VISIBLE else View.GONE
        binding.duplicatePhotoList.visibility = if (show) View.GONE else View.VISIBLE
    }

    private inner class GridSpacingDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, child: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) return
            if (adapter.getItemViewType(position) == SimilarPhotosAdapter.TYPE_HEADER) {
                outRect.set(0, if (position == 0) 0 else spacing, 0, spacing / 2)
                return
            }
            val params = child.layoutParams as GridLayoutManager.LayoutParams
            val column = params.spanIndex
            outRect.left = spacing - column * spacing / COLUMN_COUNT
            outRect.right = (column + 1) * spacing / COLUMN_COUNT
            outRect.top = spacing
        }
    }

    override fun onDestroy() {
        deleteConfirmationDialog?.dismiss()
        deleteConfirmationDialog = null
        super.onDestroy()
    }

    private companion object { const val COLUMN_COUNT = 3 }
}
