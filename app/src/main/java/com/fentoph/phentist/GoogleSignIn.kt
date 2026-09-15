package com.fentoph.phentist

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSignIn(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(activity: Activity): Result<GoogleIdentity> = withContext(Dispatchers.Main) {
        runCatching {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(context.getString(R.string.google_sign_in_server_client_id))
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)

            GoogleIdentity(
                idToken = googleCredential.idToken,
                subject = googleCredential.id,
                email = googleCredential.id,
                displayName = googleCredential.displayName
            )
        }.recoverCatching { error ->
            when (error) {
                is GetCredentialCancellationException -> throw AuthException("Google sign-in was cancelled")
                is NoCredentialException -> throw AuthException("No Google account is available on this device")
                is GoogleIdTokenParsingException -> throw AuthException("Google credential could not be verified by the client")
                else -> throw error
            }
        }
    }
}

data class GoogleIdentity(
    val idToken: String,
    val subject: String,
    val email: String,
    val displayName: String?
)

class AuthException(message: String) : Exception(message)
