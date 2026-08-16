package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.data.GroupSummary
import com.kaysyndikayte.allowancetracker.repository.GroupRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onOpenGroup: (String) -> Unit) {
    val groupRepository = remember { GroupRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var groups by remember { mutableStateOf<List<GroupSummary>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var showIconPickerForGroup by remember { mutableStateOf<GroupSummary?>(null) }

    val rawIcons = remember {
        listOf(
            "dog", "gym", "sun", "cake", "fish", "girl", "home", "crown", "globe", "happy", 
            "heart", "music", "pulse", "skull", "staff", "batman", "poison", "discord", 
            "airplane", "dinosaur", "football", "hospital", "location", "valorant", 
            "butterfly", "pixel_heart", "super_mario", "love_circled", "organization", 
            "lightning_bolt", "game_controller", "minecraft_sword", "obscene_gesture", 
            "valentine_wings", "minecraft_creeper", "minecraft_pickaxe"
        )
    }

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
            var isFabExpanded by remember { mutableStateOf(false) }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                if (isFabExpanded) {
                    SmallFloatingActionButton(
                        onClick = {
                            showJoinDialog = true
                            isFabExpanded = false
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Join Group")
                        }
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            showCreateDialog = true
                            isFabExpanded = false
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create Group")
                        }
                    }
                }
                FloatingActionButton(onClick = { isFabExpanded = !isFabExpanded }) {
                    Icon(
                        if (isFabExpanded) Icons.Filled.Add else Icons.Filled.Add, 
                        contentDescription = "Menu",
                        modifier = Modifier.let { if (isFabExpanded) it.graphicsLayer(rotationZ = 45f) else it }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                        val iconRes = remember(group.iconName) {
                            if (group.iconName != null) {
                                context.resources.getIdentifier(group.iconName, "raw", context.packageName)
                            } else 0
                        }

                        ListItem(
                            headlineContent = { Text(group.name) },
                            supportingContent = { Text("${group.memberCount} members") },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable { showIconPickerForGroup = group },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (iconRes != 0) {
                                        Image(
                                            painter = painterResource(iconRes),
                                            contentDescription = "Group Icon",
                                            modifier = Modifier.size(40.dp)
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(group.name.take(1).uppercase())
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onOpenGroup(group.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showIconPickerForGroup != null) {
        AlertDialog(
            onDismissRequest = { showIconPickerForGroup = null },
            title = { Text("Select Icon") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(300.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rawIcons) { iconName ->
                        val resId = context.resources.getIdentifier(iconName, "raw", context.packageName)
                        if (resId != 0) {
                            IconButton(onClick = {
                                val group = showIconPickerForGroup!!
                                scope.launch {
                                    groupRepository.updateGroupIcon(group.id, iconName)
                                    showIconPickerForGroup = null
                                    refresh()
                                }
                            }) {
                                Image(
                                    painter = painterResource(resId),
                                    contentDescription = iconName,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconPickerForGroup = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var selectedIcon by remember { mutableStateOf<String?>(null) }
        var showIconPickerForCreate by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New group") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, 
                        onValueChange = { name = it }, 
                        label = { Text("Group name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Group Icon", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showIconPickerForCreate = true }
                    ) {
                        val iconRes = if (selectedIcon != null) {
                            context.resources.getIdentifier(selectedIcon, "raw", context.packageName)
                        } else 0
                        
                        if (iconRes != 0) {
                            Image(
                                painter = painterResource(iconRes),
                                contentDescription = "Selected Icon",
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(if (selectedIcon == null) "Select an icon" else "Change icon")
                    }

                    if (showIconPickerForCreate) {
                        AlertDialog(
                            onDismissRequest = { showIconPickerForCreate = false },
                            title = { Text("Select Icon") },
                            text = {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    modifier = Modifier.height(300.dp),
                                    contentPadding = PaddingValues(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(rawIcons) { iconName ->
                                        val resId = context.resources.getIdentifier(iconName, "raw", context.packageName)
                                        if (resId != 0) {
                                            IconButton(onClick = {
                                                selectedIcon = iconName
                                                showIconPickerForCreate = false
                                            }) {
                                                Image(
                                                    painter = painterResource(resId),
                                                    contentDescription = iconName,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showIconPickerForCreate = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        groupRepository.createGroup(name, selectedIcon)
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