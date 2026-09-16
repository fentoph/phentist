package com.fentoph.phentist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object PhentistApi {
    private const val BASE_URL = "https://phentist.onrender.com"

    data class SessionResult(val accessToken: String, val userId: String, val email: String, val displayName: String?, val isAdmin: Boolean)
    data class PaymentResult(val id: String, val status: String)
    data class University(val id: String, val name: String, val location: String, val imageUrl: String?)
    data class Essay(val id: String, val universityId: String, val question: String, val wordLimit: Int, val previewText: String, val fullText: String, val authorName: String, val authorAvatarUrl: String?, val priceMinor: Long, val currency: String)

    suspend fun signInWithGoogle(idToken: String): Result<SessionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(request("/v1/auth/google", "POST", JSONObject().put("idToken", idToken).toString()))
            val user = json.getJSONObject("user")
            SessionResult(json.getString("accessToken"), user.getString("id"), user.getString("email"), if (user.isNull("displayName")) null else user.optString("displayName"), user.optBoolean("isAdmin"))
        }
    }

    suspend fun listUniversities(search: String = ""): Result<List<University>> = withContext(Dispatchers.IO) {
        runCatching {
            val suffix = if (search.isBlank()) "" else "?search=${URLEncoder.encode(search, Charsets.UTF_8.name())}"
            val json = JSONObject(request("/v1/universities$suffix", "GET", "{}"))
            val array = json.getJSONArray("universities")
            buildList { for (i in 0 until array.length()) { val u=array.getJSONObject(i); add(University(u.getString("id"),u.getString("name"),u.getString("location"),if(u.isNull("image_url")) null else u.optString("image_url"))) } }
        }
    }

    suspend fun listEssays(universityId: String): Result<List<Essay>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(request("/v1/universities/$universityId/essays", "GET", "{}"))
            val array = json.getJSONArray("essays")
            buildList {
                for (i in 0 until array.length()) {
                    val e=array.getJSONObject(i)
                    add(Essay(e.getString("id"),e.getString("university_id"),e.getString("question"),e.optInt("word_limit",650),e.optString("preview_text"),e.optString("full_text"),e.optString("author_name","Phentist"),if(e.isNull("author_avatar_url")) null else e.optString("author_avatar_url"),e.optLong("price_minor",25000),e.optString("currency","UZS")))
                }
            }
        }
    }

    suspend fun addUniversity(accessToken: String, name: String, location: String, imageUrl: String): Result<University> = withContext(Dispatchers.IO) {
        runCatching {
            val body=JSONObject().put("name",name).put("location",location).put("imageUrl",imageUrl)
            val u=JSONObject(request("/v1/universities","POST",body.toString(),accessToken)).getJSONObject("university")
            University(u.getString("id"),u.getString("name"),u.getString("location"),if(u.isNull("image_url"))null else u.optString("image_url"))
        }
    }

    suspend fun addEssay(accessToken: String, universityId: String, question: String, wordLimit: Int, previewText: String, fullText: String, authorName: String, authorAvatarUrl: String, priceMinor: Long): Result<Essay> = withContext(Dispatchers.IO) {
        runCatching {
            val body=JSONObject().put("universityId",universityId).put("question",question).put("wordLimit",wordLimit).put("previewText",previewText).put("authorName",authorName).put("authorAvatarUrl",authorAvatarUrl).put("fullText",fullText).put("priceMinor",priceMinor).put("currency","UZS")
            val e=JSONObject(request("/v1/university-essays","POST",body.toString(),accessToken)).getJSONObject("essay")
            Essay(e.getString("id"),e.getString("university_id"),e.getString("question"),e.optInt("word_limit",650),e.optString("preview_text"),e.optString("full_text"),e.optString("author_name","Phentist"),if(e.isNull("author_avatar_url"))null else e.optString("author_avatar_url"),e.optLong("price_minor",25000),e.optString("currency","UZS"))
        }
    }

    suspend fun removeEssay(accessToken: String, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { request("/v1/university-essays/$id", "DELETE", "{}", accessToken); Unit }
    }

    suspend fun removeUniversity(accessToken: String, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { request("/v1/universities/$id", "DELETE", "{}", accessToken); Unit }
    }

    suspend fun submitPayment(accessToken: String,name: String,surname: String,location: String,phone: String,amountMinor: Long?=null,contentId: String?=null,note: String?=null): Result<PaymentResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body=JSONObject().put("name",name).put("surname",surname).put("location",location).put("phone",phone).put("serviceEmail","aslbekqoziboyev536@gmail.com").put("currency","UZS")
            if(amountMinor!=null)body.put("amountMinor",amountMinor); if(contentId!=null)body.put("contentId",contentId); if(!note.isNullOrBlank())body.put("note",note)
            val payment=JSONObject(request("/payments","POST",body.toString(),accessToken)).getJSONObject("payment")
            PaymentResult(payment.getString("id"),payment.getString("status"))
        }
    }

    private fun request(path:String,method:String,body:String,bearer:String?=null):String {
        val connection=(URL(BASE_URL+path).openConnection() as HttpURLConnection).apply { requestMethod=method; connectTimeout=15_000; readTimeout=20_000; doInput=true; setRequestProperty("Accept","application/json"); setRequestProperty("Content-Type","application/json"); bearer?.let{setRequestProperty("Authorization","Bearer $it")}; doOutput=method!="GET" }
        try { if(method!="GET")connection.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}; val status=connection.responseCode; val stream=if(status in 200..299)connection.inputStream else connection.errorStream; val response=stream?.bufferedReader()?.use{it.readText()}.orEmpty(); if(status !in 200..299){val message=runCatching{JSONObject(response).optString("error")}.getOrDefault("request_failed");throw IllegalStateException("$message (HTTP $status)")}; return response } finally { connection.disconnect() }
    }
}
