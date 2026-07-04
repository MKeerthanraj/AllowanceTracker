package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kaysyndikayte.allowancetracker.utils.DateUtils
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: AllowanceViewModel, onBack: () -> Unit, onSelectRange: () -> Unit) {
    val ranges by viewModel.allDateRanges.collectAsState()
    val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

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
            items(ranges) { range ->
                ListItem(
                    headlineContent = { Text(DateUtils.formatRange(range.startEpochDay, range.endEpochDay)) },
                    supportingContent = { Text("Allowance: ${money.format(range.allowanceAmount)}") },
                    modifier = androidx.compose.ui.Modifier.clickable {
                        viewModel.selectRange(range.id)
                        onSelectRange()
                    }
                )
                Divider()
            }
        }
    }
}