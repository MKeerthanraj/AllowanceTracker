package com.kaysyndikayte.allowancetracker.repository

import com.kaysyndikayte.allowancetracker.data.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

class AuthRepository {
    private val auth = SupabaseClientProvider.client.auth

    val sessionStatus: StateFlow<SessionStatus> = auth.sessionStatus

    suspend fun signUp(email: String, password: String, displayName: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = kotlinx.serialization.json.buildJsonObject {
                put("display_name", kotlinx.serialization.json.JsonPrimitive(displayName))
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    fun currentUserId(): String? = auth.currentUserOrNull()?.id
}