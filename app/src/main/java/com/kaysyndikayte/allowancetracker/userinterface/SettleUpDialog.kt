package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.repository.MemberProfile
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Records a cash payment against what you owe someone in the group.
 *
 * Only people you owe are offered. Settling what someone owes *you* isn't something you can
 * do on their behalf -- they hand over the cash, so they record it from their own phone.
 */
@Composable
fun SettleUpDialog(
    members: List<MemberProfile>,
    balances: Map<String, BigDecimal>, // negative = you owe them
    isSaving: Boolean,
    errorMessage: String?,
    onConfirm: (paidToUserId: String, amount: BigDecimal) -> Unit,
    onDismiss: () -> Unit
) {
    // Only debts. abs() so the rest of the screen can talk in plain positive amounts.
    val debts: List<Pair<MemberProfile, BigDecimal>> = remember(members, balances) {
        balances.entries
            .filter { it.value.signum() < 0 }
            .mapNotNull { (userId, amount) ->
                members.find { it.id == userId }?.let { it to amount.abs().setScale(2, RoundingMode.HALF_UP) }
            }
            .sortedByDescending { it.second }
    }

    var selectedUserId by remember(debts) { mutableStateOf(debts.firstOrNull()?.first?.id) }
    var amountText by remember { mutableStateOf("") }

    val owed = debts.find { it.first.id == selectedUserId }?.second

    // Default to paying the debt off in full; the user can lower it for a part payment.
    LaunchedEffect(selectedUserId) {
        amountText = owed?.toPlainString() ?: ""
    }

    val amount = amountText.toAmountOrNull()
    val problem: String? = when {
        selectedUserId == null || owed == null -> "Nobody to settle up with"
        amount == null || amount.signum() <= 0 -> "Enter an amount greater than zero"
        amount > owed -> "That's more than you owe — you only owe ${formatRupees(owed)}"
        else -> null
    }
    val remainingAfter = if (owed != null && amount != null && problem == null) owed.subtract(amount) else null

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Settle up") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (debts.isEmpty()) {
                    Text("You don't owe anyone in this group.")
                } else {
                    Text("Who did you pay?", style = MaterialTheme.typography.labelLarge)
                    debts.forEach { (member, owedToThem) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedUserId == member.id,
                                enabled = !isSaving,
                                onClick = { selectedUserId = member.id }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(member.display_name)
                                Text(
                                    "you owe ${formatRupees(owedToThem)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = filterAmountInput(it) },
                        label = { Text("Amount paid in cash") },
                        leadingIcon = { Text("₹") },
                        singleLine = true,
                        enabled = !isSaving,
                        isError = problem != null && amountText.isNotBlank(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    remainingAfter?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (it.signum() == 0) "This clears what you owe them."
                            else "${formatRupees(it)} will still be outstanding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    problem?.takeIf { amountText.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = selectedUserId ?: return@TextButton
                    val value = amount ?: return@TextButton
                    if (problem != null) return@TextButton
                    onConfirm(target, value)
                },
                enabled = !isSaving && problem == null
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Recording...")
                } else {
                    Text("Record payment")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}
