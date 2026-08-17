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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.Category
import com.kaysyndikayte.allowancetracker.data.DateRangeDto
import com.kaysyndikayte.allowancetracker.data.PersonalTransactionDto
import com.kaysyndikayte.allowancetracker.utils.DateUtils
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.kaysyndikayte.allowancetracker.utils.ReceiptParser
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.kaysyndikayte.allowancetracker.ui.RecordTransactionDialog
import com.kaysyndikayte.allowancetracker.ui.theme.ThemeToggleAction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AllowanceViewModel,
    sharedImageUri: Uri?,
    onImageConsumed: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val range by viewModel.selectedRange.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val transactions by viewModel.transactionsForSelectedRange.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewRangeDialog by remember { mutableStateOf(false) }
    var showTransactionDialog by remember { mutableStateOf(false) }
    var prefillReason by remember { mutableStateOf("") }
    var prefillAmount by remember { mutableStateOf("") }
    var prefillTimestamp by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel.eventFlow) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is AllowanceViewModel.AllowanceEvent.DuplicateTransaction -> {
                    snackbarHostState.showSnackbar(
                        message = "Duplicate transaction detected: ${event.reason} (${formatRupees(event.amount)})",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // Whenever a receipt image is shared in, parse it and open the dialog pre-filled
    LaunchedEffect(sharedImageUri) {
        val uri = sharedImageUri ?: return@LaunchedEffect
        scope.launch {
            val parsed = ReceiptParser.parse(context, uri)
            prefillReason = parsed.reason ?: ""
            prefillAmount = parsed.amount?.toString() ?: ""
            prefillTimestamp = parsed.timestampMillis
            showTransactionDialog = true
            onImageConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The tab bar below already owns the bottom inset; taking it again here would leave
        // a dead strip above it.
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = {
                    // The remaining figure used to sit in a SpaceBetween Row here. With four
                    // action icons the title had so little width left that it wrapped to one
                    // character per line; it lives in the summary card below now.
                    Column {
                        Text(
                            text = range?.name ?: "No range set",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = range?.let { DateUtils.formatRange(it.start_epoch_day, it.end_epoch_day) } ?: "-",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.History, contentDescription = "History") }
                    IconButton(onClick = onOpenAnalytics) { Icon(Icons.Filled.BarChart, contentDescription = "Analytics") }
                    IconButton(onClick = { showNewRangeDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "New Range") }
                    ThemeToggleAction()
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
                text = formatRupees(earned),
                style = MaterialTheme.typography.displaySmall,
                color = if (earned >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (earned >= 0) "In Credit" else "In Debt",
                color = if (earned >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            )

            if (earned < 0) {
                Spacer(Modifier.height(4.dp))
                summary?.daysToClearDebt?.let { days ->
                    Text(
                        text = if (days == 1) "1 day until you're back in the green"
                        else "$days days until you're back in the green",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF757575)
                    )
                } ?: run {
                    // daysToClearDebt is null while in debt only when it can't clear before period ends
                    Text(
                        text = "At this rate, debt won't clear before the period ends",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            summary?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SummaryRow("Remaining this period", formatRupees(it.remaining), bold = true)
                        SummaryRow("Spent so far", formatRupees(it.totalSpent))
                        SummaryRow("Per day allowance", formatRupees(it.perDayAllowance))
                    }
                }
                if (it.isEnded) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This period has ended",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { showTransactionDialog = true },
                enabled = range != null && summary?.isEnded == false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Record Transaction")
            }

            if (range != null && summary?.isEnded == false) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.forceEndCurrentRange() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Text("End Period Early")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Transaction History", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // weight so the list takes the leftover height instead of being squeezed out
            // by everything above it on a short screen.
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(transactions, key = { it.id }) { tx ->
                    val cat = Category.fromName(tx.category)
                    val dateTimeStr = remember(tx.timestamp_millis) {
                        java.time.Instant.ofEpochMilli(tx.timestamp_millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                    }

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteTransaction(tx)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color = when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.EndToStart -> Color.Red
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        }
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Column {
                                ListItem(
                                    headlineContent = { Text(tx.reason) },
                                    supportingContent = { Text("${cat.displayName} • $dateTimeStr") },
                                    leadingContent = { Icon(cat.icon, contentDescription = null) },
                                    trailingContent = { Text(formatRupees(tx.amount)) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewRangeDialog) {
        NewDateRangeDialog(
            onConfirm = { name, start, end, amount ->
                viewModel.addDateRange(name, start, end, amount)
                showNewRangeDialog = false
            },
            onDismiss = { showNewRangeDialog = false }
        )
    }

    if (showTransactionDialog && summary != null && range != null) {
        RecordTransactionDialog(
            earnedOrDebt = summary!!.earnedOrDebt,
            remainingAllowance = summary!!.remaining,
            initialReason = prefillReason,
            initialAmount = prefillAmount,
            initialTimestampMillis = prefillTimestamp,
            onConfirm = { reason, category, amount, timestamp ->
                viewModel.addTransaction(reason, category.name, amount, timestamp)
                showTransactionDialog = false
                prefillReason = ""
                prefillAmount = ""
                prefillTimestamp = null
            },
            onDismiss = {
                showTransactionDialog = false
                prefillReason = ""
                prefillAmount = ""
                prefillTimestamp = null
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}