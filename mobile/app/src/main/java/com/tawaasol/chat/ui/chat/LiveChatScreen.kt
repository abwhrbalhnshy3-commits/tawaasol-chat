package com.tawaasol.chat.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tawaasol.chat.websocket.WebSocketManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun LiveChatScreen(webSocketManager: WebSocketManager, conversationId: Int = 1) {
    var input by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Disconnected") }

    LaunchedEffect(Unit) {
        webSocketManager.connect(conversationId)
        launch {
            webSocketManager.events.collect { text ->
                // simple parsing: show message text
                messages.add(text)
                // update status on join/ack
                if (text.contains("\"type\":\"joined\"")) status = "Joined"
                if (text.contains("\"type\":\"ack\"")) status = "Sent"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(text = "Status: $status")
        Column(modifier = Modifier.weight(1f)) {
            messages.forEach { m -> Text(m) }
        }
        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val json = "{\"type\":\"message\",\"conversationId\":$conversationId,\"content\":\"" + input + "\"}"
            webSocketManager.send(json)
            input = ""
            status = "Sending..."
        }) { Text("إرسال") }
    }
}
