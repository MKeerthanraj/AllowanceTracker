package com.kaysyndikayte.allowancetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kaysyndikayte.allowancetracker.repository.AllowanceRepository

/**
 * No longer needs a Context: Room's AppDatabase.getInstance(context) is gone, and
 * SupabaseClientProvider is a plain object singleton, same as AuthRepository's pattern.
 *
 * MainActivity.kt currently does:
 *   AllowanceViewModelFactory(androidx.compose.ui.platform.LocalContext.current)
 * That call site needs to drop the argument: AllowanceViewModelFactory()
 */
class AllowanceViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = AllowanceRepository()
        @Suppress("UNCHECKED_CAST")
        return AllowanceViewModel(repository) as T
    }
}