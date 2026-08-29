package com.twasol.chat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object FirebaseTokenRegistrar {
    private val client = OkHttpClient()

    fun registerToken(userId: String, jwtToken: String) {
        // نحصل على token من Firebase Messaging (يفترض أن تم توليده مسبقًا في FirebaseMessagingService)
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val fcmToken = task.result ?: return@addOnCompleteListener

            val json = JSONObject().apply {
                put("token", fcmToken)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("http://10.0.2.2:3000/register-token")
                .addHeader("Authorization", "Bearer $jwtToken")
                .post(body)
                .build()

            Thread {
                try {
                    client.newCall(request).execute().use { resp ->
                        // تحقق من الاستجابة أو افعل لوج
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }
}
