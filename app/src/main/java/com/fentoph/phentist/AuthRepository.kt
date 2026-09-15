package com.fentoph.phentist

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "phentist_auth")

/**
 * Client-side auth state only. The backend must verify the Google ID token and
 * derive the user's identity from Google's verified claims. Never trust an
 * email address supplied by the client as proof of identity.
 */
class AuthRepository(private val context: Context) {
    private val signedInKey = booleanPreferencesKey("signed_in")
    private val emailKey = stringPreferencesKey("email")
    private val nameKey = stringPreferencesKey("name")
    private val subjectKey = stringPreferencesKey("google_subject")

    val session: Flow<AuthSession> = context.authDataStore.data.map { prefs ->
        AuthSession(
            signedIn = prefs[signedInKey] ?: false,
            email = prefs[emailKey],
            displayName = prefs[nameKey],
            googleSubject = prefs[subjectKey]
        )
    }

    suspend fun saveVerifiedGoogleIdentity(
        googleSubject: String,
        email: String,
        displayName: String?
    ) {
        context.authDataStore.edit { prefs ->
            prefs[signedInKey] = true
            prefs[emailKey] = email
            displayName?.let { prefs[nameKey] = it }
            prefs[subjectKey] = googleSubject
        }
    }

    suspend fun signOut() {
        context.authDataStore.edit { it.clear() }
    }
}

data class AuthSession(
    val signedIn: Boolean,
    val email: String?,
    val displayName: String?,
    val googleSubject: String?
)

object AdminPolicy {
    const val ADMIN_EMAIL = "aslbekqoziboyev536@gmail.com"

    fun isAdmin(verifiedEmail: String): Boolean =
        verifiedEmail.equals(ADMIN_EMAIL, ignoreCase = true)
}
