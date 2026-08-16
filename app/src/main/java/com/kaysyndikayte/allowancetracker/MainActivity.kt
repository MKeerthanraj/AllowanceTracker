package com.kaysyndikayte.allowancetracker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaysyndikayte.allowancetracker.ui.AppNavHost
import com.kaysyndikayte.allowancetracker.userinterface.AuthScreen
import com.kaysyndikayte.allowancetracker.repository.AuthRepository
import com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModelFactory
import io.github.jan.supabase.auth.status.SessionStatus

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
            // AuthRepository is cheap to construct (wraps the Supabase client singleton),
            // remember{} avoids recreating it on every recomposition.
            val authRepository = remember { AuthRepository() }
            val sessionStatus by authRepository.sessionStatus.collectAsState()

            when (val status = sessionStatus) {
                is SessionStatus.Authenticated -> {
                    val viewModel = viewModel<com.kaysyndikayte.allowancetracker.viewmodel.AllowanceViewModel>(
                        factory = AllowanceViewModelFactory()
                    )
                    AppNavHost(viewModel = viewModel, sharedImageUri = sharedImageUri, onImageConsumed = onImageConsumed)
                }

                is SessionStatus.NotAuthenticated -> {
                    AuthScreen(authRepository = authRepository, onAuthSuccess = { /* sessionStatus updates automatically, recomposition handles the switch */ })
                }

                is SessionStatus.Initializing -> {
                    // Supabase is checking for a cached session on disk — brief loading state
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is SessionStatus.RefreshFailure -> {
                    // Cached session expired/invalid — treat as logged out
                    AuthScreen(authRepository = authRepository, onAuthSuccess = { })
                }
            }
        }
    }
}