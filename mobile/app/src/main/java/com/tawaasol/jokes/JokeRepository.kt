package com.tawaasol.jokes

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object JokeRepository {
    private val client = OkHttpClient()

    suspend fun fetchRandomJoke(): String? {
        return try {
            val request = Request.Builder()
                .url("https://icanhazdadjoke.com/")
                .header("Accept", "application/json")
                .header("User-Agent", "Tawaasol-Jokes-App")
                .build()
            val resp = client.newCall(request).execute()
            val body = resp.body?.string()
            if (!resp.isSuccessful || body == null) return null
            val json = JSONObject(body)
            json.optString("joke", null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
