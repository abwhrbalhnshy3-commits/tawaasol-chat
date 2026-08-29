package com.tawaasol.chat.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tawaasol.chat.auth.AuthViewModel

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val state by viewModel.ui.collectAsState()
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") })
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.requestOtp(phone) }) { Text("أرسل رمز التحقق") }

        if (state is com.tawaasol.chat.auth.AuthUiState.Sent) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = otp, onValueChange = { otp = it }, label = { Text("رمز التحقق") })
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.verifyOtp(phone, otp, null) }) { Text("تحقق وادخل") }
        }

        when (state) {
            is com.tawaasol.chat.auth.AuthUiState.Loading -> Text("تحميل...")
            is com.tawaasol.chat.auth.AuthUiState.Error -> Text((state as com.tawaasol.chat.auth.AuthUiState.Error).msg)
            else -> {}
        }
    }
}
