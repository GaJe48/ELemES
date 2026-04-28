package com.gaje48.lms.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets

private val Context.credentialsDataStore by preferencesDataStore(name = "secure_credentials")

class LocalDataSource(
    private val context: Context,
) {
    private companion object {
        const val KEYSET_NAME = "lms_secure_keyset"
        const val KEYSET_PREF_FILE = "lms_secure_keyset_pref"
        const val KEY_TEMPLATE = "AES256_GCM"
        const val MASTER_KEY_URI = "android-keystore://lms_master_key"
        val ASSOCIATED_DATA = "lms-unindra-credentials".toByteArray(StandardCharsets.UTF_8)
    }

    private object Keys {
        val nim = stringPreferencesKey("nim")
        val password = stringPreferencesKey("password_cipher")
    }

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplates.get(KEY_TEMPLATE))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    val credentials: Flow<Pair<String, String>?> = context.credentialsDataStore.data
        .map { preferences ->
            val nim = preferences[Keys.nim]
            val pwd = preferences[Keys.password]?.let { decryptPassword(it) }

            if (nim != null && pwd != null) Pair(nim, pwd) else null
        }

    suspend fun getCredentials(): Pair<String, String>? = credentials.first()

    suspend fun saveCredentials(nim: String, password: String) {
        context.credentialsDataStore.edit { preferences ->
            preferences[Keys.nim] = nim
            preferences[Keys.password] = encryptPassword(password)
        }
    }

    suspend fun clearCredentials() {
        context.credentialsDataStore.edit { preferences ->
            preferences.remove(Keys.nim)
            preferences.remove(Keys.password)
        }
    }

    private fun encryptPassword(password: String): String {
        val encrypted = aead.encrypt(password.toByteArray(StandardCharsets.UTF_8), ASSOCIATED_DATA)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decryptPassword(cipherText: String): String {
        val clearBytes = aead.decrypt(Base64.decode(cipherText, Base64.NO_WRAP), ASSOCIATED_DATA)
        return String(clearBytes, StandardCharsets.UTF_8)
    }


}