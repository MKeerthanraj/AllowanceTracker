package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.GroupExpenseDetail

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseHistoryCard(
    expense: GroupExpenseDetail,
    currentUserId: String,
    onLongPress: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            // Tap still expands the breakdown in place; long press opens the full editor.
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = onLongPress
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.reason, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Paid by ${if (expense.paidByUserId == currentUserId) "you" else expense.paidByName} · ${formatDate(expense.createdAt)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("₹${expense.amount}", style = MaterialTheme.typography.titleMedium)
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Split (${expense.splitType})", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                expense.participants.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (p.userId == currentUserId) "You" else p.displayName)
                        Text(
                            "₹${p.amountOwed}",
                            color = if (p.userId == expense.paidByUserId) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(isoTimestamp: String): String {
    return try {
        val instant = java.time.Instant.parse(isoTimestamp)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
            .withZone(java.time.ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        isoTimestamp
    }
}