package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.material3.SelectableDates

@Composable
fun NewDateRangeDialog(
    onConfirm: (name: String, startEpochDay: Long, endEpochDay: Long, amount: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var amountText by remember { mutableStateOf("") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var attemptedSubmit by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()

    val nameError = attemptedSubmit && name.isBlank()
    val startError = attemptedSubmit && startDate == null
    val endError = attemptedSubmit && (endDate == null || (startDate != null && endDate!!.isBefore(startDate)))
    val amountError = attemptedSubmit && (amount == null || amount <= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Allowance Period") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Period Name (e.g., Feb 2024)") },
                    isError = nameError,
                    supportingText = { if (nameError) Text("Name is required") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (startError) ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB00020)) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(startDate?.toString() ?: "Select Start Date")
                }
                if (startError) {
                    Text("Start date is required", color = Color(0xFFB00020), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (endError) ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB00020)) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(endDate?.toString() ?: "Select End Date")
                }
                if (endError) {
                    Text("End date is required and must be after start date", color = Color(0xFFB00020), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Allowance Amount") },
                    leadingIcon = { Text("₹") },
                    isError = amountError,
                    supportingText = { if (amountError) Text("Enter a valid amount") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attemptedSubmit = true
                val hasErrors = name.isBlank() || startDate == null || endDate == null ||
                        endDate!!.isBefore(startDate) ||
                        amount == null || amount <= 0
                if (hasErrors) return@TextButton
                onConfirm(name, startDate!!.toEpochDay(), endDate!!.toEpochDay(), amount!!)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showStartPicker) {
        DatePickerModal(
            onDateSelected = { startDate = it; showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        DatePickerModal(
            minSelectableEpochDay = startDate?.toEpochDay(),
            onDateSelected = { endDate = it; showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    minSelectableEpochDay: Long? = null,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                if (minSelectableEpochDay == null) return true
                val candidateEpochDay = java.time.Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
                return candidateEpochDay >= minSelectableEpochDay
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    val date = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    onDateSelected(date)
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}