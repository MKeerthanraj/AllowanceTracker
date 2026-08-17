package com.kaysyndikayte.allowancetracker.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = ThemePreferences(application)

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /**
     * Flips light <-> dark and persists it. `currentlyDark` is the *actual* rendered
     * darkness right now (already resolved from SYSTEM by AllowanceTrackerTheme), so the
     * very first tap — while still on SYSTEM — flips away from whatever the phone is
     * currently showing, rather than requiring two taps to "escape" SYSTEM mode.
     */
    fun toggle(currentlyDark: Boolean) {
        viewModelScope.launch {
            preferences.setThemeMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ThemeViewModel(application) as T
        }
    }
}
