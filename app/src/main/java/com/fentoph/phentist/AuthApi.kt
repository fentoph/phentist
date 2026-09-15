package com.fentoph.phentist

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AuthApi(private val context: Context) {
    private val baseUrl: String
        get() = context.getString(R.string.backend_base_url).trimEnd('/')

    suspend fun verifyGoogleIdToken(idToken: String): VerifiedSession = withContext(Dispatchers.IO) {
        require(baseUrl.startsWith("https://")) { "Phentist backend must use HTTPS" }

        val connection = (URL("$baseUrl/v1/auth/google").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val body = JSONObject().put("idToken", idToken).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw AuthException("Authentication failed ($status)")
            }

            val json = JSONObject(response)
            VerifiedSession(
                accessToken = json.getString("accessToken"),
                email = json.getString("email"),
                displayName = json.optString("displayName").ifBlank { null },
                isAdmin = json.optBoolean("isAdmin", false)
            )
        } finally {
            connection.disconnect()
        }
    }
}

data class VerifiedSession(
    val accessToken: String,
    val email: String,
    val displayName: String?,
    val isAdmin: Boolean
)
