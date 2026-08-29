package com.tawaasol.chat.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface JokeApi {
    @GET("/")
    suspend fun randomJoke(@Header("Accept") accept: String = "application/json"): Response<JokeDto>
}

data class JokeDto(val id: String?, val joke: String?)
