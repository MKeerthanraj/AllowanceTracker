package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupMembers: List<Pair<String, String>>, // userId to displayName
    onManualEntry: (amount: java.math.BigDecimal, paidBy: String, participants: List<String>) -> Unit,
    onReceiptCapture: () -> Unit,
    onBack: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf(groupMembers.firstOrNull()?.first ?: "") }
    val selectedParticipants = remember { mutableStateListOf<String>().apply { addAll(groupMembers.map { it.first }) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Add expense") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

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
                value = amountText, onValueChange = { amountText = it },
                label = { Text("Total amount (₹)") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Text("Paid by", style = MaterialTheme.typography.labelLarge)
            groupMembers.forEach { (id, name) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = paidBy == id, onClick = { paidBy = id })
                    Text(name)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Who's involved? (uncheck anyone not part of this expense)", style = MaterialTheme.typography.labelLarge)
            groupMembers.forEach { (id, name) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedParticipants.contains(id),
                        onCheckedChange = { checked ->
                            if (checked) selectedParticipants.add(id) else selectedParticipants.remove(id)
                        }
                    )
                    Text(name)
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val amount = amountText.toBigDecimalOrNull() ?: return@Button
                    if (selectedParticipants.isEmpty()) return@Button
                    onManualEntry(amount, paidBy, selectedParticipants.toList())
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Next: choose split") }
        }
    }
}