package org.siros.wwwallet.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.siros.wwwallet.BuildConfig

data class Profile(
    val baseUrl: String,
)

class ProfileStorage(
    private val context: Context,
) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

        private val BASE_URL_KEY = stringPreferencesKey("baseurl")
    }

    suspend fun store(profile: Profile) {
        context.dataStore.edit { store ->
            store[BASE_URL_KEY] = profile.baseUrl
        }
    }

    suspend fun restore(): Profile =
        context.dataStore.data
            .map { preferences ->
                Profile(
                    baseUrl = preferences[BASE_URL_KEY] ?: BuildConfig.BASE_URL,
                )
            }.first()
}
