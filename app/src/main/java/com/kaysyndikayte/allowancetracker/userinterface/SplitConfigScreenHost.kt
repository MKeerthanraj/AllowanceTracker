// SplitConfigScreenHost.kt
package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.runtime.*
import com.kaysyndikayte.allowancetracker.repository.ExpenseRepository
import com.kaysyndikayte.allowancetracker.viewmodel.PendingExpenseViewModel
import kotlinx.coroutines.launch

@Composable
fun SplitConfigScreenHost(
    pendingExpenseViewModel: PendingExpenseViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val expenseRepository = remember { ExpenseRepository() }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    SplitConfigScreen(
        totalAmount = pendingExpenseViewModel.totalAmount,
        expenseName = pendingExpenseViewModel.reason,
        participants = pendingExpenseViewModel.participants,
        isSaving = isSaving,
        saveError = saveError,
        onConfirm = { splitType, amounts ->
            scope.launch {
                isSaving = true
                saveError = null
                try {
                    expenseRepository.saveManualSplit(
                        groupId = pendingExpenseViewModel.groupId,
                        paidBy = pendingExpenseViewModel.paidBy,
                        reason = pendingExpenseViewModel.reason.ifBlank { "Group expense" },
                        category = pendingExpenseViewModel.category,
                        totalAmount = pendingExpenseViewModel.totalAmount,
                        splitType = splitType,
                        amounts = amounts
                    )
                    pendingExpenseViewModel.reset()
                    onDone()
                } catch (e: Exception) {
                    isSaving = false
                    saveError = e.message ?: "Couldn't save the split. Check your connection and try again."
                }
            }
        },
        onBack = onBack
    )
}