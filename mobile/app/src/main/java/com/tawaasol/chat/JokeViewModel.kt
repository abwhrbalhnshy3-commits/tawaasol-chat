package com.tawaasol.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tawaasol.chat.data.JokeRepositoryInterface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val joke: String) : UiState()
    data class Error(val message: String) : UiState()
}

@HiltViewModel
class JokeViewModel @Inject constructor(private val repo: JokeRepositoryInterface) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadJoke() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val j = repo.fetchRandom() ?: repo.getCachedJoke()
            _uiState.value = if (j != null) UiState.Success(j) else UiState.Error("فشل جلب النكتة")
        }
    }
}
