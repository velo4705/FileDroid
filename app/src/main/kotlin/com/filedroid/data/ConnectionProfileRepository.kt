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

    suspend fun save(profile: ConnectionProfile, password: String): Long {
        val key = "conn_${profile.label}_${profile.host}"
        credentialStore.putString(key, password)
        val saved = profile.copy(credentialKey = key)
        return dao.upsert(saved)
    }

    suspend fun delete(profile: ConnectionProfile) {
        credentialStore.remove(profile.credentialKey)
        dao.delete(profile)
    }

    fun getPassword(profile: ConnectionProfile): String? =
        credentialStore.getString(profile.credentialKey)
}
