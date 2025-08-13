package io.yubicolabs.wwwwallet.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import de.adesso.softauthn.Authenticators
import de.adesso.softauthn.CredentialsContainer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class CredentialStorage(
    private val context: Context,
) {
    companion object {
        private val CREDENTIALS_BLOB_KEY = byteArrayPreferencesKey("credentials_blob_key")

        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    }

    private val initialCredentialContainer: CredentialsContainer by lazy {
        val authenticators =
            listOf(
                Authenticators.yubikey5Nfc().build(),
            )

        CredentialsContainer(authenticators)
    }

    suspend fun store(container: CredentialsContainer) {
        context.dataStore.edit { preferences ->
            // TODO: Think of secure storage
            val blob = container.serialize()
            preferences[CREDENTIALS_BLOB_KEY] = blob
        }
    }

    suspend fun restore(): CredentialsContainer =
        context.dataStore.data.map { preferences ->
            val blob = preferences[CREDENTIALS_BLOB_KEY]
            if (blob != null) {
                CredentialsContainer.deserialize(blob)
            } else {
                null
            }
        }.firstOrNull() ?: initialCredentialContainer
}
