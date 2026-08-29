package com.tawaasol.jokes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JokeScreen()
                }
            }
        }
    }
}

@Composable
fun JokeScreen() {
    var joke by remember { mutableStateOf("اضغط على " + "جلب نكتة" + " للبدء") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = joke, style = MaterialTheme.typography.h6)

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

        Button(onClick = {
            if (!loading) {
                loading = true
                CoroutineScope(Dispatchers.IO).launch {
                    val result = JokeRepository.fetchRandomJoke()
                    launch(Dispatchers.Main) {
                        joke = result ?: "فشل جلب النكتة. حاول مرة أخرى."
                        loading = false
                    }
                }
            }
        }) {
            Text(text = if (loading) "تحميل..." else "جلب نكتة")
        }
    }
}
