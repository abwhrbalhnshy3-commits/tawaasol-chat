package com.tawaasol.chat.websocket

import okhttp3.*
import okio.ByteString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WebSocketManager(private val tokenProvider: () -> String?) {
    private var ws: WebSocket? = null
    private val client = OkHttpClient()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun connect() {
        val token = tokenProvider() ?: return
        val req = Request.Builder().url("ws://10.0.2.2:8080/ws?token=$token").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    _events.emit(text)
                }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {}
        })
    }

    fun send(text: String) {
        ws?.send(text)
    }

    fun close() {
        ws?.close(1000, "bye")
    }
}
