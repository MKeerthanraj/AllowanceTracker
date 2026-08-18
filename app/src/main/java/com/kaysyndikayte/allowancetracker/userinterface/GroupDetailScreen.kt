package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.SupabaseClientProvider
import com.kaysyndikayte.allowancetracker.repository.GroupRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Share
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: (groupId: String) -> Unit
) {
    val groupRepository = remember { GroupRepository() }
    val myUserId = remember { SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: "" }
    val scope = rememberCoroutineScope()

    var members by remember { mutableStateOf<List<com.kaysyndikayte.allowancetracker.repository.MemberProfile>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Any>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<String, java.math.BigDecimal>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var inviteCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    var expenseHistory by remember { mutableStateOf<List<com.kaysyndikayte.allowancetracker.data.GroupExpenseDetail>>(emptyList()) }
    val expenseRepository = remember { com.kaysyndikayte.allowancetracker.repository.ExpenseRepository() }
    var showSettleUp by remember { mutableStateOf(false) }
    var selectedExpense by remember {
        mutableStateOf<com.kaysyndikayte.allowancetracker.data.GroupExpenseDetail?>(null)
    }
    var isEditingExpense by remember { mutableStateOf(false) }
    var editExpenseError by remember { mutableStateOf<String?>(null) }
    var isSettling by remember { mutableStateOf(false) }
    var settleError by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        isLoading = true
        try {
            members = groupRepository.getGroupMembers(groupId)
            balances = groupRepository.getNetBalances(groupId, myUserId)
            inviteCode = groupRepository.getInviteCode(groupId)
            expenseHistory = groupRepository.getGroupExpenseHistory(groupId)
            loadError = null
        } finally {
            // Without this the screen stayed on its spinner for good if any one of those
            // queries threw: no error, no content, nothing to tap.
            isLoading = false
        }
    }

    /**
     * Reloading is not the same operation as saving, and it fails for its own reasons. Letting
     * it throw here would either kill the process (from the LaunchedEffect) or report a
     * successful edit as a failed one.
     */
    suspend fun refreshSafely() {
        try {
            refresh()
        } catch (e: Exception) {
            loadError = e.message ?: "Couldn't load this group. Check your connection."
        }
    }

    /**
     * Every edit runs the same shape: hold the dialog, clear the last error, do the work,
     * close, reload. Reloading sits outside the try so a reload failure is reported as what it
     * is rather than as a change that didn't save.
     */
    fun runExpenseEdit(failureMessage: String, work: suspend () -> Unit) {
        scope.launch {
            isEditingExpense = true
            editExpenseError = null
            try {
                work()
                selectedExpense = null
            } catch (e: Exception) {
                editExpenseError = e.message ?: failureMessage
                return@launch
            } finally {
                isEditingExpense = false
            }
            refreshSafely()
        }
    }

    LaunchedEffect(groupId) { refreshSafely() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddExpense(groupId) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add expense")
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            loadError?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { scope.launch { refreshSafely() } }) { Text("Retry") }
                }
            }

            Text("Members", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
                members.forEach { member ->
                    AssistChip(onClick = {}, label = { Text(member.display_name) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Invite code", style = MaterialTheme.typography.labelMedium)
                    Text(inviteCode, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my group on AllowanceTracker! Use invite code: $inviteCode")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share invite code"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share invite code")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Balances",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                // Only meaningful when you owe somebody -- you can't record a payment
                // somebody else made to you.
                TextButton(
                    onClick = { settleError = null; showSettleUp = true },
                    enabled = balances.values.any { it.signum() < 0 }
                ) { Text("Settle up") }
            }

            if (balances.isEmpty()) {
                Text("All settled up", modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                balances.forEach { (userId, amount) ->
                    val name = members.find { it.id == userId }?.display_name ?: "Someone"
                    val positive = amount.signum() > 0
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = if (positive) "$name owes you ₹${amount.abs()}" else "You owe $name ₹${amount.abs()}",
                            color = if (positive) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))

            if (expenseHistory.isEmpty()) {
                Text("No expenses yet", modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(expenseHistory, key = { it.id }) { expense ->
                        ExpenseHistoryCard(
                            expense = expense,
                            currentUserId = myUserId,
                            onLongPress = { editExpenseError = null; selectedExpense = expense }
                        )
                    }
                }
            }
        }
    }

    selectedExpense?.let { expense ->
        ExpenseDetailScreen(
            expense = expense,
            currentUserId = myUserId,
            groupMembers = members.map { it.id to it.display_name },
            isSaving = isEditingExpense,
            errorMessage = editExpenseError,
            onSaveSettlement = { amount ->
                val paidTo = expense.participants.firstOrNull()?.userId
                if (paidTo == null) {
                    editExpenseError = "This payment has no recipient recorded, so it can't be edited."
                } else {
                    runExpenseEdit("Couldn't save that change. Check your connection and try again.") {
                        expenseRepository.updateSettlementAmount(expense.id, paidTo, amount)
                    }
                }
            },
            onSaveSplit = { reason, totalAmount, splitType, amounts ->
                runExpenseEdit("Couldn't save those changes. Check your connection and try again.") {
                    expenseRepository.updateSplit(
                        expenseId = expense.id,
                        reason = reason,
                        totalAmount = totalAmount,
                        splitType = splitType,
                        amounts = amounts
                    )
                }
            },
            onSaveItemized = { reason, items, taxAmount, amounts ->
                runExpenseEdit("Couldn't save those changes. Check your connection and try again.") {
                    val subtotal = items.fold(java.math.BigDecimal.ZERO) { acc, i -> acc.add(i.price) }
                    expenseRepository.updateItemizedSplit(
                        expenseId = expense.id,
                        reason = reason,
                        subtotal = subtotal,
                        taxAmount = taxAmount,
                        items = items,
                        amounts = amounts
                    )
                }
            },
            onDelete = {
                runExpenseEdit("Couldn't delete that. Check your connection and try again.") {
                    expenseRepository.deleteExpense(expense.id)
                }
            },
            onDismiss = { selectedExpense = null }
        )
    }

    if (showSettleUp) {
        SettleUpDialog(
            members = members,
            balances = balances,
            isSaving = isSettling,
            errorMessage = settleError,
            onConfirm = { paidToUserId, amount ->
                scope.launch {
                    isSettling = true
                    settleError = null
                    try {
                        val paidToName = members.find { it.id == paidToUserId }?.display_name
                        expenseRepository.recordSettlement(
                            groupId = groupId,
                            paidByUserId = myUserId,
                            paidToUserId = paidToUserId,
                            amount = amount,
                            note = if (paidToName != null) "Cash settlement to $paidToName" else "Cash settlement"
                        )
                        showSettleUp = false
                    } catch (e: Exception) {
                        settleError = e.message
                            ?: "Couldn't record that payment. Check your connection and try again."
                        return@launch
                    } finally {
                        isSettling = false
                    }
                    // Balances come from the server view, so re-read rather than adjusting the
                    // local copy and hoping the two agree.
                    refreshSafely()
                }
            },
            onDismiss = { showSettleUp = false }
        )
    }
}