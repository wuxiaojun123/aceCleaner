package com.nice.aceclean.ui.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.similarphoto.SimilarPhotoCluster
import com.nice.aceclean.similarphoto.SimilarPhotoItem
import com.nice.aceclean.similarphoto.SimilarPhotoRow
import com.nice.aceclean.similarphoto.SimilarPhotoScanner
import com.nice.aceclean.similarphoto.SimilarPhotosAdapter
import com.nice.aceclean.ui.base.BaseActivity
import kotlinx.coroutines.launch

class DuplicatePhotoCleanerActivity : BaseActivity(R.layout.activity_duplicate_photo_cleaner) {
    private lateinit var adapter: SimilarPhotosAdapter
    private lateinit var gridLayoutManager: GridLayoutManager
    private lateinit var keepBestSwitch: SwitchCompat
    private var syncingSwitch = false
    private var pendingDeleteItems = emptyList<SimilarPhotoItem>()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPhotoAccess()) loadPhotos() else showPermissionEmptyState()
    }

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onPhotosDeleted(pendingDeleteItems)
        pendingDeleteItems = emptyList()
    }

    override fun initViews() {
        view<View>(R.id.duplicate_back).setOnClickListener { finish() }
        keepBestSwitch = view(R.id.duplicate_keep_best_switch)
        keepBestSwitch.isChecked = true

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
        view<RecyclerView>(R.id.duplicate_photo_list).apply {
            layoutManager = gridLayoutManager
            adapter = this@DuplicatePhotoCleanerActivity.adapter
            addItemDecoration(GridSpacingDecoration(resources.getDimensionPixelSize(R.dimen.dup_grid_spacing)))
        }
        keepBestSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!syncingSwitch) adapter.setKeepBestSelection(enabled)
        }
        view<View>(R.id.duplicate_delete).setOnClickListener { confirmDelete() }
    }

    override fun initData() {
        if (hasPhotoAccess()) loadPhotos() else requestPhotoAccess()
    }

    private fun requestPhotoAccess() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    private fun hasPhotoAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        else -> ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadPhotos() {
        showLoading(true)
        lifecycleScope.launch {
            val clusters = runCatching { SimilarPhotoScanner.scan(this@DuplicatePhotoCleanerActivity) }
                .onFailure { Toast.makeText(this@DuplicatePhotoCleanerActivity, R.string.dup_scan_failed, Toast.LENGTH_SHORT).show() }
                .getOrDefault(emptyList())
            val rows = buildRows(clusters)
            adapter.submit(rows)
            showLoading(false)
            showEmpty(rows.isEmpty())
            keepBestSwitch.isEnabled = rows.isNotEmpty()
            keepBestSwitch.alpha = if (rows.isEmpty()) 0.5f else 1f
            updateSelection(adapter.selectedItems())
        }
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
        view<TextView>(R.id.duplicate_selected_count).text = getString(R.string.dup_selected_count, count)
        view<TextView>(R.id.duplicate_selected_size).text = if (count == 0) {
            getString(R.string.dup_action_note)
        } else {
            getString(R.string.dup_selected_size, Formatter.formatFileSize(this, selected.sumOf { it.sizeBytes }))
        }
        view<View>(R.id.duplicate_delete).apply {
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
        AlertDialog.Builder(this)
            .setTitle(R.string.dup_delete_confirm_title)
            .setMessage(getString(R.string.dup_delete_confirm_message, selected.size, Formatter.formatFileSize(this, selected.sumOf { it.sizeBytes })))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dup_delete) { _, _ -> delete(selected) }
            .show()
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
        Toast.makeText(this, getString(R.string.dup_deleted, items.size), Toast.LENGTH_SHORT).show()
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
        view<View>(R.id.duplicate_loading).visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmpty(show: Boolean) {
        view<View>(R.id.duplicate_empty).visibility = if (show) View.VISIBLE else View.GONE
        view<View>(R.id.duplicate_photo_list).visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showPermissionEmptyState() {
        showLoading(false)
        showEmpty(true)
        keepBestSwitch.isEnabled = false
        keepBestSwitch.alpha = 0.5f
        view<TextView>(R.id.duplicate_empty_description).setText(R.string.storage_permission_required)
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

    private companion object { const val COLUMN_COUNT = 3 }
}
