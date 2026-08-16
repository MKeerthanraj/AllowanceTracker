package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.GroupSummary
import com.kaysyndikayte.allowancetracker.repository.GroupRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onOpenGroup: (String) -> Unit) {
    val groupRepository = remember { GroupRepository() }
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<GroupSummary>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        isLoading = true
        errorMessage = null
        try {
            groups = groupRepository.getMyGroups()
        } catch (e: Exception) {
            errorMessage = e.message
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        // Bottom inset belongs to the tab bar hosting this screen.
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = { TopAppBar(title = { Text("Groups") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create group")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TextButton(onClick = { showJoinDialog = true }, modifier = Modifier.padding(16.dp)) {
                Text("Have an invite code? Join a group")
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("Error loading groups", style = MaterialTheme.typography.titleMedium)
                        Text(
                            errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(onClick = { scope.launch { refresh() } }) { Text("Retry") }
                    }
                }
            } else if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No groups yet. Create one or join with an invite code.")
                }
            } else {
                LazyColumn {
                    items(groups) { group ->
                        ListItem(
                            headlineContent = { Text(group.name) },
                            supportingContent = { Text("${group.memberCount} members") },
                            modifier = Modifier.clickable { onOpenGroup(group.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New group") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        groupRepository.createGroup(name)
                        showCreateDialog = false
                        refresh()
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showJoinDialog) {
        var code by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join group") },
            text = {
                Column {
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Invite code") })
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            groupRepository.joinGroupByCode(code.trim())
                            showJoinDialog = false
                            refresh()
                        } catch (e: Exception) {
                            error = "Invalid code"
                        }
                    }
                }) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") }
            }
        )
    }
}