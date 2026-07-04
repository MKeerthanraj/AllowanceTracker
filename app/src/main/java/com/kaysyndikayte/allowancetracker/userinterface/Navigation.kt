package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel

object Routes {
    const val HOME = "home"
    const val ANALYTICS = "analytics"
    const val HISTORY = "history"
}

@Composable
fun AppNavHost(viewModel: AllowanceViewModel) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenAnalytics = { navController.navigate(Routes.ANALYTICS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) }
            )
        }
        composable(Routes.ANALYTICS) {
            AnalyticsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSelectRange = { navController.popBackStack(Routes.HOME, false) }
            )
        }
    }
}