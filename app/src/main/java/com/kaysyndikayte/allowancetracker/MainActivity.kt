package com.kaysyndikayte.allowancetracker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaysyndikayte.allowancetracker.ui.AppNavHost
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModelFactory

class MainActivity : ComponentActivity() {

    private var sharedImageUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        setContent {
            AllowanceTrackerApp(sharedImageUri = sharedImageUri, onImageConsumed = { sharedImageUri = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            android.util.Log.d("MainActivity", "Received share intent, uri=$uri, type=${intent.type}")
            sharedImageUri = uri
        } else {
            android.util.Log.d("MainActivity", "handleIncomingIntent called but action/type didn't match: action=${intent?.action}, type=${intent?.type}")
        }
    }
}

@Composable
fun AllowanceTrackerApp(sharedImageUri: Uri?, onImageConsumed: () -> Unit) {
    MaterialTheme {
        Surface {
            val viewModel = viewModel<com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel>(
                factory = AllowanceViewModelFactory(androidx.compose.ui.platform.LocalContext.current)
            )
            AppNavHost(viewModel = viewModel, sharedImageUri = sharedImageUri, onImageConsumed = onImageConsumed)
        }
    }
}