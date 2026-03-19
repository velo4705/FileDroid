package com.filedroid.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.data.ConnectionProfile
import com.filedroid.data.ConnectionProfileRepository
import com.filedroid.data.Protocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileListViewModel @Inject constructor(
    private val repo: ConnectionProfileRepository
) : ViewModel() {

    val profiles: StateFlow<List<ConnectionProfile>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(
        id: Long = 0,
        label: String,
        protocol: Protocol,
        host: String,
        port: Int,
        username: String,
        password: String,
        initialPath: String,
        anonymous: Boolean,
        usePrivateKey: Boolean = false,
        privateKey: String = "",
        passphrase: String = ""
    ) {
        viewModelScope.launch {
            repo.save(
                ConnectionProfile(
                    id = id, label = label, protocol = protocol,
                    host = host, port = port, username = username,
                    credentialKey = "", initialRemotePath = initialPath,
                    anonymous = anonymous, usePrivateKey = usePrivateKey
                ),
                // Store private key as credential if provided, otherwise password
                if (usePrivateKey && privateKey.isNotBlank()) privateKey else password
            )
        }
    }

    fun delete(profile: ConnectionProfile) {
        viewModelScope.launch { repo.delete(profile) }
    }

    fun getPassword(profile: ConnectionProfile): String =
        repo.getPassword(profile) ?: ""
}
