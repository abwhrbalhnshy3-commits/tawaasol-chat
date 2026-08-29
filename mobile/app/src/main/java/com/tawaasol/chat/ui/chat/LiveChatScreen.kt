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
fun LiveChatScreen(webSocketManager: WebSocketManager) {
    var input by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        webSocketManager.connect()
        webSocketManager.events.collect { text ->
            messages.add(text)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            messages.forEach { m -> Text(m) }
        }
        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { 
            val json = "{\"type\":\"message\",\"conversationId\":1,\"content\":\"" + input + "\"}"
            webSocketManager.send(json)
            input = ""
        }) { Text("إرسال") }
    }
}
