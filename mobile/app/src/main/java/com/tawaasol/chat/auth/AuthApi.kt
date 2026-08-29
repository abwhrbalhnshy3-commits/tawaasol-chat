package com.tawaasol.chat.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class RequestOtp(val phone: String)
data class VerifyOtp(val phone: String, val otp: String, val name: String?)

interface AuthApi {
    @POST("/api/auth/request-otp")
    suspend fun requestOtp(@Body req: RequestOtp): Response<Map<String, Boolean>>

    @POST("/api/auth/verify-otp")
    suspend fun verifyOtp(@Body req: VerifyOtp): Response<Map<String, String>>
}
