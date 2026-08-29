package com.tawaasol.chat.websocket

import okhttp3.*
import okio.ByteString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WebSocketManager(private val tokenProvider: () -> String?) {
    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(java.time.Duration.ofSeconds(15))
        .build()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private var reconnectAttempts = AtomicInteger(0)
    private var connected = false

    // CoroutineScope tied to this manager; caller should cancel when no longer needed
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun connect(conversationId: Int? = null) {
        val token = tokenProvider() ?: return
        val req = Request.Builder().url("ws://10.0.2.2:8080/ws?token=$token").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts.set(0)
                connected = true
                // join conversation if provided
                conversationId?.let { val json = "{\"type\":\"join\",\"conversationId\":$it}"; webSocket.send(json) }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { _events.emit(text) }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { connected = false; attemptReconnect(conversationId) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { connected = false; attemptReconnect(conversationId) }
        })
    }

    private fun attemptReconnect(conversationId: Int?) {
        val attempt = reconnectAttempts.incrementAndGet()
        val delayMs = (kotlin.math.min(30, 1 shl attempt)).toLong() * 1000L
        scope.launch {
            delay(delayMs)
            connect(conversationId)
        }
    }

    fun send(text: String) {
        if (connected) ws?.send(text) else { /* optionally implement queueing */ }
    }

    fun close() {
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        scope.cancel()
    }

    // non-blocking typed send (uses coroutine scope)
    fun sendTyping(conversationId: Int, state: String) {
        scope.launch {
            try {
                val json = buildJsonObject {
                    put("type", "typing")
                    put("conversationId", conversationId)
                    put("state", state)
                }.toString()
                ws?.send(json)
            } catch (e: Exception) {
                // optional logging
            }
        }
    }
}
