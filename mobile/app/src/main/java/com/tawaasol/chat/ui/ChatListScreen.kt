package com.tawaasol.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class ChatItem(val id: Int, val title: String, val lastMessage: String)

@Composable
fun ChatListScreen(items: List<ChatItem>, onOpen: (Int) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(items) { item ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)) {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.title)
                        Text(text = item.lastMessage, color = Color.Gray)
                    }
                    Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text(text = "Open", modifier = Modifier
                            .background(Color(0xFF06B6D4))
                            .padding(8.dp))
                    }
                }
            }
        }
    }
}
