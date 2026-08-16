package com.kaysyndikayte.allowancetracker.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kaysyndikayte.allowancetracker.userinterface.*
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel
import com.kaysyndikayte.allowancetracker.viewmodel.PendingExpenseViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

object Routes {
    const val HOME = "home"
    const val ANALYTICS = "analytics"
    const val HISTORY = "history"
    const val GROUPS = "groups"
    const val GROUP_DETAIL = "group_detail/{groupId}"
    const val ADD_EXPENSE = "add_expense/{groupId}"
    const val SPLIT_CONFIG = "split_config"
    const val RECEIPT_CAPTURE = "receipt_capture/{groupId}"
    fun receiptCapture(groupId: String) = "receipt_capture/$groupId"
    fun groupDetail(groupId: String) = "group_detail/$groupId"
    fun addExpense(groupId: String) = "add_expense/$groupId"
}

@Composable
fun AppNavHost(
    viewModel: AllowanceViewModel,
    sharedImageUri: Uri? = null,
    onImageConsumed: () -> Unit = {}
) {
    val navController: NavHostController = rememberNavController()
    val pendingExpenseViewModel: PendingExpenseViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            MainScreen(
                viewModel = viewModel,
                sharedImageUri = sharedImageUri,
                onImageConsumed = onImageConsumed,
                onOpenAnalytics = { navController.navigate(Routes.ANALYTICS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenGroup = { groupId -> navController.navigate(Routes.groupDetail(groupId)) }
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
        composable(
            Routes.GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupDetailScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onAddExpense = { gid -> navController.navigate(Routes.addExpense(gid)) }
            )
        }
        composable(
            Routes.ADD_EXPENSE,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            AddExpenseScreenHost(
                groupId = groupId,
                pendingExpenseViewModel = pendingExpenseViewModel,
                onGoToSplitConfig = { navController.navigate(Routes.SPLIT_CONFIG) },
                onGoToReceiptCapture = { navController.navigate(Routes.receiptCapture(groupId)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SPLIT_CONFIG) {
            SplitConfigScreenHost(
                pendingExpenseViewModel = pendingExpenseViewModel,
                onDone = { navController.popBackStack(Routes.GROUP_DETAIL, inclusive = false) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.RECEIPT_CAPTURE,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            ReceiptFlowHost(
                groupId = groupId,
                pendingExpenseViewModel = pendingExpenseViewModel,
                onDone = { navController.popBackStack(Routes.GROUP_DETAIL, inclusive = false) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}