package com.tawaasol.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class MessageUi(val id: Int, val senderId: Int, val content: String)

@Composable
fun ChatScreen(messages: List<MessageUi>) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { msg ->
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Text(text = msg.content, modifier = Modifier.padding(12.dp))
                }
            }
        }
        // input UI would go here (TextField, Send button)
        Text(text = "(Input field and send button to be implemented)")
    }
}
