package uk.yaylali.cellseg.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted wrapper for the Hugging Face API token.
 *
 * The token is stored only in [EncryptedSharedPreferences].
 * It is NEVER written to Room, DataStore, logs, or exported data.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val PREFS_FILE = "hf_token_prefs"
        const val KEY_HF_TOKEN = "hf_api_token"
    }

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Returns the stored HF token, or null if not set. */
    fun getToken(): String? = prefs.getString(KEY_HF_TOKEN, null)?.ifBlank { null }

    /** Stores the HF token. Pass null or blank to clear. */
    fun setToken(token: String?) {
        if (token.isNullOrBlank()) {
            prefs.edit().remove(KEY_HF_TOKEN).apply()
        } else {
            prefs.edit().putString(KEY_HF_TOKEN, token).apply()
        }
    }

    /** Removes any stored token. */
    fun clearToken() = prefs.edit().remove(KEY_HF_TOKEN).apply()

    /** Returns true if a non-blank token is stored. */
    fun hasToken(): Boolean = !getToken().isNullOrBlank()
}
