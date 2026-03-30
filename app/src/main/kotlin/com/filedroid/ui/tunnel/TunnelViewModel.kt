package com.filedroid.ui.tunnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.security.CredentialStore
import com.filedroid.tunnel.ConnectionCode
import com.filedroid.tunnel.TunnelConfig
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

    /**
     * Connect using a human-friendly 4-word code.
     * The code is the tunnel ID — no URL needed, uses the built-in relay.
     */
    fun connectWithCode(code: String) {
        val tunnelId = ConnectionCode.toTunnelId(code)
        val config = TunnelConfig(
            relayUrl = TunnelConfig.DEFAULT_RELAY_URL,
            tunnelId = tunnelId
        )
        tunnelManager.startClient(config)
    }

    /** Disconnect from the relay. */
    fun disconnect() {
        tunnelManager.stop()
    }
}
