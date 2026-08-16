package com.kaysyndikayte.allowancetracker.userinterface

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel

private const val TAB_ALLOWANCE = 0
private const val TAB_GROUPS = 1

/**
 * Home is two peers now -- personal allowance and shared groups -- rather than groups being
 * buried behind an icon in an already-crowded app bar. Each tab keeps its own Scaffold and
 * top bar; this only owns which one is showing.
 */
@Composable
fun MainScreen(
    viewModel: AllowanceViewModel,
    sharedImageUri: Uri?,
    onImageConsumed: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGroup: (String) -> Unit
) {
    // rememberSaveable so the selected tab survives rotation and coming back from a group.
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_ALLOWANCE) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                TAB_ALLOWANCE -> HomeScreen(
                    viewModel = viewModel,
                    sharedImageUri = sharedImageUri,
                    onImageConsumed = onImageConsumed,
                    onOpenAnalytics = onOpenAnalytics,
                    onOpenHistory = onOpenHistory
                )

                else -> GroupsScreen(onOpenGroup = onOpenGroup)
            }
        }

        NavigationBar {
            NavigationBarItem(
                selected = selectedTab == TAB_ALLOWANCE,
                onClick = { selectedTab = TAB_ALLOWANCE },
                icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
                label = { Text("Allowance") }
            )
            NavigationBarItem(
                selected = selectedTab == TAB_GROUPS,
                onClick = { selectedTab = TAB_GROUPS },
                icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                label = { Text("Groups") }
            )
        }
    }
}
