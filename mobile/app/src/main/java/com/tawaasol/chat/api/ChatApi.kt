package com.tawaasol.chat.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class CreateConversationRequest(val title: String)
data class RegisterRequest(val username: String, val phone: String)
data class SendMessageRequest(val senderId: Int, val content: String)

data class ConversationDto(val id: Int, val title: String)

data class MessageDto(val id: Int, val conversationId: Int, val senderId: Int, val content: String, val timestamp: String)

interface ChatApi {
    @POST("/api/register")
    suspend fun register(@Body req: RegisterRequest): Response<Map<String, Int>>

    @GET("/api/conversations")
    suspend fun listConversations(): Response<List<ConversationDto>>

    @POST("/api/conversations")
    suspend fun createConversation(@Body req: CreateConversationRequest): Response<Map<String, Int>>

    @GET("/api/conversations/{id}/messages")
    suspend fun getMessages(@Path("id") id: Int): Response<List<MessageDto>>

    @POST("/api/conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") id: Int, @Body req: SendMessageRequest): Response<Map<String, Int>>
}
