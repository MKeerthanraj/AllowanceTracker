package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordTransactionDialog(
    earnedOrDebt: Double,
    onConfirm: (reason: String, category: Category, amount: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Category.FOOD) }
    var expanded by remember { mutableStateOf(false) }
    var showOverWarning by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        leadingIcon = { Icon(selectedCategory.icon, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        Category.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName) },
                                leadingIcon = { Icon(cat.icon, contentDescription = null) },
                                onClick = { selectedCategory = cat; expanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; showOverWarning = false },
                    label = { Text("Amount") },
                    leadingIcon = { Text("₹") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (showOverWarning) {
                    Text(
                        "⚠ This amount exceeds your current earned allowance and will push you into debt. Confirm again to proceed.",
                        color = Color(0xFFB00020)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (amount == null || amount <= 0 || reason.isBlank()) return@TextButton
                if (amount > earnedOrDebt && !showOverWarning) {
                    showOverWarning = true
                } else {
                    onConfirm(reason, selectedCategory, amount)
                }
            }) { Text(if (showOverWarning) "Confirm Anyway" else "Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}