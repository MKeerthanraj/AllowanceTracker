package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kaysyndikayte.allowancetracker.data.GroupExpenseDetail
import com.kaysyndikayte.allowancetracker.logic.ReceiptItem
import java.math.BigDecimal
import java.math.RoundingMode

const val SPLIT_TYPE_SETTLEMENT = "settlement"
const val SPLIT_TYPE_ITEMIZED = "itemized"

/**
 * Long-pressing an entry in group activity opens this. What you can change depends on what
 * kind of entry it is:
 *
 *  - a cash settlement is one person handing money to another, so only the amount is editable
 *  - a receipt split reopens ItemSplitScreen with every saved item and who shared it
 *  - any other split reopens SplitConfigScreen with the current amounts
 *
 * The last two are the same screens used when creating the expense, prefilled, rather than a
 * separate editor that could drift away from them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    expense: GroupExpenseDetail,
    currentUserId: String,
    groupMembers: List<Pair<String, String>>,
    isSaving: Boolean,
    errorMessage: String?,
    onSaveSettlement: (amount: BigDecimal) -> Unit,
    onSaveSplit: (reason: String, totalAmount: BigDecimal, splitType: String, amounts: Map<String, BigDecimal>) -> Unit,
    onSaveItemized: (reason: String, items: List<ReceiptItem>, taxAmount: BigDecimal, amounts: Map<String, BigDecimal>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember(expense.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                editing && expense.splitType == SPLIT_TYPE_ITEMIZED -> ItemSplitScreen(
                    initialItems = expense.items.map {
                        ReceiptItem(it.name, it.price, it.participantIds)
                    },
                    taxAmount = expense.taxAmount,
                    groupMembers = groupMembers,
                    initialName = expense.reason,
                    saveError = errorMessage,
                    isSaving = isSaving,
                    // The tax comes back from the screen rather than being read off the
                    // expense again: the Tax field is editable, and reusing the saved figure
                    // meant an edited tax reached expense_splits but never the expense row,
                    // leaving the two disagreeing about the total.
                    onConfirm = { name, items, tax, amounts ->
                        onSaveItemized(name, items, tax, amounts)
                    },
                    onBack = { editing = false }
                )

                editing -> SplitConfigScreen(
                    totalAmount = expense.amount.setScale(2, RoundingMode.HALF_UP),
                    expenseName = expense.reason,
                    // The whole group, not just whoever is on the expense today. Passing only
                    // the current participants meant nobody could ever be added, and anybody
                    // removed in one edit was unrecoverable in every edit after it.
                    participants = groupMembers,
                    initiallyIncluded = expense.participants.map { it.userId }.toSet(),
                    isSaving = isSaving,
                    saveError = errorMessage,
                    onConfirm = { splitType, amounts ->
                        onSaveSplit(
                            expense.reason,
                            expense.amount.setScale(2, RoundingMode.HALF_UP),
                            splitType,
                            amounts
                        )
                    },
                    onBack = { editing = false }
                )

                else -> ExpenseDetailBody(
                    expense = expense,
                    currentUserId = currentUserId,
                    isSaving = isSaving,
                    errorMessage = errorMessage,
                    onEditSplit = { editing = true },
                    onSaveSettlement = onSaveSettlement,
                    onRequestDelete = { confirmDelete = true },
                    onDismiss = onDismiss
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    if (expense.splitType == SPLIT_TYPE_SETTLEMENT) "Delete this payment?"
                    else "Delete this expense?"
                )
            },
            text = {
                Text(
                    expense.reason + " for " +
                        formatRupees(expense.amount.setScale(2, RoundingMode.HALF_UP)) +
                        " will be removed for everyone in the group, and the balances it " +
                        "created will be undone. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDetailBody(
    expense: GroupExpenseDetail,
    currentUserId: String,
    isSaving: Boolean,
    errorMessage: String?,
    onEditSplit: () -> Unit,
    onSaveSettlement: (BigDecimal) -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isSettlement = expense.splitType == SPLIT_TYPE_SETTLEMENT
    val originalTotal = expense.amount.setScale(2, RoundingMode.HALF_UP)

    var amountText by remember(expense.id) { mutableStateOf(originalTotal.toPlainString()) }
    val amount = amountText.toAmountOrNull()?.setScale(2, RoundingMode.HALF_UP)
    val amountChanged = amount != null && amount.compareTo(originalTotal) != 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSettlement) "Cash payment" else "Expense") },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onRequestDelete, enabled = !isSaving) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(expense.reason, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            DetailRow("Paid by", if (expense.paidByUserId == currentUserId) "You" else expense.paidByName)
            if (!isSettlement) DetailRow("Split type", expense.splitType)
            DetailRow("Recorded", formatIsoDate(expense.createdAt))

            if (isSettlement) {
                // Only the cash figure is editable: who paid whom is a matter of record.
                val paidTo = expense.participants.firstOrNull()
                DetailRow(
                    "Paid to",
                    if (paidTo == null) "Someone"
                    else if (paidTo.userId == currentUserId) "You" else paidTo.displayName
                )

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = filterAmountInput(it) },
                    label = { Text("Amount paid in cash") },
                    leadingIcon = { Text("₹") },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = amount == null || amount.signum() <= 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { amount?.let(onSaveSettlement) },
                    enabled = !isSaving && amountChanged && amount != null && amount.signum() > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Text("Save amount")
                    }
                }
                return@Column
            }

            if (expense.items.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Items", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                expense.items.forEach { item ->
                    val sharedBy = item.participantIds
                        .mapNotNull { id ->
                            expense.participants.find { it.userId == id }?.let {
                                if (it.userId == currentUserId) "You" else it.displayName
                            }
                        }
                        .joinToString(", ")
                        .ifBlank { "nobody" }
                    DetailRow(item.name + "  (" + sharedBy + ")", formatRupees(item.price))
                }
                if (expense.taxAmount.signum() > 0) {
                    DetailRow("Tax", formatRupees(expense.taxAmount))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Who owes what", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            expense.participants.forEach { p ->
                DetailRow(
                    label = if (p.userId == currentUserId) "You" else p.displayName,
                    value = formatRupees(p.amountOwed)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow("Total", formatRupees(originalTotal), emphasise = true)

            errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onEditSplit,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (expense.splitType == SPLIT_TYPE_ITEMIZED) "Edit items and split"
                    else "Edit split"
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, emphasise: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatIsoDate(isoTimestamp: String): String = try {
    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
        .withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.parse(isoTimestamp))
} catch (e: Exception) {
    isoTimestamp
}
