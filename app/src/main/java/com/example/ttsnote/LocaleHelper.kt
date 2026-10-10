package com.example.ttsnote

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    const val LANG_EN = "en"
    const val LANG_ZH = "zh"

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun getLanguage(context: Context): String {
        return prefs(context).getString(KEY_LANGUAGE, LANG_EN) ?: LANG_EN
    }

    fun setLanguage(context: Context, language: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun wrap(context: Context): Context {
        return updateResources(context, getLanguage(context))
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = when (language) {
            LANG_ZH -> Locale.TRADITIONAL_CHINESE
            else -> Locale.ENGLISH
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
