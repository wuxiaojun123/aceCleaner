package com.nice.library_push.business.news

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nice.library_push.api.isHttpsUrl
import com.nice.library_push.databinding.NicePushNewsActivityBinding

/** Default news destination; a host-provided PushRouter can use its own reader instead. */
class PushNewsActivity : ComponentActivity() {
    private lateinit var binding: NicePushNewsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!isHttpsUrl(url)) { finish(); return }
        binding = NicePushNewsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.retry.setOnClickListener { load(url) }
        binding.web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        binding.web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !isHttpsUrl(request.url.toString())

            override fun onPageFinished(view: WebView, url: String?) { binding.progress.visibility = View.GONE }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError()
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) showError()
            }
        }
        binding.web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { binding.progress.progress = newProgress }
            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!title.isNullOrBlank()) binding.title.text = title
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.web.canGoBack()) binding.web.goBack() else finish()
            }
        })
        if (savedInstanceState == null || binding.web.restoreState(savedInstanceState) == null) load(url)
    }

    private fun load(url: String) {
        binding.errorPanel.visibility = View.GONE
        binding.web.visibility = View.VISIBLE
        binding.progress.visibility = View.VISIBLE
        binding.web.loadUrl(url)
    }

    private fun showError() {
        binding.progress.visibility = View.GONE
        binding.web.visibility = View.GONE
        binding.errorPanel.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.web.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.web.apply {
            stopLoading()
            (parent as? android.view.ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "nice_push_article_url"
        fun open(context: Context, url: String) {
            if (!isHttpsUrl(url)) return
            context.startActivity(Intent(context, PushNewsActivity::class.java)
                .putExtra(EXTRA_URL, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
