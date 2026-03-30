package com.filedroid.ui.tunnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.security.CredentialKeys
import com.filedroid.security.CredentialStore
import com.filedroid.tunnel.RelayClient
import com.filedroid.tunnel.TunnelManager
import com.filedroid.tunnel.TunnelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TunnelViewModel @Inject constructor(
    private val tunnelManager: TunnelManager,
    private val credentialStore: CredentialStore
) : ViewModel() {

    val state: StateFlow<TunnelState> = tunnelManager.state

    /** Saved relay URL preference. */
    fun getRelayUrl(): String = credentialStore.getString("relay_url") ?: ""

    /** Saved tunnel ID preference. */
    fun getTunnelId(): String = credentialStore.getString("tunnel_id") ?: ""

    /** Connect as tunnel client to reach a remote server over mobile data. */
    fun connectAsClient(relayUrl: String, tunnelId: String, username: String = "", password: String = "") {
        savePrefs(relayUrl, tunnelId)
        val config = com.filedroid.tunnel.TunnelConfig(
            relayUrl = relayUrl,
            tunnelId = tunnelId,
            username = username,
            password = password
        )
        tunnelManager.startClient(config)
    }

    /** Disconnect from the relay. */
    fun disconnect() {
        tunnelManager.stop()
    }

    private fun savePrefs(relayUrl: String, tunnelId: String) {
        credentialStore.putString("relay_url", relayUrl)
        credentialStore.putString("tunnel_id", tunnelId)
    }
}
