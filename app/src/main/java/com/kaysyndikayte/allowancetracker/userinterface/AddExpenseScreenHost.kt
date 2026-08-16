// AddExpenseScreenHost.kt
package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.runtime.*
import com.kaysyndikayte.allowancetracker.repository.GroupRepository
import com.kaysyndikayte.allowancetracker.viewmodel.PendingExpenseViewModel

@Composable
fun AddExpenseScreenHost(
    groupId: String,
    pendingExpenseViewModel: PendingExpenseViewModel,
    onGoToSplitConfig: () -> Unit,
    onGoToReceiptCapture: () -> Unit,
    onBack: () -> Unit
) {
    val groupRepository = remember { GroupRepository() }
    var members by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(groupId) {
        members = groupRepository.getGroupMembers(groupId).map { it.id to it.display_name }
    }

    AddExpenseScreen(
        groupMembers = members,
        onManualEntry = { amount, paidBy, participantIds ->
            pendingExpenseViewModel.groupId = groupId
            pendingExpenseViewModel.totalAmount = amount
            pendingExpenseViewModel.paidBy = paidBy
            pendingExpenseViewModel.participants = members.filter { participantIds.contains(it.first) }
            onGoToSplitConfig()
        },
        onReceiptCapture = onGoToReceiptCapture,
        onBack = onBack
    )
}