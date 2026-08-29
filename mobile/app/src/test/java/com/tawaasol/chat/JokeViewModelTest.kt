package com.tawaasol.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRepo(private val joke: String?) : com.tawaasol.chat.data.JokeRepositoryInterface {
    override suspend fun fetchRandom(): String? = joke
    override suspend fun getCachedJoke(): String? = null
}

class JokeViewModelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when repo returns joke viewmodel emits success`() = runTest {
        val vm = JokeViewModel(FakeRepo("مرحبا - اختبار"))
        vm.loadJoke()
        // give coroutine a chance to run
        kotlinx.coroutines.test.advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        if (state is UiState.Success) {
            assertTrue(state.joke.contains("اختبار"))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when repo returns null viewmodel emits error`() = runTest {
        val vm = JokeViewModel(FakeRepo(null))
        vm.loadJoke()
        kotlinx.coroutines.test.advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
    }
}
