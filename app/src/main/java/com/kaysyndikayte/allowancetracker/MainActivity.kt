package com.kaysyndikayte.allowancetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaysyndikayte.allowancetracker.userinterface.AppNavHost
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AllowanceTrackerApp()
        }
    }
}

@Composable
fun AllowanceTrackerApp() {
    MaterialTheme {
        Surface {
            val viewModel = viewModel<AllowanceViewModel>(
                factory = AllowanceViewModelFactory(LocalContext.current)
            )
            AppNavHost(viewModel)
        }
    }
}