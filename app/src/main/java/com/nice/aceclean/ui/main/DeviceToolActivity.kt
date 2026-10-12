package com.nice.aceclean.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.viewbinding.ViewBinding
import com.nice.aceclean.R
import com.nice.aceclean.databinding.ItemDeviceInfoRowBinding
import com.nice.aceclean.databinding.LayoutFeatureHeaderBinding
import com.nice.aceclean.ui.base.BaseActivity

abstract class DeviceToolActivity<B : ViewBinding>(
    inflateBinding: (LayoutInflater) -> B,
    @param:LayoutRes layoutRes: Int,
    @param:StringRes private val titleRes: Int,
) : BaseActivity<B>(inflateBinding, layoutRes) {

    final override fun initViews() {
        featureHeader.featureTitle.setText(titleRes)
        featureHeader.featureBack.setOnClickListener { finish() }
        initToolViews()
    }

    protected abstract val featureHeader: LayoutFeatureHeaderBinding

    protected abstract fun initToolViews()

    protected fun addInfoRow(container: ViewGroup, @StringRes labelRes: Int, value: String) {
        val row = ItemDeviceInfoRowBinding.inflate(layoutInflater, container, false)
        row.infoLabel.setText(labelRes)
        row.infoValue.text = value
        container.addView(row.root)
    }
}
