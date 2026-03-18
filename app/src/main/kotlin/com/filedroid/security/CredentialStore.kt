package com.filedroid.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keys used to store credentials in EncryptedSharedPreferences. */
object CredentialKeys {
    const val SERVER_PASSWORD = "server_password"
    const val FTP_PORT = "ftp_port"
    const val SFTP_PORT = "sftp_port"
}

/** Thrown when EncryptedSharedPreferences cannot be initialised. */
class CredentialStoreException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

interface CredentialStore {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
    fun hasServerPassword(): Boolean
}

class CredentialStoreImpl(context: Context) : CredentialStore {

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "filedroid_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        throw CredentialStoreException(
            "Failed to initialise EncryptedSharedPreferences. " +
                "Clear app data and restart if this persists.",
            e
        )
    }

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun hasServerPassword(): Boolean =
        prefs.getString(CredentialKeys.SERVER_PASSWORD, null)?.isNotEmpty() == true
}
