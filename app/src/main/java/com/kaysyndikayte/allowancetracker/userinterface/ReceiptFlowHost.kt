package com.kaysyndikayte.allowancetracker.userinterface

import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.logic.GroqReceiptParser
import com.kaysyndikayte.allowancetracker.logic.ParsedReceipt
import com.kaysyndikayte.allowancetracker.logic.ReceiptOcrHelper
import com.kaysyndikayte.allowancetracker.repository.ExpenseRepository
import com.kaysyndikayte.allowancetracker.repository.GroupRepository
import com.kaysyndikayte.allowancetracker.logic.ReceiptItem
import com.kaysyndikayte.allowancetracker.viewmodel.PendingExpenseViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal

private sealed class FlowStep {
    object Capture : FlowStep()
    object Processing : FlowStep()
    data class Review(val receipt: ParsedReceipt) : FlowStep()
    data class Error(val message: String) : FlowStep()
}

@Composable
fun ReceiptFlowHost(
    groupId: String,
    pendingExpenseViewModel: PendingExpenseViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val groupRepository = remember { GroupRepository() }
    val expenseRepository = remember { ExpenseRepository() }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf<FlowStep>(FlowStep.Capture) }
    var members by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var paidBy by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        members = groupRepository.getGroupMembers(groupId).map { it.id to it.display_name }
        paidBy = members.firstOrNull()?.first ?: ""
    }

    fun processImage(uri: Uri) {
        step = FlowStep.Processing
        scope.launch {
            try {
                val rawText = ReceiptOcrHelper.extractText(context, uri)
                if (rawText.isBlank()) {
                    step = FlowStep.Error("Couldn't read any text from that image. Try a clearer photo.")
                    return@launch
                }
                val parsed = GroqReceiptParser.parseReceiptText(rawText)
                if (parsed.items.isEmpty()) {
                    step = FlowStep.Error("Couldn't identify any items on the receipt. Try manual entry instead.")
                    return@launch
                }
                step = FlowStep.Review(parsed)
            } catch (e: Exception) {
                step = FlowStep.Error(e.message ?: "Something went wrong parsing the receipt.")
            }
        }
    }

    when (val current = step) {
        is FlowStep.Capture -> ReceiptCaptureScreen(
            onImageReady = { uri -> processImage(uri) },
            onBack = onBack
        )

        is FlowStep.Processing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is FlowStep.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(current.message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { step = FlowStep.Capture }) { Text("Try another photo") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBack) { Text("Back") }
            }
        }

        is FlowStep.Review -> ItemSplitScreen(
            initialItems = current.receipt.items.map { ReceiptItem(it.name, it.price.toBigDecimal()) },
            taxAmount = current.receipt.tax.toBigDecimal(),
            groupMembers = members,
            isSaving = isSaving,
            // taxAmount is what the user left in the Tax field, which is not necessarily what
            // the parser read off the receipt -- saving the parsed figure meant a corrected tax
            // reached the splits but not the expense total.
            onConfirm = { expenseName, items, taxAmount, amounts ->
                scope.launch {
                    isSaving = true
                    try {
                        val subtotal = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.price) }
                        expenseRepository.saveItemizedSplit(
                            groupId = groupId,
                            paidBy = paidBy,
                            reason = expenseName,
                            category = "FOOD",
                            subtotal = subtotal,
                            taxAmount = taxAmount,
                            items = items,
                            amounts = amounts
                        )
                        onDone()
                    } catch (e: Exception) {
                        // This save is a long chain of inserts; a failure part-way used to
                        // escape the coroutine and take the whole app down.
                        isSaving = false
                        step = FlowStep.Error(
                            e.message ?: "Couldn't save the split. Check your connection and try again."
                        )
                    }
                }
            },
            onBack = onBack
        )
    }
}