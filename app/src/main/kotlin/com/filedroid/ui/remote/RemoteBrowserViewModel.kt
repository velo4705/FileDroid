package com.filedroid.ui.remote

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.data.ConnectionProfile
import com.filedroid.data.ConnectionProfileRepository
import com.filedroid.data.Protocol
import com.filedroid.remote.FtpClient
import com.filedroid.remote.RemoteClient
import com.filedroid.remote.RemoteFile
import com.filedroid.remote.SftpClient
import com.filedroid.transfer.TransferEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class RemoteBrowserUiState(
    val profile: ConnectionProfile? = null,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val currentPath: String = "/",
    val entries: List<RemoteFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showRenameDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val selectedFile: RemoteFile? = null,
    val showReconnectPrompt: Boolean = false,
    val downloadedToPath: String? = null  // non-null briefly after a download completes
)

@HiltViewModel
class RemoteBrowserViewModel @Inject constructor(
    private val profileRepo: ConnectionProfileRepository,
    private val transferEngine: TransferEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteBrowserUiState())
    val uiState: StateFlow<RemoteBrowserUiState> = _uiState.asStateFlow()

    private var client: RemoteClient? = null
    private val backStack = ArrayDeque<String>()

    /** Load profile by ID then connect. */
    fun loadAndConnect(profileId: Long) {
        viewModelScope.launch {
            val profile = profileRepo.getById(profileId) ?: return@launch
            _uiState.update { it.copy(profile = profile) }
            connect(profile)
        }
    }

    fun connect(profile: ConnectionProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isConnecting = true, error = null, profile = profile) }
            val remoteClient: RemoteClient = when (profile.protocol) {
                Protocol.SFTP -> SftpClient()
                Protocol.FTP -> FtpClient()
                Protocol.FTPS -> FtpClient().also { it.useFtps() }
            }
            val result = when {
                profile.anonymous -> remoteClient.connectAnonymous(profile.host, profile.port)
                // R2.8 — private-key auth for SFTP
                profile.usePrivateKey && remoteClient is SftpClient -> {
                    val privateKey = profileRepo.getPassword(profile) ?: ""
                    remoteClient.connectWithKey(profile.host, profile.port, profile.username, privateKey)
                }
                else -> {
                    val password = profileRepo.getPassword(profile) ?: ""
                    remoteClient.connect(profile.host, profile.port, profile.username, password)
                }
            }
            result.fold(
                onSuccess = {
                    client = remoteClient
                    _uiState.update { it.copy(isConnected = true, isConnecting = false) }
                    navigateTo(profile.initialRemotePath)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isConnecting = false, error = e.message) }
                }
            )
        }
    }

    fun navigateTo(path: String) {
        // R7.5 — reject path traversal
        if (path.contains("..")) {
            _uiState.update { it.copy(error = "Access denied: invalid path") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = client?.listDirectory(path)
            if (result == null) {
                // client gone — prompt reconnect
                _uiState.update { it.copy(isLoading = false, showReconnectPrompt = true) }
                return@launch
            }
            result.fold(
                onSuccess = { entries ->
                    backStack.addLast(path)
                    _uiState.update {
                        it.copy(currentPath = path, entries = entries, isLoading = false)
                    }
                },
                onFailure = { e ->
                    val msg = e.message ?: "Unknown error"
                    val dropped = msg.contains("broken pipe", ignoreCase = true) ||
                            msg.contains("connection", ignoreCase = true)
                    _uiState.update {
                        it.copy(isLoading = false, error = msg,
                            showReconnectPrompt = dropped && it.isConnected)
                    }
                }
            )
        }
    }

    fun navigateUp(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeLast()
        val parent = backStack.removeLast()
        navigateTo(parent)
        return true
    }

    fun rename(newName: String) {
        val file = _uiState.value.selectedFile ?: return
        val newPath = file.path.substringBeforeLast("/") + "/$newName"
        viewModelScope.launch(Dispatchers.IO) {
            client?.rename(file.path, newPath)?.fold(
                onSuccess = { refresh() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
        dismissDialogs()
    }

    fun delete() {
        val file = _uiState.value.selectedFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            client?.delete(file.path)?.fold(
                onSuccess = { refresh() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
        dismissDialogs()
    }

    fun createDirectory(name: String) {
        val path = _uiState.value.currentPath.trimEnd('/') + "/$name"
        viewModelScope.launch(Dispatchers.IO) {
            client?.createDirectory(path)?.fold(
                onSuccess = { refresh() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
    }

    fun download(file: RemoteFile) {
        val c = client ?: return
        val destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val localPath = File(destDir, file.name).absolutePath
        transferEngine.enqueueDownload(c, file.path, localPath, file.size)
        _uiState.update { it.copy(downloadedToPath = localPath) }
    }

    fun clearDownloadedToast() = _uiState.update { it.copy(downloadedToPath = null) }

    fun dismissReconnectPrompt() = _uiState.update { it.copy(showReconnectPrompt = false) }

    fun reconnect() {
        val profile = _uiState.value.profile ?: return
        _uiState.update { it.copy(showReconnectPrompt = false) }
        connect(profile)
    }

    fun showRename(file: RemoteFile) = _uiState.update { it.copy(showRenameDialog = true, selectedFile = file) }
    fun showDeleteConfirm(file: RemoteFile) = _uiState.update { it.copy(showDeleteConfirm = true, selectedFile = file) }
    fun dismissDialogs() = _uiState.update { it.copy(showRenameDialog = false, showDeleteConfirm = false, selectedFile = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun disconnect() {
        client?.disconnect(); client = null; backStack.clear()
        _uiState.update { RemoteBrowserUiState() }
    }

    private fun refresh() = navigateTo(_uiState.value.currentPath)

    override fun onCleared() { super.onCleared(); client?.disconnect() }
}
