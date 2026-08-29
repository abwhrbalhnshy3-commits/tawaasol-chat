package com.tawaasol.chat.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("/api/auth/request-otp")
    suspend fun requestOtp(@Body req: RequestOtp): Response<Map<String, Boolean>>

    @POST("/api/auth/verify-otp")
    suspend fun verifyOtp(@Body req: VerifyOtp): Response<Map<String, String>>

    @POST("/api/auth/refresh")
    suspend fun refresh(@Body body: Map<String, String>): Response<Map<String, String>>
}
