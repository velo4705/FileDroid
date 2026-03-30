package com.filedroid.data

import com.filedroid.security.CredentialStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ConnectionProfileRepository @Inject constructor(
    private val dao: ConnectionProfileDao,
    private val credentialStore: CredentialStore
) {
    fun observeAll(): Flow<List<ConnectionProfile>> = dao.observeAll()

    suspend fun getById(id: Long): ConnectionProfile? = dao.getById(id)

    suspend fun save(profile: ConnectionProfile, password: String, passphrase: String = ""): Long {
        val key = "conn_${profile.label}_${profile.host}"
        credentialStore.putString(key, password)
        if (passphrase.isNotBlank()) {
            credentialStore.putString("${key}_passphrase", passphrase)
        } else {
            credentialStore.remove("${key}_passphrase")
        }
        val saved = profile.copy(credentialKey = key)
        return dao.upsert(saved)
    }

    suspend fun delete(profile: ConnectionProfile) {
        credentialStore.remove(profile.credentialKey)
        credentialStore.remove("${profile.credentialKey}_passphrase")
        dao.delete(profile)
    }

    fun getPassword(profile: ConnectionProfile): String? =
        credentialStore.getString(profile.credentialKey)

    fun getPassphrase(profile: ConnectionProfile): String? =
        credentialStore.getString("${profile.credentialKey}_passphrase")
}
