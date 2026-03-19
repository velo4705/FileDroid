package com.filedroid.ui.settings

import androidx.lifecycle.ViewModel
import com.filedroid.permission.PermissionManager
import com.filedroid.security.CredentialKeys
import com.filedroid.security.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val PORT_ERROR_MSG = "Port must be between 1 and 65535"
private const val KEY_USERNAME = "server_username"
private const val KEY_ROOT_PATH = "root_path"

data class SettingsUiState(
    val ftpPort: String = "2121",
    val sftpPort: String = "2222",
    val ftpPortError: String? = null,
    val sftpPortError: String? = null,
    val serverUsername: String = "",
    val serverPassword: String = "",
    val serverRootPath: String = "",
    val passwordSaved: Boolean = false
)

/** Returns true iff [input] represents an integer in [1, 65535]. */
fun isValidPort(input: String): Boolean {
    val n = input.toIntOrNull() ?: return false
    return n in 1..65535
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialStore: CredentialStore,
    @Suppress("UNUSED_PARAMETER") private val permissionManager: PermissionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            ftpPort = credentialStore.getString(CredentialKeys.FTP_PORT) ?: "2121",
            sftpPort = credentialStore.getString(CredentialKeys.SFTP_PORT) ?: "2222",
            serverUsername = credentialStore.getString(KEY_USERNAME) ?: "",
            serverPassword = credentialStore.getString(CredentialKeys.SERVER_PASSWORD) ?: "",
            serverRootPath = credentialStore.getString(KEY_ROOT_PATH) ?: ""
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateFtpPort(input: String) {
        _uiState.update {
            it.copy(ftpPort = input, ftpPortError = if (isValidPort(input)) null else PORT_ERROR_MSG)
        }
    }

    fun updateSftpPort(input: String) {
        _uiState.update {
            it.copy(sftpPort = input, sftpPortError = if (isValidPort(input)) null else PORT_ERROR_MSG)
        }
    }

    fun savePorts() {
        val state = _uiState.value
        if (state.ftpPortError == null) credentialStore.putString(CredentialKeys.FTP_PORT, state.ftpPort)
        if (state.sftpPortError == null) credentialStore.putString(CredentialKeys.SFTP_PORT, state.sftpPort)
    }

    fun updateServerUsername(value: String) = _uiState.update { it.copy(serverUsername = value) }
    fun updateServerPassword(value: String) = _uiState.update { it.copy(serverPassword = value) }
    fun updateServerRootPath(value: String) = _uiState.update { it.copy(serverRootPath = value) }

    fun saveServerCredentials() {
        val state = _uiState.value
        credentialStore.putString(KEY_USERNAME, state.serverUsername)
        credentialStore.putString(CredentialKeys.SERVER_PASSWORD, state.serverPassword)
        credentialStore.putString(KEY_ROOT_PATH, state.serverRootPath)
        _uiState.update { it.copy(passwordSaved = true) }
    }

    fun saveServerPassword(password: String) {
        credentialStore.putString(CredentialKeys.SERVER_PASSWORD, password)
        _uiState.update { it.copy(serverPassword = password, passwordSaved = true) }
    }
}
