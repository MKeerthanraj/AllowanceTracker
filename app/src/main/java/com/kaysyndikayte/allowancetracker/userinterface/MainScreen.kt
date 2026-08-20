package com.kaysyndikayte.allowancetracker.userinterface

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel

/**
 * Home is two peers -- personal allowance and shared groups -- rather than groups being buried
 * behind an icon in an already-crowded app bar. Each tab keeps its own Scaffold and top bar;
 * this only owns which one is showing.
 *
 * The choice is persisted (see HomeTabPreferences), so leaving the app on Groups and coming
 * back later lands on Groups.
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
    val tabViewModel = viewModel<HomeTabViewModel>(
        factory = HomeTabViewModel.Factory(
            LocalContext.current.applicationContext as Application
        )
    )
    val storedTab by tabViewModel.selectedTab.collectAsState()

    // An image shared in from another app is only picked up by HomeScreen, so it outranks the
    // remembered tab. Without this, sharing a receipt while Groups was the last tab used did
    // nothing visible, and the receipt flow then ambushed the user the next time they happened
    // to tap Allowance.
    val tab = if (sharedImageUri != null) HomeTab.ALLOWANCE else storedTab

    // null while the stored tab is still being read off disk -- see HomeTabViewModel.
    if (tab == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                HomeTab.ALLOWANCE -> HomeScreen(
                    viewModel = viewModel,
                    sharedImageUri = sharedImageUri,
                    onImageConsumed = onImageConsumed,
                    onOpenAnalytics = onOpenAnalytics,
                    onOpenHistory = onOpenHistory
                )

                HomeTab.GROUPS -> GroupsScreen(onOpenGroup = onOpenGroup)
            }
        }

        NavigationBar {
            NavigationBarItem(
                selected = tab == HomeTab.ALLOWANCE,
                onClick = { tabViewModel.select(HomeTab.ALLOWANCE) },
                icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
                label = { Text("Allowance") }
            )
            NavigationBarItem(
                selected = tab == HomeTab.GROUPS,
                onClick = { tabViewModel.select(HomeTab.GROUPS) },
                icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                label = { Text("Groups") }
            )
        }
    }
}
