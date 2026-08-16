package com.kaysyndikayte.allowancetracker.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaysyndikayte.allowancetracker.repository.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(authRepository: AuthRepository, onAuthSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (isSignUp) "Create account" else "Log in", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (isSignUp) {
            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }

        Button(onClick = {
            scope.launch {
                try {
                    if (isSignUp) authRepository.signUp(email, password, displayName)
                    else authRepository.signIn(email, password)
                    onAuthSuccess()
                } catch (e: Exception) {
                    errorMsg = e.message
                }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSignUp) "Sign up" else "Log in")
        }

        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if (isSignUp) "Already have an account? Log in" else "New here? Sign up")
        }
    }
}