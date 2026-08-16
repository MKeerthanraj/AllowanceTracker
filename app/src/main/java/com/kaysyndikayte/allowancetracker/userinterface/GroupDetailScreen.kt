package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.SupabaseClientProvider
import com.kaysyndikayte.allowancetracker.repository.GroupRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Share
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: (groupId: String) -> Unit
) {
    val groupRepository = remember { GroupRepository() }
    val myUserId = remember { SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: "" }
    val scope = rememberCoroutineScope()

    var members by remember { mutableStateOf<List<com.kaysyndikayte.allowancetracker.repository.MemberProfile>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Any>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<String, java.math.BigDecimal>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var inviteCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    var expenseHistory by remember { mutableStateOf<List<com.kaysyndikayte.allowancetracker.data.GroupExpenseDetail>>(emptyList()) }

    // inside refresh():
    suspend fun refresh() {
        isLoading = true
        members = groupRepository.getGroupMembers(groupId)
        balances = groupRepository.getNetBalances(groupId, myUserId)
        inviteCode = groupRepository.getInviteCode(groupId)
        expenseHistory = groupRepository.getGroupExpenseHistory(groupId)
        isLoading = false
    }

    LaunchedEffect(groupId) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddExpense(groupId) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add expense")
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text("Members", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
                members.forEach { member ->
                    AssistChip(onClick = {}, label = { Text(member.display_name) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Invite code", style = MaterialTheme.typography.labelMedium)
                    Text(inviteCode, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my group on AllowanceTracker! Use invite code: $inviteCode")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share invite code"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share invite code")
                }
            }
            Text("Balances", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))

            if (balances.isEmpty()) {
                Text("All settled up", modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                balances.forEach { (userId, amount) ->
                    val name = members.find { it.id == userId }?.display_name ?: "Someone"
                    val positive = amount.signum() > 0
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = if (positive) "$name owes you ₹${amount.abs()}" else "You owe $name ₹${amount.abs()}",
                            color = if (positive) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))

            if (expenseHistory.isEmpty()) {
                Text("No expenses yet", modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(expenseHistory, key = { it.id }) { expense ->
                        ExpenseHistoryCard(expense = expense, currentUserId = myUserId)
                    }
                }
            }
        }
    }
}