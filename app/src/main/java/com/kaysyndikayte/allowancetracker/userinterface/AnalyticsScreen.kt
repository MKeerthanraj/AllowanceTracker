package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.Category
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AllowanceViewModel, onBack: () -> Unit) {
    val analytics by viewModel.analytics.collectAsState()
    val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            analytics?.let { data ->
                Text("Total Spent: ${money.format(data.totalSpent)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                data.topCategory?.let {
                    val cat = Category.valueOf(it.category)
                    Text("Top category: ${cat.displayName} (${money.format(it.total)})")
                }
                Spacer(Modifier.height(16.dp))
                Text("Breakdown by category", style = MaterialTheme.typography.titleSmall)
                LazyColumn {
                    items(data.perCategory) { spend ->
                        val cat = Category.valueOf(spend.category)
                        ListItem(
                            leadingContent = { Icon(cat.icon, contentDescription = null) },
                            headlineContent = { Text(cat.displayName) },
                            supportingContent = { Text("${"%.1f".format(spend.percent)}% of total") },
                            trailingContent = { Text(money.format(spend.total)) }
                        )
                        Divider()
                    }
                }
            } ?: Text("No data for this range yet.")
        }
    }
}