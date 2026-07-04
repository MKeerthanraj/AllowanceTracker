package com.kaysyndikayte.allowancetracker.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kaysyndikayte.allowancetracker.data.AppDatabase
import com.kaysyndikayte.allowancetracker.repository.AllowanceRepository

class AllowanceViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(context)
        val repository = AllowanceRepository(db.dateRangeDao(), db.transactionDao())
        @Suppress("UNCHECKED_CAST")
        return AllowanceViewModel(repository) as T
    }
}