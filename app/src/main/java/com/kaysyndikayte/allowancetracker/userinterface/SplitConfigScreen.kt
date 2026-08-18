package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.logic.SplitCalculator
import com.kaysyndikayte.allowancetracker.ui.theme.ThemeToggleAction
import java.math.BigDecimal
import java.math.RoundingMode

enum class SplitMode { EQUAL, UNEQUAL, SHARES, PERCENTAGE }

/** What the current inputs add up to, and why they can't be confirmed yet. */
private data class SplitState(
    val amounts: Map<String, BigDecimal>?,
    val assigned: BigDecimal,
    val problem: String?
)

private val HUNDRED = BigDecimal(100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitConfigScreen(
    totalAmount: BigDecimal,
    expenseName: String,
    participants: List<Pair<String, String>>, // userId to displayName
    isSaving: Boolean = false,
    saveError: String? = null,
    onConfirm: (splitType: String, amounts: Map<String, BigDecimal>) -> Unit,
    onBack: () -> Unit
) {
    var mode by remember { mutableStateOf(SplitMode.EQUAL) }
    val unequalAmounts = remember { mutableStateMapOf<String, String>() }
    val shareValues = remember {
        mutableStateMapOf<String, String>().apply { participants.forEach { put(it.first, "1") } }
    }
    val percentValues = remember { mutableStateMapOf<String, String>() }

    val ids = participants.map { it.first }
    val total = totalAmount.setScale(2, RoundingMode.HALF_UP)

    // Recomputed on every keystroke so the remaining figure below is always live.
    val state: SplitState = remember(mode, total, ids, unequalAmounts.toMap(), shareValues.toMap(), percentValues.toMap()) {
        computeSplit(mode, total, ids, unequalAmounts, shareValues, percentValues)
    }

    val remaining = total.subtract(state.assigned)
    val canConfirm = state.amounts != null && state.problem == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Split ${formatRupees(total)}", maxLines = 1)
                        if (expenseName.isNotBlank()) {
                            Text(
                                expenseName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { ThemeToggleAction() }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            SplitModeChips(mode) { mode = it }
            Spacer(Modifier.height(12.dp))

            RunningTotalCard(
                total = total,
                assigned = state.assigned,
                remaining = remaining,
                mode = mode
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(participants, key = { it.first }) { (id, name) ->
                    val share = state.amounts?.get(id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name)
                            // Everyone sees their real figure, including the person who picks
                            // up the leftover paisa on an amount that won't divide evenly.
                            if (share != null) {
                                Text(
                                    formatRupees(share),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        when (mode) {
                            SplitMode.EQUAL -> Unit
                            SplitMode.UNEQUAL -> OutlinedTextField(
                                value = unequalAmounts[id] ?: "",
                                onValueChange = { unequalAmounts[id] = filterAmountInput(it) },
                                modifier = Modifier.width(120.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                label = { Text("₹") }
                            )
                            SplitMode.SHARES -> OutlinedTextField(
                                value = shareValues[id] ?: "",
                                onValueChange = { shareValues[id] = filterWholeNumberInput(it) },
                                modifier = Modifier.width(96.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("shares") }
                            )
                            SplitMode.PERCENTAGE -> OutlinedTextField(
                                value = percentValues[id] ?: "",
                                onValueChange = { percentValues[id] = filterAmountInput(it) },
                                modifier = Modifier.width(100.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                label = { Text("%") }
                            )
                        }
                    }
                }
            }

            state.problem?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            saveError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Button(
                onClick = { state.amounts?.let { onConfirm(mode.name.lowercase(), it) } },
                enabled = canConfirm && !isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving split...")
                } else {
                    Text("Confirm split")
                }
            }
        }
    }
}

@Composable
private fun RunningTotalCard(
    total: BigDecimal,
    assigned: BigDecimal,
    remaining: BigDecimal,
    mode: SplitMode
) {
    val settled = remaining.signum() == 0
    val overAssigned = remaining.signum() < 0

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                overAssigned -> MaterialTheme.colorScheme.errorContainer
                settled -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            LabelledAmount("Total", formatRupees(total))
            LabelledAmount(
                if (mode == SplitMode.PERCENTAGE) "Allocated" else "Assigned",
                formatRupees(assigned)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            LabelledAmount(
                label = when {
                    overAssigned -> "Over by"
                    settled -> "Remaining"
                    else -> "Remaining"
                },
                value = formatRupees(remaining.abs()),
                emphasise = true
            )
        }
    }
}

@Composable
private fun LabelledAmount(label: String, value: String, emphasise: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SplitModeChips(selected: SplitMode, onSelect: (SplitMode) -> Unit) {
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

/**
 * Every mode reports the same three things: the resulting per-person amounts (null when the
 * inputs aren't usable yet), how much of the bill they account for, and the reason the split
 * can't be confirmed. The Confirm button keys off this rather than trying and catching.
 */
private fun computeSplit(
    mode: SplitMode,
    total: BigDecimal,
    ids: List<String>,
    unequalAmounts: Map<String, String>,
    shareValues: Map<String, String>,
    percentValues: Map<String, String>
): SplitState {
    if (ids.isEmpty()) {
        return SplitState(null, BigDecimal.ZERO, "Nobody is part of this expense")
    }
    if (total.signum() <= 0) {
        return SplitState(null, BigDecimal.ZERO, "The expense total must be more than zero")
    }

    return when (mode) {
        SplitMode.EQUAL -> {
            val amounts = SplitCalculator.equal(total, ids).associate { it.userId to it.amount }
            SplitState(amounts, total, null)
        }

        SplitMode.UNEQUAL -> {
            val parsed = ids.associateWith { (unequalAmounts[it] ?: "").toAmountOrNull() ?: BigDecimal.ZERO }
            val assigned = parsed.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
            val difference = total.subtract(assigned)
            when {
                difference.signum() < 0 ->
                    SplitState(null, assigned, "That's ${formatRupees(difference.abs())} more than the expense total")
                difference.signum() > 0 ->
                    SplitState(null, assigned, "${formatRupees(difference)} still needs assigning")
                parsed.values.all { it.signum() == 0 } ->
                    SplitState(null, assigned, "Enter what each person owes")
                else -> SplitState(parsed, assigned, null)
            }
        }

        SplitMode.SHARES -> {
            val parsed = ids.associateWith { (shareValues[it] ?: "").toIntOrNull() ?: 0 }
            if (parsed.values.sum() <= 0) {
                SplitState(null, BigDecimal.ZERO, "Give at least one person a share")
            } else {
                val amounts = SplitCalculator.byShares(parsed, total).associate { it.userId to it.amount }
                SplitState(amounts, total, null)
            }
        }

        SplitMode.PERCENTAGE -> {
            val parsed = ids.associateWith { (percentValues[it] ?: "").toAmountOrNull() ?: BigDecimal.ZERO }
            val percentTotal = parsed.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
            val allocated = total.multiply(percentTotal).divide(HUNDRED, 2, RoundingMode.HALF_UP)
            val difference = HUNDRED.subtract(percentTotal)
            when {
                difference.signum() < 0 ->
                    SplitState(null, allocated, "That's ${difference.abs().stripTrailingZeros().toPlainString()}% over 100%")
                difference.signum() > 0 ->
                    SplitState(null, allocated, "${difference.stripTrailingZeros().toPlainString()}% still needs allocating")
                else -> {
                    val amounts = SplitCalculator.byPercentage(parsed, total).associate { it.userId to it.amount }
                    SplitState(amounts, total, null)
                }
            }
        }
    }
}