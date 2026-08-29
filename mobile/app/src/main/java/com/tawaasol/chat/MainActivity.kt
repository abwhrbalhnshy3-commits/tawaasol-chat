package com.tawaasol.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: JokeViewModel = hiltViewModel()
                    JokeScreen(vm)
                }
            }
        }
    }
}

@Composable
fun JokeScreen(viewModel: JokeViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Success -> Text(text = (state as UiState.Success).joke)
            is UiState.Error -> Text(text = (state as UiState.Error).message)
            UiState.Idle -> Text(text = "اضغط على جلب نكتة للبدء")
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

        Button(onClick = { viewModel.loadJoke() }) {
            Text(text = "جلب نكتة")
        }
    }
}
