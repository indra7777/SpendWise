package com.rupeelog.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.rupeelog.data.remote.SupabaseConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: Auth
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val credentialManager = CredentialManager.create(context)

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        val currentUser = auth.currentUserOrNull()
        _authState.value = if (currentUser != null) {
            AuthState.Authenticated(
                userId = currentUser.id,
                email = currentUser.email ?: "",
                name = currentUser.userMetadata?.get("full_name")?.toString()?.removeSurrounding("\"") ?: "",
                avatarUrl = currentUser.userMetadata?.get("avatar_url")?.toString()?.removeSurrounding("\"")
            )
        } else {
            AuthState.NotAuthenticated
        }
    }

    // Store raw nonce for Supabase verification
    private var currentRawNonce: String? = null

    suspend fun signInWithGoogle(activityContext: Context): Result<UserInfo> {
        return try {
            android.util.Log.d("AuthRepository", "Starting Google Sign-In")
            android.util.Log.d("AuthRepository", "Web Client ID: ${SupabaseConfig.GOOGLE_WEB_CLIENT_ID}")

            // Generate nonce: raw for Supabase, hashed for Google
            val rawNonce = UUID.randomUUID().toString()
            val hashedNonce = hashNonce(rawNonce)
            currentRawNonce = rawNonce

            android.util.Log.d("AuthRepository", "Generated nonce (first 8 chars): ${rawNonce.take(8)}...")

            // First try with authorized accounts only (faster if user has signed in before)
            val resultFromAuthorized = tryGetCredential(
                activityContext = activityContext,
                hashedNonce = hashedNonce,
                filterByAuthorizedAccounts = true
            )

            if (resultFromAuthorized.isSuccess) {
                return handleSignInResult(resultFromAuthorized.getOrThrow())
            }

            android.util.Log.d("AuthRepository", "No authorized accounts, trying all accounts...")

            // Fallback: show all Google accounts
            val resultFromAll = tryGetCredential(
                activityContext = activityContext,
                hashedNonce = hashedNonce,
                filterByAuthorizedAccounts = false
            )

            handleSignInResult(resultFromAll.getOrThrow())

        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            android.util.Log.e("AuthRepository", "User cancelled sign-in", e)
            _authState.value = AuthState.Error("Sign-in cancelled")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            android.util.Log.e("AuthRepository", "GetCredentialException: ${e.type} - ${e.message}", e)
            _authState.value = AuthState.Error("Sign-in failed: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Unexpected error: ${e.javaClass.simpleName} - ${e.message}", e)
            _authState.value = AuthState.Error(e.message ?: "Sign-in failed")
            Result.failure(e)
        }
    }

    private suspend fun tryGetCredential(
        activityContext: Context,
        hashedNonce: String,
        filterByAuthorizedAccounts: Boolean
    ): Result<GetCredentialResponse> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                .setServerClientId(SupabaseConfig.GOOGLE_WEB_CLIENT_ID)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            android.util.Log.d("AuthRepository", "Credential received (filterByAuthorized=$filterByAuthorizedAccounts)")
            Result.success(result)
        } catch (e: NoCredentialException) {
            android.util.Log.d("AuthRepository", "No credential found (filterByAuthorized=$filterByAuthorizedAccounts)")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            android.util.Log.e("AuthRepository", "GetCredentialException (filterByAuthorized=$filterByAuthorizedAccounts): ${e.type}")
            Result.failure(e)
        }
    }

    private fun hashNonce(rawNonce: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(rawNonce.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private suspend fun handleSignInResult(result: GetCredentialResponse): Result<UserInfo> {
        val credential = result.credential

        return when {
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    android.util.Log.d("AuthRepository", "Got ID token, signing in to Supabase...")

                    // Sign in to Supabase with the Google ID token
                    // Pass the RAW (unhashed) nonce - Supabase will hash it to verify
                    auth.signInWith(IDToken) {
                        this.idToken = idToken
                        this.provider = Google
                        this.nonce = currentRawNonce
                    }

                    val user = auth.currentUserOrNull()
                    if (user != null) {
                        _authState.value = AuthState.Authenticated(
                            userId = user.id,
                            email = user.email ?: "",
                            name = user.userMetadata?.get("full_name")?.toString()?.removeSurrounding("\"") ?: "",
                            avatarUrl = user.userMetadata?.get("avatar_url")?.toString()?.removeSurrounding("\"")
                        )
                        Result.success(user)
                    } else {
                        Result.failure(Exception("Failed to get user after sign-in"))
                    }
                } catch (e: GoogleIdTokenParsingException) {
                    _authState.value = AuthState.Error("Invalid Google token")
                    Result.failure(e)
                }
            }
            else -> {
                _authState.value = AuthState.Error("Unexpected credential type")
                Result.failure(Exception("Unexpected credential type"))
            }
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
            _authState.value = AuthState.NotAuthenticated
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Sign-out failed")
        }
    }

    fun getCurrentUser() = auth.currentUserOrNull()

    fun isAuthenticated() = auth.currentUserOrNull() != null
}

sealed class AuthState {
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    data class Authenticated(
        val userId: String,
        val email: String,
        val name: String,
        val avatarUrl: String?
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}
