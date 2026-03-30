package com.filedroid.ui.ssh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.data.SshProfile
import com.filedroid.data.SshProfileDao
import com.filedroid.security.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SshProfileViewModel @Inject constructor(
    private val dao: SshProfileDao,
    private val credentialStore: CredentialStore
) : ViewModel() {

    val profiles: StateFlow<List<SshProfile>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(
        profile: SshProfile?,
        label: String, host: String, port: Int, username: String,
        password: String = "",
        usePrivateKey: Boolean = false,
        privateKey: String = "",
        passphrase: String = ""
    ) {
        viewModelScope.launch {
            val credKey = profile?.credentialKey ?: "ssh_${UUID.randomUUID()}"
            val credValue = if (usePrivateKey && privateKey.isNotEmpty()) privateKey else password
            if (credValue.isNotEmpty()) credentialStore.putString(credKey, credValue)
            if (passphrase.isNotEmpty()) {
                credentialStore.putString("${credKey}_passphrase", passphrase)
            } else {
                credentialStore.remove("${credKey}_passphrase")
            }
            dao.upsert(SshProfile(
                id = profile?.id ?: 0,
                label = label, host = host, port = port,
                username = username, credentialKey = credKey,
                usePrivateKey = usePrivateKey
            ))
        }
    }

    fun delete(profile: SshProfile) {
        viewModelScope.launch {
            credentialStore.remove(profile.credentialKey)
            credentialStore.remove("${profile.credentialKey}_passphrase")
            dao.delete(profile)
        }
    }

    fun getPassword(profile: SshProfile): String =
        credentialStore.getString(profile.credentialKey) ?: ""

    fun getPassphrase(profile: SshProfile): String? =
        credentialStore.getString("${profile.credentialKey}_passphrase")
}
