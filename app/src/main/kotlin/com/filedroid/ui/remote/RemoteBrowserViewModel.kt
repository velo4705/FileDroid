package com.filedroid.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.data.ConnectionProfile
import com.filedroid.data.ConnectionProfileRepository
import com.filedroid.data.Protocol
import com.filedroid.remote.FtpClient
import com.filedroid.remote.RemoteClient
import com.filedroid.remote.RemoteFile
import com.filedroid.remote.SftpClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val selectedFile: RemoteFile? = null
)

@HiltViewModel
class RemoteBrowserViewModel @Inject constructor(
    private val profileRepo: ConnectionProfileRepository
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
            val result = if (profile.anonymous) {
                remoteClient.connectAnonymous(profile.host, profile.port)
            } else {
                val password = profileRepo.getPassword(profile) ?: ""
                remoteClient.connect(profile.host, profile.port, profile.username, password)
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
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            client?.listDirectory(path)?.fold(
                onSuccess = { entries ->
                    backStack.addLast(path)
                    _uiState.update {
                        it.copy(currentPath = path, entries = entries, isLoading = false)
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
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

    fun showRename(file: RemoteFile) = _uiState.update { it.copy(showRenameDialog = true, selectedFile = file) }
    fun showDeleteConfirm(file: RemoteFile) = _uiState.update { it.copy(showDeleteConfirm = true, selectedFile = file) }
    fun dismissDialogs() = _uiState.update { it.copy(showRenameDialog = false, showDeleteConfirm = false, selectedFile = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun disconnect() { client?.disconnect(); client = null; backStack.clear()
        _uiState.update { RemoteBrowserUiState() }
    }

    private fun refresh() = navigateTo(_uiState.value.currentPath)

    override fun onCleared() { super.onCleared(); client?.disconnect() }
}
