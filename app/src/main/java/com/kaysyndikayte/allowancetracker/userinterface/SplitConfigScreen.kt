package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.logic.SplitCalculator
import java.math.BigDecimal

enum class SplitMode { EQUAL, UNEQUAL, SHARES, PERCENTAGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitConfigScreen(
    totalAmount: BigDecimal,
    participants: List<Pair<String, String>>, // userId to displayName
    onConfirm: (splitType: String, amounts: Map<String, BigDecimal>) -> Unit,
    onBack: () -> Unit
) {
    var mode by remember { mutableStateOf(SplitMode.EQUAL) }
    val unequalAmounts = remember { mutableStateMapOf<String, String>() }
    val shareValues = remember { mutableStateMapOf<String, String>().apply { participants.forEach { put(it.first, "1") } } }
    val percentValues = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Split ₹$totalAmount") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            SingleChoiceSegmented(mode, onSelect = { mode = it })
            Spacer(Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(participants) { (id, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(name, modifier = Modifier.weight(1f))
                        when (mode) {
                            SplitMode.EQUAL -> {
                                val eachAmount = totalAmount.divide(BigDecimal(participants.size), 2, java.math.RoundingMode.DOWN)
                                Text("₹$eachAmount")
                            }
                            SplitMode.UNEQUAL -> OutlinedTextField(
                                value = unequalAmounts[id] ?: "", onValueChange = { unequalAmounts[id] = it },
                                modifier = Modifier.width(100.dp), label = { Text("₹") }
                            )
                            SplitMode.SHARES -> OutlinedTextField(
                                value = shareValues[id] ?: "1", onValueChange = { shareValues[id] = it },
                                modifier = Modifier.width(80.dp), label = { Text("shares") }
                            )
                            SplitMode.PERCENTAGE -> OutlinedTextField(
                                value = percentValues[id] ?: "", onValueChange = { percentValues[id] = it },
                                modifier = Modifier.width(90.dp), label = { Text("%") }
                            )
                        }
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    try {
                        val ids = participants.map { it.first }
                        val results = when (mode) {
                            SplitMode.EQUAL -> SplitCalculator.equal(totalAmount, ids)
                            SplitMode.UNEQUAL -> SplitCalculator.unequal(
                                ids.associateWith { (unequalAmounts[it] ?: "0").toBigDecimal() }, totalAmount
                            )
                            SplitMode.SHARES -> SplitCalculator.byShares(
                                ids.associateWith { (shareValues[it] ?: "1").toInt() }, totalAmount
                            )
                            SplitMode.PERCENTAGE -> SplitCalculator.byPercentage(
                                ids.associateWith { (percentValues[it] ?: "0").toBigDecimal() }, totalAmount
                            )
                        }
                        onConfirm(mode.name.lowercase(), results.associate { it.userId to it.amount })
                    } catch (e: Exception) {
                        error = e.message
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Confirm split") }
        }
    }
}

@Composable
private fun SingleChoiceSegmented(selected: SplitMode, onSelect: (SplitMode) -> Unit) {
    Row {
        SplitMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}