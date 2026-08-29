package com.tawaasol.chat.auth

import com.tawaasol.chat.api.ChatApi
import com.tawaasol.chat.datastore.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import retrofit2.create

class AuthRepository(private val dataStore: DataStoreManager) {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }
    private val api by lazy { retrofit.create<AuthApi>() }

    suspend fun requestOtp(phone: String) = withContext(Dispatchers.IO) { api.requestOtp(RequestOtp(phone)) }

    suspend fun verifyOtp(phone: String, otp: String, name: String?) = withContext(Dispatchers.IO) {
        val resp = api.verifyOtp(VerifyOtp(phone, otp, name))
        if (resp.isSuccessful) {
            val body = resp.body()
            val token = body?.get("token")
            val refresh = body?.get("refreshToken")
            if (token != null) dataStore.saveToken(token)
            if (refresh != null) dataStore.saveRefresh(refresh)
        }
        resp
    }

    suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        val refresh = dataStore.getRefresh() ?: return@withContext false
        val r = retrofit.create<AuthApi>().refresh(mapOf("refreshToken" to refresh))
        if (r.isSuccessful) {
            val token = r.body()?.get("token")
            if (token != null) { dataStore.saveToken(token); return@withContext true }
        }
        return@withContext false
    }
}
