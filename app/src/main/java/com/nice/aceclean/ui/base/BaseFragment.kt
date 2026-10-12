package com.nice.aceclean.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseFragment<B : ViewBinding>(
    private val inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> B,
) : Fragment() {

    private var _binding: B? = null
    protected val binding: B get() = checkNotNull(_binding) { "Fragment view is not available" }
    protected val hasViewBinding: Boolean get() = _binding != null

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val viewBinding = inflateBinding(inflater, container, false)
        _binding = viewBinding
        SystemBarInsets.apply(viewBinding.root)
        return viewBinding.root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        initData()
    }

    final override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    protected abstract fun initViews()

    protected open fun initData() = Unit
}
