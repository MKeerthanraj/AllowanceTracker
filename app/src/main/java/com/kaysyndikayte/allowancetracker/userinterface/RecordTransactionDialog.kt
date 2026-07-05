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
    var selectedCategory by remember { mutableStateOf(Category.SELECT_CATEGORY) }
    var expanded by remember { mutableStateOf(false) }
    var showDebtWarning by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var attemptedSubmit by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()
    val exceedsTotalAllowance = amount != null && amount > remainingAllowance

    val reasonError = attemptedSubmit && reason.isBlank()
    val amountError = attemptedSubmit && (amount == null || amount <= 0)
    val categoryError = attemptedSubmit && selectedCategory == Category.SELECT_CATEGORY

    val displayFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a") }
    val displayDateTime = remember(timestampMillis) {
        Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).format(displayFormatter)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    isError = reasonError,
                    supportingText = { if (reasonError) Text("Reason is required") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        isError = categoryError,
                        supportingText = { if (categoryError) Text("Please select a category") },
                        leadingIcon = { Icon(selectedCategory.icon, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        Category.values().filter { it != Category.SELECT_CATEGORY }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName) },
                                leadingIcon = { Icon(cat.icon, contentDescription = null) },
                                onClick = { selectedCategory = cat; expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; showDebtWarning = false },
                    label = { Text("Amount") },
                    leadingIcon = { Text("₹") },
                    isError = amountError || exceedsTotalAllowance,
                    supportingText = {
                        when {
                            amountError -> Text("Enter a valid amount")
                            exceedsTotalAllowance -> Text("Exceeds remaining allowance (₹${"%.2f".format(remainingAllowance)})")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

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

                if (showDebtWarning && !exceedsTotalAllowance) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ This amount exceeds your currently earned allowance and will push you into debt. Confirm again to proceed.",
                        color = Color(0xFFF57C00)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attemptedSubmit = true
                val amt = amount
                val hasErrors = reason.isBlank() ||
                        amt == null || amt <= 0 ||
                        selectedCategory == Category.SELECT_CATEGORY ||
                        exceedsTotalAllowance
                if (hasErrors) return@TextButton

                if (amt!! > earnedOrDebt && !showDebtWarning) {
                    showDebtWarning = true
                } else {
                    onConfirm(reason, selectedCategory, amt, timestampMillis)
                }
            }) { Text(if (showDebtWarning) "Confirm Anyway" else "Confirm") }
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