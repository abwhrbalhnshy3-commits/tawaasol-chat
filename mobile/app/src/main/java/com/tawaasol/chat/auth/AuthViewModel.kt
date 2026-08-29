package com.tawaasol.chat.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tawaasol.chat.datastore.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState { object Idle: AuthUiState(); object Loading: AuthUiState(); data class Sent(val phone: String): AuthUiState(); data class Verified(val token: String): AuthUiState(); data class Error(val msg: String): AuthUiState() }

class AuthViewModel(private val repo: AuthRepository, private val dataStore: DataStoreManager): ViewModel() {
    private val _ui = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun requestOtp(phone: String) {
        viewModelScope.launch {
            _ui.value = AuthUiState.Loading
            val r = repo.requestOtp(phone)
            if (r.isSuccessful) _ui.value = AuthUiState.Sent(phone) else _ui.value = AuthUiState.Error("failed")
        }
    }

    fun verifyOtp(phone: String, otp: String, name: String?) {
        viewModelScope.launch {
            _ui.value = AuthUiState.Loading
            val r = repo.verifyOtp(phone, otp, name)
            if (r.isSuccessful) {
                val token = r.body()?.get("token") ?: ""
                _ui.value = AuthUiState.Verified(token)
            } else _ui.value = AuthUiState.Error("invalid otp")
        }
    }
}
