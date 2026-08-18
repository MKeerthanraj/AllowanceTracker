package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.logic.ReceiptItem
import com.kaysyndikayte.allowancetracker.logic.SplitCalculator
import com.kaysyndikayte.allowancetracker.ui.theme.ThemeToggleAction
import java.math.BigDecimal
import java.util.UUID

private data class EditableItem(
    val localId: String = UUID.randomUUID().toString(),
    val name: String,
    val price: BigDecimal,
    val participantIds: Set<String> = emptySet()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSplitScreen(
    initialItems: List<ReceiptItem>,
    taxAmount: BigDecimal,
    groupMembers: List<Pair<String, String>>,
    initialName: String = "",
    saveError: String? = null,
    isSaving: Boolean = false,
    onConfirm: (name: String, items: List<ReceiptItem>, amounts: Map<String, BigDecimal>) -> Unit,
    onBack: () -> Unit
) {
    // Keyed on the incoming list so reopening a saved receipt restores its items, and each
    // item's people, exactly as they were recorded.
    var items by remember(initialItems) {
        mutableStateOf(
            initialItems.map {
                EditableItem(
                    name = it.name,
                    price = it.price,
                    participantIds = it.participantIds.toSet()
                )
            }
        )
    }
    var expenseName by remember(initialName) { mutableStateOf(initialName) }
    var taxText by remember { mutableStateOf(taxAmount.toPlainString()) }
    var editingItem by remember { mutableStateOf<EditableItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val itemsSubtotal = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.price) }
    val tax = taxText.toAmountOrNull() ?: BigDecimal.ZERO
    val runningTotal = itemsSubtotal.add(tax)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Who had what?") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { ThemeToggleAction() }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            OutlinedTextField(
                value = expenseName,
                onValueChange = { expenseName = it },
                label = { Text("What was this for?") },
                placeholder = { Text("Dinner at Empire") },
                singleLine = true,
                isError = error != null && expenseName.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = taxText,
                onValueChange = { taxText = filterAmountInput(it) },
                label = { Text("Tax") },
                leadingIcon = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Split proportionally among everyone involved, based on what each person ordered.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Items ${formatRupees(itemsSubtotal)} + tax ${formatRupees(tax)}",
                    style = MaterialTheme.typography.bodySmall)
                Text(formatRupees(runningTotal), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items, key = { it.localId }) { item ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.name} — ₹${item.price}", style = MaterialTheme.typography.titleSmall)
                            }
                            IconButton(onClick = { editingItem = item }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit item")
                            }
                            IconButton(onClick = {
                                items = items.filter { it.localId != item.localId }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete item")
                            }
                        }
                        FlowRowMembers(
                            members = groupMembers,
                            selected = item.participantIds,
                            onToggle = { userId ->
                                items = items.map {
                                    if (it.localId == item.localId) {
                                        val updated = if (it.participantIds.contains(userId))
                                            it.participantIds - userId else it.participantIds + userId
                                        it.copy(participantIds = updated)
                                    } else it
                                }
                            }
                        )
                    }
                    HorizontalDivider()
                }
            }

            TextButton(
                onClick = { showAddDialog = true },
                enabled = !isSaving,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add an item")
            }

            (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    if (expenseName.isBlank()) {
                        error = "Give this expense a name"
                        return@Button
                    }
                    if (items.isEmpty()) {
                        error = "Add at least one item"
                        return@Button
                    }
                    val receiptItems = items.map { ReceiptItem(it.name, it.price, it.participantIds.toList()) }
                    if (receiptItems.any { it.participantIds.isEmpty() }) {
                        error = "Every item needs at least one person"
                        return@Button
                    }
                    if (runningTotal.signum() <= 0) {
                        error = "The receipt total must be more than zero"
                        return@Button
                    }
                    error = null
                    val results = SplitCalculator.itemized(receiptItems, tax)
                    onConfirm(
                        expenseName.trim(),
                        receiptItems,
                        results.associate { it.userId to it.amount }
                    )
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                // Saving fires a long chain of Supabase inserts; without this the screen
                // just sat there looking frozen after the tap.
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving split...")
                } else {
                    Text("Confirm split")
                }
            }
        }
    }

    editingItem?.let { item ->
        EditItemDialog(
            initialName = item.name,
            initialPrice = item.price.toPlainString(),
            onDismiss = { editingItem = null },
            onSave = { newName, newPrice ->
                items = items.map {
                    if (it.localId == item.localId) it.copy(name = newName, price = newPrice) else it
                }
                editingItem = null
            }
        )
    }

    if (showAddDialog) {
        EditItemDialog(
            initialName = "",
            initialPrice = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, price ->
                items = items + EditableItem(name = name, price = price)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun EditItemDialog(
    initialName: String,
    initialPrice: String,
    onDismiss: () -> Unit,
    onSave: (name: String, price: BigDecimal) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var priceText by remember { mutableStateOf(initialPrice) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isBlank()) "Add item" else "Edit item") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item name") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = filterAmountInput(it) },
                    label = { Text("Price") },
                    leadingIcon = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = priceText.toAmountOrNull()
                if (name.isBlank() || price == null || price.signum() < 0) {
                    error = "Enter a valid name and price"
                    return@TextButton
                }
                onSave(name.trim(), price)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FlowRowMembers(
    members: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Row(modifier = Modifier.padding(top = 4.dp)) {
        members.forEach { (id, name) ->
            FilterChip(
                selected = selected.contains(id),
                onClick = { onToggle(id) },
                label = { Text(name) },
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}