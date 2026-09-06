package com.nice.aceclean.ui

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nice.aceclean.R
import com.nice.aceclean.language.LanguageAdapter
import com.nice.aceclean.language.LanguageItem
import com.nice.aceclean.ui.base.BaseActivity
import com.nice.aceclean.util.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class LanguageActivity : BaseActivity(R.layout.activity_language) {

    override val statusBarColorRes: Int = R.color.language_page_background

    private lateinit var languageAdapter: LanguageAdapter
    private var isClosing = false

    private val languageItems = listOf(
        LanguageItem(R.string.language_english, R.drawable.icon_language_us, Locale.forLanguageTag("en")),
        LanguageItem(R.string.language_japanese, R.drawable.icon_language_jp, Locale.forLanguageTag("ja")),
        LanguageItem(R.string.language_hindi, R.drawable.icon_language_in, Locale.forLanguageTag("hi")),
        LanguageItem(R.string.language_korean, R.drawable.icon_language_kr, Locale.forLanguageTag("ko")),
        LanguageItem(R.string.language_spanish, R.drawable.icon_language_es, Locale.forLanguageTag("es")),
        LanguageItem(R.string.language_portuguese, R.drawable.icon_language_pt, Locale.forLanguageTag("pt")),
        LanguageItem(R.string.language_german, R.drawable.icon_language_de, Locale.forLanguageTag("de")),
        LanguageItem(R.string.language_french, R.drawable.icon_language_fr, Locale.forLanguageTag("fr")),
        LanguageItem(R.string.language_turkish, R.drawable.icon_language_tr, Locale.forLanguageTag("tr")),
        LanguageItem(R.string.language_italian, R.drawable.icon_language_it, Locale.forLanguageTag("it")),
        LanguageItem(R.string.language_afrikaans, R.drawable.icon_language_af, Locale.forLanguageTag("af")),
        LanguageItem(R.string.language_xhosa, R.drawable.icon_language_xh, Locale.forLanguageTag("xh")),
        LanguageItem(R.string.language_zulu, R.drawable.icon_language_zu, Locale.forLanguageTag("zu")),
        LanguageItem(R.string.language_russian, R.drawable.icon_language_ru, Locale.forLanguageTag("ru")),
        LanguageItem(R.string.language_ukrainian, R.drawable.icon_language_uk, Locale.forLanguageTag("uk")),
        LanguageItem(R.string.language_vietnamese, R.drawable.icon_language_vi, Locale.forLanguageTag("vi")),
        LanguageItem(R.string.language_indonesian, R.drawable.icon_language_id, Locale.forLanguageTag("id")),
        LanguageItem(R.string.language_malay, R.drawable.icon_language_ms, Locale.forLanguageTag("ms")),
        LanguageItem(R.string.language_thai, R.drawable.icon_language_th, Locale.forLanguageTag("th")),
        LanguageItem(R.string.language_filipino, R.drawable.icon_language_fil, Locale.forLanguageTag("fil")),
        LanguageItem(R.string.language_tagalog, R.drawable.icon_language_tl, Locale.forLanguageTag("tl")),
        LanguageItem(R.string.language_cebuano, R.drawable.icon_language_ceb, Locale.forLanguageTag("ceb")),
    )

    override fun initViews() {
        view<android.view.View>(R.id.back_button).setOnClickListener { finish() }
        setupLanguageList()
    }

    private fun setupLanguageList() {
        val selectedLocale = LocaleHelper.getSavedLocale(this)
        val sortedItems = languageItems.withSelectedFirst(selectedLocale)
        languageAdapter = LanguageAdapter(sortedItems, selectedLocale) { item ->
            selectLanguage(item.locale)
        }
        view<RecyclerView>(R.id.language_recycler_view).apply {
            layoutManager = LinearLayoutManager(this@LanguageActivity)
            adapter = languageAdapter
        }
    }

    private fun List<LanguageItem>.withSelectedFirst(selectedLocale: Locale): List<LanguageItem> {
        val selectedIndex = indexOfFirst { it.locale.toLanguageTag() == selectedLocale.toLanguageTag() }
        if (selectedIndex <= 0) return this
        return listOf(this[selectedIndex]) + filterIndexed { index, _ -> index != selectedIndex }
    }

    private fun selectLanguage(locale: Locale) {
        if (isClosing) return
        LocaleHelper.saveLocale(this, locale)
        languageAdapter.updateSelected(locale)
        isClosing = true
        languageAdapter.setInteractionsEnabled(false)
        lifecycleScope.launch {
            delay(600L)
            setResult(RESULT_OK, Intent().putExtra(EXTRA_LANGUAGE_CHANGED, true))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    companion object {
        const val EXTRA_LANGUAGE_CHANGED = "extra_language_changed"
    }
}
