package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.ui.theme.ThemeToggleAction
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupMembers: List<Pair<String, String>>, // userId to displayName
    onManualEntry: (
        name: String,
        amount: BigDecimal,
        paidBy: String,
        participants: List<String>
    ) -> Unit,
    onReceiptCapture: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf("") }
    val selectedParticipants = remember { mutableStateListOf<String>() }
    var attemptedSubmit by remember { mutableStateOf(false) }

    // Members arrive asynchronously, so seed the defaults once they land rather than at
    // first composition, when the list is still empty.
    LaunchedEffect(groupMembers) {
        if (paidBy.isBlank()) paidBy = groupMembers.firstOrNull()?.first ?: ""
        if (selectedParticipants.isEmpty()) selectedParticipants.addAll(groupMembers.map { it.first })
    }

    val amount = amountText.toAmountOrNull()
    val nameError = attemptedSubmit && name.isBlank()
    val amountError = attemptedSubmit && (amount == null || amount.signum() <= 0)
    val participantsError = attemptedSubmit && selectedParticipants.isEmpty()
    val paidByError = attemptedSubmit && paidBy.isBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add expense") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { ThemeToggleAction() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedButton(onClick = onReceiptCapture, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan a receipt instead")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Or enter manually", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("What was this for?") },
                placeholder = { Text("Dinner at Empire") },
                singleLine = true,
                isError = nameError,
                supportingText = { if (nameError) Text("Give this expense a name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = filterAmountInput(it) },
                label = { Text("Total amount") },
                leadingIcon = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError,
                supportingText = { if (amountError) Text("Enter an amount greater than zero") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Text("Paid by", style = MaterialTheme.typography.labelLarge)
            groupMembers.forEach { (id, memberName) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = paidBy == id, onClick = { paidBy = id })
                    Text(memberName)
                }
            }
            if (paidByError) {
                Text(
                    "Choose who paid",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Who's involved? (uncheck anyone not part of this expense)",
                style = MaterialTheme.typography.labelLarge
            )
            groupMembers.forEach { (id, memberName) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedParticipants.contains(id),
                        onCheckedChange = { checked ->
                            if (checked) selectedParticipants.add(id) else selectedParticipants.remove(id)
                        }
                    )
                    Text(memberName)
                }
            }
            if (participantsError) {
                Text(
                    "Pick at least one person",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    attemptedSubmit = true
                    val validAmount = amountText.toAmountOrNull() ?: return@Button
                    if (name.isBlank() || validAmount.signum() <= 0) return@Button
                    if (paidBy.isBlank() || selectedParticipants.isEmpty()) return@Button
                    onManualEntry(name.trim(), validAmount, paidBy, selectedParticipants.toList())
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Next: choose split") }
        }
    }
}