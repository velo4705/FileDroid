package com.filedroid.ui.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.security.CredentialKeys
import com.filedroid.security.CredentialStore
import com.filedroid.server.FtpServerManager
import com.filedroid.server.ServerConfig
import com.filedroid.server.SftpServerManager
import com.filedroid.service.ServerForegroundService
import com.filedroid.tunnel.TunnelManager
import com.filedroid.tunnel.TunnelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import javax.inject.Inject

data class ServerUiState(
    val ftpRunning: Boolean = false,
    val sftpRunning: Boolean = false,
    val ftpPort: Int = 2121,
    val sftpPort: Int = 2222,
    val rootPath: String = "",
    val localIp: String = "",
    val availableInterfaces: List<Pair<String, String>> = emptyList(),
    val bindAddress: String = "",
    val tunnelEnabled: Boolean = false,
    val relayUrl: String = "",
    val tunnelId: String = "",
    /** Public ports assigned by the relay — e.g. {"ftp": 3021, "sftp": 3022}
     *  FileZilla/WinSCP can connect to these ports on the relay host. */
    val publicPorts: Map<String, Int> = emptyMap()
)

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val ftpManager: FtpServerManager,
    private val sftpManager: SftpServerManager,
    private val credentialStore: CredentialStore,
    private val tunnelManager: TunnelManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState(
        ftpPort = credentialStore.getString(CredentialKeys.FTP_PORT)?.toIntOrNull() ?: 2121,
        sftpPort = credentialStore.getString(CredentialKeys.SFTP_PORT)?.toIntOrNull() ?: 2222,
        rootPath = credentialStore.getString("root_path") ?: "",
        availableInterfaces = loadNetworkInterfaces(),
        bindAddress = credentialStore.getString("bind_address") ?: "",
        tunnelEnabled = credentialStore.getString("tunnel_enabled") == "true",
        relayUrl = credentialStore.getString("relay_url") ?: com.filedroid.tunnel.TunnelConfig.DEFAULT_RELAY_URL,
        tunnelId = credentialStore.getString("tunnel_id") ?: ""
    ))
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    // Observe tunnel state for public ports
    init {
        viewModelScope.launch {
            tunnelManager.state.collect { state ->
                if (state.status == TunnelStatus.CONNECTED && state.publicPorts.isNotEmpty()) {
                    _uiState.update { it.copy(publicPorts = state.publicPorts) }
                } else if (state.status == TunnelStatus.DISCONNECTED) {
                    _uiState.update { it.copy(publicPorts = emptyMap()) }
                }
            }
        }
    }

    fun startServers(ftpEnabled: Boolean, sftpEnabled: Boolean) {
        val config = buildConfig(ftpEnabled, sftpEnabled)
        context.startForegroundService(ServerForegroundService.startIntent(context, config))
        _uiState.update { it.copy(ftpRunning = ftpEnabled, sftpRunning = sftpEnabled) }
    }

    fun stopServers() {
        context.startService(ServerForegroundService.stopIntent(context))
        _uiState.update { it.copy(ftpRunning = false, sftpRunning = false) }
    }

    fun refresh() {
        // Only sync from managers if we think nothing is running — avoids overwriting
        // optimistic state before the async server start coroutine completes
        val current = _uiState.value
        if (!current.ftpRunning && !current.sftpRunning) {
            _uiState.update {
                it.copy(
                    ftpRunning = ftpManager.isRunning(),
                    sftpRunning = sftpManager.isRunning(),
                    availableInterfaces = loadNetworkInterfaces()
                )
            }
        } else {
            _uiState.update { it.copy(availableInterfaces = loadNetworkInterfaces()) }
        }
    }

    /** R7.4 — persist and apply selected bind address */
    fun setBindAddress(address: String) {
        credentialStore.putString("bind_address", address)
        _uiState.update { it.copy(bindAddress = address) }
    }

    /** M10 — persist tunnel settings */
    fun setTunnelConfig(enabled: Boolean, relayUrl: String, tunnelId: String) {
        credentialStore.putString("tunnel_enabled", enabled.toString())
        credentialStore.putString("relay_url", relayUrl)
        credentialStore.putString("tunnel_id", tunnelId)
        _uiState.update { it.copy(tunnelEnabled = enabled, relayUrl = relayUrl, tunnelId = tunnelId) }
    }

    private fun buildConfig(ftpEnabled: Boolean, sftpEnabled: Boolean) = ServerConfig(
        ftpPort = _uiState.value.ftpPort,
        sftpPort = _uiState.value.sftpPort,
        rootPath = _uiState.value.rootPath,
        username = credentialStore.getString("server_username") ?: "",
        password = credentialStore.getString(CredentialKeys.SERVER_PASSWORD) ?: "",
        ftpEnabled = ftpEnabled,
        sftpEnabled = sftpEnabled,
        bindAddress = _uiState.value.bindAddress,
        tunnelEnabled = _uiState.value.tunnelEnabled,
        relayUrl = _uiState.value.relayUrl,
        tunnelId = _uiState.value.tunnelId
    )

    companion object {
        /** Returns list of (label, ipAddress) for all up non-loopback IPv4 interfaces. */
        fun loadNetworkInterfaces(): List<Pair<String, String>> = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { iface ->
                    iface.inetAddresses.toList()
                        .filter { addr -> addr is java.net.Inet4Address }
                        .map { addr -> "${iface.displayName} (${addr.hostAddress})" to addr.hostAddress!! }
                } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
