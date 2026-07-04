package com.kaysyndikayte.allowancetracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.Category
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordTransactionDialog(
    earnedOrDebt: Double,
    remainingAllowance: Double,
    initialReason: String = "",
    initialAmount: String = "",
    initialTimestampMillis: Long? = null,
    onConfirm: (reason: String, category: Category, amount: Double, timestampMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember(initialReason) { mutableStateOf(initialReason) }
    var amountText by remember(initialAmount) { mutableStateOf(initialAmount) }
    var timestampMillis by remember(initialTimestampMillis) {
        mutableStateOf(initialTimestampMillis ?: System.currentTimeMillis())
    }
    var selectedCategory by remember { mutableStateOf(Category.FOOD) }
    var expanded by remember { mutableStateOf(false) }
    var showDebtWarning by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()
    val exceedsTotalAllowance = amount != null && amount > remainingAllowance

    val displayFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a") }
    val displayDateTime = remember(timestampMillis) {
        Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).format(displayFormatter)
    }

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
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        Category.values().forEach { cat ->
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
                    onValueChange = { amountText = it; showDebtWarning = false },
                    label = { Text("Amount") },
                    leadingIcon = { Text("₹") },
                    isError = exceedsTotalAllowance,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Date & Time", style = MaterialTheme.typography.labelMedium)
                Text(displayDateTime, style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text("Date")
                    }
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Text("Time")
                    }
                    OutlinedButton(
                        onClick = { timestampMillis = System.currentTimeMillis() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Now")
                    }
                }

                // Hard block: cannot proceed at all past this
                if (exceedsTotalAllowance) {
                    Text(
                        "⛔ This amount exceeds your remaining total allowance for this period (₹${"%.2f".format(remainingAllowance)}). Please enter a smaller amount.",
                        color = Color(0xFFB00020)
                    )
                }
                // Soft warning: allowed, just informs the user they'll go into debt
                else if (showDebtWarning) {
                    Text(
                        "⚠ This amount exceeds your currently earned allowance and will push you into debt. Confirm again to proceed.",
                        color = Color(0xFFF57C00)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0 && reason.isNotBlank() && !exceedsTotalAllowance,
                onClick = {
                    val amt = amount ?: return@TextButton
                    if (amt > earnedOrDebt && !showDebtWarning) {
                        showDebtWarning = true
                    } else {
                        onConfirm(reason, selectedCategory, amt, timestampMillis)
                    }
                }
            ) { Text(if (showDebtWarning) "Confirm Anyway" else "Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestampMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { pickedDateMillis ->
                        val existing = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
                        val pickedDate = Instant.ofEpochMilli(pickedDateMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val merged = pickedDate.atTime(existing.toLocalTime())
                        timestampMillis = merged.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val existing = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
        val state = rememberTimePickerState(
            initialHour = existing.hour,
            initialMinute = existing.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val merged = existing.toLocalDate().atTime(state.hour, state.minute)
                    timestampMillis = merged.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
        )
    }
}