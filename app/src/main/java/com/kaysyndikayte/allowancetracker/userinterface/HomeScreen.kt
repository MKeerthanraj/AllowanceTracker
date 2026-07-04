package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.Category
import com.kaysyndikayte.allowancetracker.utils.DateUtils
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel
import java.text.NumberFormat
import java.util.Locale

private fun money(v: Double): String =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).format(v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AllowanceViewModel, onOpenAnalytics: () -> Unit, onOpenHistory: () -> Unit) {
    val range by viewModel.selectedRange.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val transactions by viewModel.transactionsForSelectedRange.collectAsState()

    var showNewRangeDialog by remember { mutableStateOf(false) }
    var showTransactionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = range?.let { DateUtils.formatRange(it.startEpochDay, it.endEpochDay) } ?: "No range set",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = summary?.let { money(it.remaining) } ?: "-",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.History, contentDescription = "History") }
                    IconButton(onClick = onOpenAnalytics) { Icon(Icons.Filled.BarChart, contentDescription = "Analytics") }
                    IconButton(onClick = { showNewRangeDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "New Range") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Allowance Status", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val earned = summary?.earnedOrDebt ?: 0.0
            Text(
                text = money(earned),
                style = MaterialTheme.typography.displaySmall,
                color = if (earned >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (earned >= 0) "In Credit" else "In Debt",
                color = if (earned >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            )

            Spacer(Modifier.height(16.dp))
            summary?.let {
                Text("Per day allowance: ${money(it.perDayAllowance)}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { showTransactionDialog = true },
                enabled = range != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Record Transaction")
            }

            Spacer(Modifier.height(24.dp))
            Text("Transaction History", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(transactions) { tx ->
                    val cat = Category.valueOf(tx.category)
                    ListItem(
                        headlineContent = { Text(tx.reason) },
                        supportingContent = { Text(cat.displayName) },
                        leadingContent = { Icon(cat.icon, contentDescription = null) },
                        trailingContent = { Text(money(tx.amount)) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showNewRangeDialog) {
        NewDateRangeDialog(
            onConfirm = { start, end, amount ->
                viewModel.addDateRange(start, end, amount)
                showNewRangeDialog = false
            },
            onDismiss = { showNewRangeDialog = false }
        )
    }

    if (showTransactionDialog && summary != null) {
        RecordTransactionDialog(
            earnedOrDebt = summary!!.earnedOrDebt,
            onConfirm = { reason, category, amount ->
                viewModel.addTransaction(reason, category.name, amount)
                showTransactionDialog = false
            },
            onDismiss = { showTransactionDialog = false }
        )
    }
}