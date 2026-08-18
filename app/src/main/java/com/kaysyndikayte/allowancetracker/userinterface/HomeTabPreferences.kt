package com.kaysyndikayte.allowancetracker.userinterface

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeTab { ALLOWANCE, GROUPS }

private val Context.homeTabDataStore by preferencesDataStore(name = "home_tab_prefs")

private val SELECTED_TAB_KEY = stringPreferencesKey("selected_home_tab")

/**
 * Same thin persistence approach as ThemePreferences: the tab is stored as plain text so the
 * DataStore file stays readable, and anyone with no value saved yet lands on ALLOWANCE.
 *
 * rememberSaveable alone wasn't enough -- it carries the tab across rotation and across
 * navigating into a group and back, but a cold start begins with no saved instance state,
 * so the app always reopened on Allowance.
 */
class HomeTabPreferences(private val context: Context) {

    val selectedTab: Flow<HomeTab> = context.homeTabDataStore.data.map { prefs ->
        when (prefs[SELECTED_TAB_KEY]) {
            HomeTab.GROUPS.name -> HomeTab.GROUPS
            else -> HomeTab.ALLOWANCE
        }
    }

    suspend fun setSelectedTab(tab: HomeTab) {
        context.homeTabDataStore.edit { prefs ->
            prefs[SELECTED_TAB_KEY] = tab.name
        }
    }
}

class HomeTabViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = HomeTabPreferences(application)

    /**
     * null means "not read from disk yet". MainScreen waits on that rather than defaulting to
     * ALLOWANCE, so a user whose last tab was Groups doesn't watch the Allowance tab flash up
     * and get replaced on every launch.
     */
    val selectedTab: StateFlow<HomeTab?> = preferences.selectedTab
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun select(tab: HomeTab) {
        viewModelScope.launch { preferences.setSelectedTab(tab) }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HomeTabViewModel(application) as T
        }
    }
}
