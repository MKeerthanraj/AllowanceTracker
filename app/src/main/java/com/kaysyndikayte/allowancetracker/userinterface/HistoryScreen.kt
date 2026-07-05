package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.DateRangeEntity
import com.kaysyndikayte.allowancetracker.ui.LiveIndicatorDot
import com.kaysyndikayte.allowancetracker.utils.DateUtils
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: AllowanceViewModel, onBack: () -> Unit, onSelectRange: () -> Unit) {
    val ranges by viewModel.allDateRanges.collectAsState()
    val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    var rangeToDelete by remember { mutableStateOf<DateRangeEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Date Ranges") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(ranges, key = { it.id }) { range ->
                val isLive = DateUtils.isLive(range.startEpochDay, range.endEpochDay)
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(DateUtils.formatRange(range.startEpochDay, range.endEpochDay))
                            if (isLive) {
                                Spacer(Modifier.width(8.dp))
                                LiveIndicatorDot()
                            }
                        }
                    },
                    supportingContent = { Text("Allowance: ${money.format(range.allowanceAmount)}") },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            viewModel.selectRange(range.id)
                            onSelectRange()
                        },
                        onLongClick = { rangeToDelete = range }
                    )
                )
                Divider()
            }
        }
    }

    rangeToDelete?.let { range ->
        val isLive = DateUtils.isLive(range.startEpochDay, range.endEpochDay)
        AlertDialog(
            onDismissRequest = { rangeToDelete = null },
            title = { Text(if (isLive) "End this active period?" else "Delete this period?") },
            text = {
                Text(
                    if (isLive) {
                        "This allowance period is currently live. Are you sure you want to end it and delete it? All its transactions will be removed too."
                    } else {
                        "Are you sure you want to delete this period (${DateUtils.formatRange(range.startEpochDay, range.endEpochDay)})? All its transactions will be removed too."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDateRange(range)
                    rangeToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { rangeToDelete = null }) { Text("Cancel") }
            }
        )
    }
}