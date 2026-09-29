package com.nice.aceclean.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.nice.aceclean.R
import com.nice.aceclean.ui.base.BaseActivity

abstract class DeviceToolActivity(
    @param:LayoutRes layoutRes: Int,
    @param:StringRes private val titleRes: Int,
) : BaseActivity(layoutRes) {

    final override fun initViews() {
        view<TextView>(R.id.feature_title).setText(titleRes)
        view<android.view.View>(R.id.feature_back).setOnClickListener { finish() }
        initToolViews()
    }

    protected abstract fun initToolViews()

    protected fun addInfoRow(container: ViewGroup, @StringRes labelRes: Int, value: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_device_info_row, container, false)
        row.findViewById<TextView>(R.id.info_label).setText(labelRes)
        row.findViewById<TextView>(R.id.info_value).text = value
        container.addView(row)
    }
}
