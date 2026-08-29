package com.tawaasol.chat.websocket

import okhttp3.*
import okio.ByteString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class WebSocketManager(private val tokenProvider: () -> String?) {
    private var ws: WebSocket? = null
    private val client = OkHttpClient()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private var reconnectAttempts = AtomicInteger(0)
    private var connected = false

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
                GlobalScope.launch(Dispatchers.Default) { _events.emit(text) }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { connected = false; attemptReconnect(conversationId) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { connected = false; attemptReconnect(conversationId) }
        })
    }

    private fun attemptReconnect(conversationId: Int?) {
        val attempt = reconnectAttempts.incrementAndGet()
        val delayMs = (Math.min(30, 1 shl attempt)).toLong() * 1000L
        GlobalScope.launch(Dispatchers.Default) {
            delay(delayMs)
            connect(conversationId)
        }
    }

    fun send(text: String) { if (connected) ws?.send(text) else { /* queueing could be implemented */ } }

    fun close() { ws?.close(1000, "bye") }
}
