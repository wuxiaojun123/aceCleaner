package com.nice.aceclean.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.nice.aceclean.R
import com.nice.aceclean.analytics.setTrackedClickListener
import com.nice.aceclean.databinding.ActivityPhotoViewerBinding
import com.nice.aceclean.databinding.ItemPhotoViewerPageBinding
import com.nice.aceclean.similarphoto.SimilarPhotoCluster
import com.nice.aceclean.similarphoto.SimilarPhotoItem
import com.nice.aceclean.similarphoto.SimilarPhotoRow
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.EventUtils

/** In-app group preview, adapted from cleanboxpro's PhotoViewerActivity. */
class PhotoViewerActivity : BaseActivity<ActivityPhotoViewerBinding>(
    { ActivityPhotoViewerBinding.inflate(it) }, R.layout.activity_photo_viewer,
) {
    private val handoff: FeatureResultViewModel by viewModels()
    private var photos: List<SimilarPhotoItem> = emptyList()
    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) = updateInfo(position)
    }

    override val navigationBarColorRes: Int = R.color.black

    override fun initViews() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        photos = (handoff.read(intent) as? FeatureScanResult.Photos)
            ?.clusters?.firstOrNull()?.items?.map { it.item }.orEmpty()
        if (photos.isEmpty()) {
            finish()
            return
        }
        val startIndex = intent.getIntExtra(EXTRA_START, 0).coerceIn(photos.indices)
        binding.viewerBack.setTrackedClickListener(EventUtils.CLICK_PHOTO_VIEWER_BACK) { finish() }
        binding.viewPager.adapter = PageAdapter()
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.registerOnPageChangeCallback(pageCallback)
        binding.viewPager.setCurrentItem(startIndex, false)
        updateInfo(startIndex)
    }

    private fun updateInfo(position: Int) {
        val photo = photos.getOrNull(position) ?: return
        binding.viewerCounter.text = getString(R.string.string_photo_viewer_counter, position + 1, photos.size)
        val size = Formatter.formatFileSize(this, photo.sizeBytes)
        binding.viewerInfo.text = if (photo.displayName.isBlank()) size else
            getString(R.string.string_photo_viewer_info, photo.displayName, size)
    }

    override fun onDestroy() {
        binding.viewPager.unregisterOnPageChangeCallback(pageCallback)
        binding.viewPager.adapter = null
        super.onDestroy()
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageViewHolder(
            ItemPhotoViewerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

        override fun getItemCount() = photos.size
        override fun onBindViewHolder(holder: PageViewHolder, position: Int) = holder.bind(photos[position])
        override fun onViewRecycled(holder: PageViewHolder) {
            holder.clear()
            super.onViewRecycled(holder)
        }
    }

    private inner class PageViewHolder(private val pageBinding: ItemPhotoViewerPageBinding) : RecyclerView.ViewHolder(pageBinding.root) {
        private val images = Glide.with(this@PhotoViewerActivity)

        fun clear() = images.clear(pageBinding.pageImage)

        fun bind(photo: SimilarPhotoItem) {
            pageBinding.pageImage.setOnSingleTapListener { finish() }
            pageBinding.pageImage.contentDescription = photo.displayName
            pageBinding.pageLoading.visibility = View.VISIBLE
            pageBinding.pageError.visibility = View.GONE
            // Glide decodes to the page size, applies EXIF orientation and cancels recycled requests.
            images
                .load(photo.uri)
                .dontTransform()
                .dontAnimate()
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(error: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        pageBinding.pageLoading.visibility = View.GONE
                        pageBinding.pageError.visibility = View.VISIBLE
                        return false
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        pageBinding.pageLoading.visibility = View.GONE
                        pageBinding.pageError.visibility = View.GONE
                        return false
                    }
                })
                .into(pageBinding.pageImage)
        }
    }

    companion object {
        private const val EXTRA_START = "photo_viewer_start"

        internal fun start(context: Context, photos: List<SimilarPhotoItem>, startIndex: Int) {
            if (photos.isEmpty()) return
            // Keep large groups out of Binder, as in the scan-result handoff.
            val payload = FeatureScanResult.Photos(listOf(SimilarPhotoCluster(0, photos.map { SimilarPhotoRow.Photo(it) })))
            val intent = Intent(context, PhotoViewerActivity::class.java)
                .putExtra(ScanFeature.EXTRA_RESULT, FeatureScanResults.put(payload))
                .putExtra(EXTRA_START, startIndex)
            context.startActivity(intent)
        }
    }
}
