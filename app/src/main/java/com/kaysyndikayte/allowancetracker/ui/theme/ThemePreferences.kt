package com.kaysyndikayte.allowancetracker.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

/**
 * Thin persistence layer. Stores "SYSTEM" / "LIGHT" / "DARK" as plain text so it's readable
 * in the DataStore file if you ever need to debug it, and falls back to SYSTEM for anyone
 * upgrading who has no value saved yet.
 */
class ThemePreferences(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }
}
