package com.example.ttsnote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ttsnote.LocaleHelper
import com.example.ttsnote.ui.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    private object PreferencesKeys {
        val GLOBAL_FONT_SIZE = intPreferencesKey("global_font_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("app_language")
    }

    private val _language = MutableStateFlow(LocaleHelper.getLanguage(context))
    val language: Flow<String> = _language.asStateFlow()

    val globalFontSize: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            preferences[PreferencesKeys.GLOBAL_FONT_SIZE] ?: 18
        }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            val modeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.Auto.name
            ThemeMode.valueOf(modeString)
        }

    suspend fun updateFontSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GLOBAL_FONT_SIZE] = size
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun updateLanguage(language: String) {
        LocaleHelper.setLanguage(context, language)
        _language.value = language
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language
        }
    }
}
