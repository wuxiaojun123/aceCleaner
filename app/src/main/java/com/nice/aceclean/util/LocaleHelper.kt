package com.nice.aceclean.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    fun getSavedLocale(context: Context): Locale {
        val tag = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, Locale.ENGLISH.toLanguageTag())
            .orEmpty()
        return Locale.forLanguageTag(tag).takeIf { it.language.isNotBlank() } ?: Locale.ENGLISH
    }

    fun saveLocale(context: Context, locale: Locale) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, locale.toLanguageTag())
            .apply()
    }

    fun wrapContext(context: Context): Context {
        val locale = getSavedLocale(context)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(configuration)
    }
}
