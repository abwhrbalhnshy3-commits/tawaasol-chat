package com.tawaasol.chat.data

import com.tawaasol.chat.api.JokeApi
import com.tawaasol.chat.datastore.DataStoreManager
import javax.inject.Inject
import javax.inject.Singleton

interface JokeRepositoryInterface {
    suspend fun fetchRandom(): String?
    suspend fun getCachedJoke(): String?
}

@Singleton
class DefaultJokeRepository @Inject constructor(
    private val api: JokeApi,
    private val dataStore: DataStoreManager
) : JokeRepositoryInterface {
    override suspend fun fetchRandom(): String? {
        val resp = api.randomJoke()
        if (resp.isSuccessful) {
            val j = resp.body()?.joke
            if (j != null) {
                dataStore.saveLastJoke(j)
            }
            return j
        }
        return null
    }

    override suspend fun getCachedJoke(): String? {
        return dataStore.getLastJoke()
    }
}
