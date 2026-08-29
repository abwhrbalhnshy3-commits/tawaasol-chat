package com.tawaasol.chat.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "tawaasol_prefs")

@Singleton
class DataStoreManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val KEY_LAST_JOKE = stringPreferencesKey("last_joke")

    suspend fun saveLastJoke(joke: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_JOKE] = joke
        }
    }

    suspend fun getLastJoke(): String? {
        return try {
            val prefs = context.dataStore.data.first()
            prefs[KEY_LAST_JOKE]
        } catch (e: IOException) {
            null
        }
    }
}
