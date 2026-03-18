package com.filedroid.ui.server

import android.content.Context
import androidx.lifecycle.ViewModel
import com.filedroid.security.CredentialKeys
import com.filedroid.security.CredentialStore
import com.filedroid.server.FtpServerManager
import com.filedroid.server.ServerConfig
import com.filedroid.server.SftpServerManager
import com.filedroid.service.ServerForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ServerUiState(
    val ftpRunning: Boolean = false,
    val sftpRunning: Boolean = false,
    val ftpPort: Int = 2121,
    val sftpPort: Int = 2222,
    val rootPath: String = "",
    val localIp: String = ""
)

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val ftpManager: FtpServerManager,
    private val sftpManager: SftpServerManager,
    private val credentialStore: CredentialStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState(
        ftpPort = credentialStore.getString(CredentialKeys.FTP_PORT)?.toIntOrNull() ?: 2121,
        sftpPort = credentialStore.getString(CredentialKeys.SFTP_PORT)?.toIntOrNull() ?: 2222,
        rootPath = credentialStore.getString("root_path") ?: ""
    ))
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

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
        _uiState.update { it.copy(ftpRunning = ftpManager.isRunning(), sftpRunning = sftpManager.isRunning()) }
    }

    private fun buildConfig(ftpEnabled: Boolean, sftpEnabled: Boolean) = ServerConfig(
        ftpPort = _uiState.value.ftpPort,
        sftpPort = _uiState.value.sftpPort,
        rootPath = _uiState.value.rootPath,
        username = credentialStore.getString("server_username") ?: "",
        password = credentialStore.getString(CredentialKeys.SERVER_PASSWORD) ?: "",
        ftpEnabled = ftpEnabled,
        sftpEnabled = sftpEnabled
    )
}
