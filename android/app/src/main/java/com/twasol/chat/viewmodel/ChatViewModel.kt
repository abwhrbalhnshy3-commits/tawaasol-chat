package com.twasol.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twasol.chat.model.Message
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.*

class ChatViewModel : ViewModel() {

    private var socket: Socket? = null
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private var currentUserId: String = ""
    private var recipientId: String = ""
    private var jwtToken: String? = null

    // استدعِ login ثم connect
    fun loginAndConnect(userId: String, otherId: String, onResult: (Boolean, String?) -> Unit) {
        currentUserId = userId
        recipientId = otherId

        val client = OkHttpClient()
        val json = JSONObject().apply { put("userId", userId) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("http://10.0.2.2:3000/auth/login")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        onResult(false, "login failed: ${resp.code}")
                        return@use
                    }
                    val respBody = resp.body?.string() ?: ""
                    val obj = JSONObject(respBody)
                    jwtToken = obj.optString("token", null)
                    if (jwtToken == null) {
                        onResult(false, "no token returned")
                        return@use
                    }

                    // بعد الحصول على التوكن: سجل token جهاز FCM عند الحاجة
                    registerTokenToServer(userId, jwtToken!!)

                    // ثم اتصل بالـ socket مع تمرير التوكن كـ query (متوافق مع socket.io-client v2)
                    connectToServerWithToken(jwtToken!!)

                    onResult(true, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.message)
            }
        }.start()
    }

    private fun connectToServerWithToken(token: String) {
        try {
            val opts = IO.Options()
            // socket.io-client v2 uses query string; نمرر token كـ query
            opts.query = "token=$token"
            socket = IO.socket("http://10.0.2.2:3000", opts)
            socket?.connect()

            socket?.on("receive_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0]
                    val json = when (data) {
                        is JSONObject -> data.toString()
                        else -> data.toString()
                    }
                    val temp = Gson().fromJson(json, Message::class.java)
                    val ts = parseTimestamp((args[0] as? JSONObject)?.opt("timestamp"))
                    val message = temp.copy(timestamp = ts)
                    viewModelScope.launch(Dispatchers.Main) {
                        _messages.value = _messages.value + message
                    }
                }
            }

            socket?.on("message_sent") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0].toString()
                    val saved = Gson().fromJson(data, Message::class.java)
                    val ts = parseTimestamp((JSONObject(data)).opt("timestamp"))
                    val message = saved.copy(timestamp = ts)
                    viewModelScope.launch(Dispatchers.Main) {
                        _messages.value = _messages.value + message
                    }
                }
            }

            socket?.on(Socket.EVENT_RECONNECT) {
                socket?.emit("join_room", currentUserId)
            }

            // انضم للغرفة باسم المستخدم بعد الاتصال
            socket?.on(Socket.EVENT_CONNECT) {
                socket?.emit("join_room", currentUserId)
            }

        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    private fun parseTimestamp(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> {
                val s = value
                return try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    sdf.parse(s)?.time ?: s.toLongOrNull() ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    s.toLongOrNull() ?: System.currentTimeMillis()
                }
            }
            else -> System.currentTimeMillis()
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || socket == null) return

        val message = Message(
            senderId = currentUserId,
            receiverId = recipientId,
            content = content,
            timestamp = System.currentTimeMillis()
        )

        val jsonObject = JSONObject().apply {
            put("senderId", message.senderId)
            put("receiverId", message.receiverId)
            put("content", message.content)
            put("timestamp", message.timestamp)
        }

        socket?.emit("send_message", jsonObject)

        viewModelScope.launch {
            _messages.value = _messages.value + message
        }
    }

    fun registerTokenToServer(userId: String, token: String) {
        // تأكد من أن jwtToken موجودة
        val jwt = jwtToken ?: return
        FirebaseTokenRegistrar.registerToken(userId, jwt)
    }

    override fun onCleared() {
        super.onCleared()
        socket?.disconnect()
        socket?.off()
    }
}
