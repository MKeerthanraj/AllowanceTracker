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

    SplitConfigScreen(
        totalAmount = pendingExpenseViewModel.totalAmount,
        participants = pendingExpenseViewModel.participants,
        onConfirm = { splitType, amounts ->
            scope.launch {
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
            }
        },
        onBack = onBack
    )
}