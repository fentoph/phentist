package com.fentoph.phentist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PhentistApi {
    private const val BASE_URL = "https://phentist.onrender.com"

    data class SessionResult(
        val accessToken: String,
        val userId: String,
        val email: String,
        val displayName: String?,
        val isAdmin: Boolean
    )

    data class PaymentResult(val id: String, val status: String)

    suspend fun signInWithGoogle(idToken: String): Result<SessionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request(
                path = "/v1/auth/google",
                method = "POST",
                body = JSONObject().put("idToken", idToken).toString()
            )
            val json = JSONObject(response)
            val user = json.getJSONObject("user")
            SessionResult(
                accessToken = json.getString("accessToken"),
                userId = user.getString("id"),
                email = user.getString("email"),
                displayName = if (user.isNull("displayName")) null else user.optString("displayName"),
                isAdmin = user.optBoolean("isAdmin", false)
            )
        }
    }

    suspend fun submitPayment(
        accessToken: String,
        name: String,
        surname: String,
        location: String,
        phone: String,
        amountMinor: Long? = null,
        contentId: String? = null,
        note: String? = null
    ): Result<PaymentResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("name", name)
                .put("surname", surname)
                .put("location", location)
                .put("phone", phone)
                .put("serviceEmail", "aslbekqoziboyev536@gmail.com")
                .put("currency", "UZS")
            if (amountMinor != null) body.put("amountMinor", amountMinor)
            if (contentId != null) body.put("contentId", contentId)
            if (!note.isNullOrBlank()) body.put("note", note)

            val response = request("/payments", "POST", body.toString(), accessToken)
            val payment = JSONObject(response).getJSONObject("payment")
            PaymentResult(payment.getString("id"), payment.getString("status"))
        }
    }

    private fun request(path: String, method: String, body: String, bearer: String? = null): String {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            doOutput = true
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("error") }.getOrDefault("request_failed")
                throw IllegalStateException("$message (HTTP $status)")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
