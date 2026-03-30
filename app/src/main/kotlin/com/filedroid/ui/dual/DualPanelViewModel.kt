package com.filedroid.ui.dual

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.data.ConnectionProfile
import com.filedroid.data.ConnectionProfileRepository
import com.filedroid.data.Protocol
import com.filedroid.local.LocalFileRepository
import com.filedroid.remote.FtpClient
import com.filedroid.remote.RemoteClient
import com.filedroid.remote.RemoteFile
import com.filedroid.remote.SftpClient
import com.filedroid.transfer.TransferEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** A file item that can exist on either the local or remote side. */
data class PanelFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isLocal: Boolean
)

/** Which panel is being shown on the remote side. */
data class RemotePanelState(
    val profile: ConnectionProfile? = null,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val currentPath: String = "/",
    val entries: List<RemoteFile> = emptyList(),
    val error: String? = null
)

/** State of one panel (local or remote). */
data class PanelState(
    val currentPath: String = "",
    val entries: List<PanelFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val breadcrumbs: List<String> = emptyList(),
    val isLocal: Boolean = true
)

data class DualPanelUiState(
    val leftPanel: PanelState = PanelState(isLocal = true),
    val rightPanel: PanelState = PanelState(isLocal = false),
    val remoteState: RemotePanelState = RemotePanelState(),
    val showProfilePicker: Boolean = false,
    val transferMessage: String? = null
)

@HiltViewModel
class DualPanelViewModel @Inject constructor(
    private val localRepo: LocalFileRepository,
    private val profileRepo: ConnectionProfileRepository,
    private val transferEngine: TransferEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualPanelUiState())
    val uiState: StateFlow<DualPanelUiState> = _uiState.asStateFlow()

    private var remoteClient: RemoteClient? = null

    // Local side navigation
    private val localBackStack = ArrayDeque<File>()

    init {
        val root = localRepo.getDefaultRoot()
        navigateLocal(root)
    }

    // ── Local panel navigation ──────────────────────────────────────────

    fun navigateLocal(dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(leftPanel = it.leftPanel.copy(isLoading = true, error = null)) }
            localRepo.listDirectory(dir).fold(
                onSuccess = { entries ->
                    localBackStack.addLast(dir)
                    val panelFiles = entries.map { f ->
                        PanelFile(f.name, f.path, f.isDirectory, f.size, f.lastModified, isLocal = true)
                    }
                    _uiState.update {
                        it.copy(leftPanel = it.leftPanel.copy(
                            currentPath = dir.absolutePath,
                            entries = panelFiles,
                            isLoading = false,
                            breadcrumbs = buildLocalBreadcrumbs(dir),
                            isLocal = true
                        ))
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(leftPanel = it.leftPanel.copy(isLoading = false, error = e.message)) }
                }
            )
        }
    }

    fun navigateLocalUp(): Boolean {
        if (localBackStack.size <= 1) return false
        localBackStack.removeLast()  // Remove current entry
        val parent = localBackStack.last()  // Peek at parent without removing
        navigateLocal(parent)
        return true
    }

    fun canNavigateLocalUp(): Boolean = localBackStack.size > 1

    private fun buildLocalBreadcrumbs(dir: File): List<String> {
        val parts = mutableListOf<String>()
        var current: File? = dir
        while (current != null) {
            parts.add(0, current.name.ifEmpty { "/" })
            current = current.parentFile
        }
        return parts
    }

    // ── Remote panel navigation ─────────────────────────────────────────

    fun showProfilePicker() = _uiState.update { it.copy(showProfilePicker = true) }
    fun hideProfilePicker() = _uiState.update { it.copy(showProfilePicker = false) }

    fun connectToProfile(profile: ConnectionProfile) {
        _uiState.update { it.copy(showProfilePicker = false, remoteState = it.remoteState.copy(profile = profile, isConnecting = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val client: RemoteClient = when (profile.protocol) {
                Protocol.SFTP -> SftpClient()
                Protocol.FTP -> FtpClient()
                Protocol.FTPS -> FtpClient().also { it.useFtps(profile.ftpsImplicit) }
            }
            val result = when {
                profile.usePrivateKey && client is SftpClient -> {
                    val key = profileRepo.getPassword(profile) ?: ""
                    val passphrase = profileRepo.getPassphrase(profile)
                    client.connectWithKey(profile.host, profile.port, profile.username, key, passphrase)
                }
                profile.anonymous -> client.connectAnonymous(profile.host, profile.port)
                else -> {
                    val pass = profileRepo.getPassword(profile) ?: ""
                    client.connect(profile.host, profile.port, profile.username, pass)
                }
            }
            result.fold(
                onSuccess = {
                    remoteClient = client
                    val initialPath = when {
                        profile.initialRemotePath == "~" && client is SftpClient -> client.resolveHomePath()
                        profile.initialRemotePath == "~" -> "/"
                        else -> profile.initialRemotePath
                    }
                    _uiState.update { it.copy(remoteState = it.remoteState.copy(isConnecting = false, isConnected = true)) }
                    navigateRemote(initialPath)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(remoteState = it.remoteState.copy(isConnecting = false, error = e.message)) }
                }
            )
        }
    }

    fun navigateRemote(path: String) {
        val client = remoteClient ?: return
        if (path.contains("..")) {
            _uiState.update { it.copy(remoteState = it.remoteState.copy(error = "Access denied")) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(rightPanel = it.rightPanel.copy(isLoading = true, error = null)) }
            client.listDirectory(path).fold(
                onSuccess = { entries ->
                    val panelFiles = entries.map { f ->
                        PanelFile(f.name, f.path, f.isDirectory, f.size, f.lastModified, isLocal = false)
                    }
                    _uiState.update {
                        it.copy(
                            rightPanel = it.rightPanel.copy(
                                currentPath = path,
                                entries = panelFiles,
                                isLoading = false,
                                breadcrumbs = buildRemoteBreadcrumbs(path),
                                isLocal = false
                            ),
                            remoteState = it.remoteState.copy(currentPath = path)
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(rightPanel = it.rightPanel.copy(isLoading = false, error = e.message)) }
                }
            )
        }
    }

    fun navigateRemoteUp() {
        val current = _uiState.value.remoteState.currentPath
        if (current == "/" || current.isBlank()) return
        val parent = current.trimEnd('/').substringBeforeLast('/')
        navigateRemote(if (parent.isEmpty()) "/" else parent)
    }

    private fun buildRemoteBreadcrumbs(path: String): List<String> {
        if (path == "/") return listOf("/")
        return path.split("/").filter { it.isNotEmpty() }
    }

    // ── Transfer operations ─────────────────────────────────────────────

    /** Upload selected local files to the current remote directory. */
    fun uploadToRemote(localFiles: List<PanelFile>) {
        val client = remoteClient ?: return
        val remoteDir = _uiState.value.remoteState.currentPath
        for (file in localFiles) {
            if (file.isDirectory) {
                transferEngine.enqueueDirectoryUpload(client, file.path, "$remoteDir/${file.name}") {
                    viewModelScope.launch { navigateRemote(remoteDir) }
                }
            } else {
                val remotePath = "$remoteDir/${file.name}"
                transferEngine.enqueueUpload(client, file.path, remotePath) {
                    viewModelScope.launch { navigateRemote(remoteDir) }
                }
            }
        }
        _uiState.update { it.copy(transferMessage = "Transferring ${localFiles.size} item(s) to remote...") }
    }

    /** Download selected remote files to the current local directory. */
    fun downloadToLocal(remoteFiles: List<PanelFile>) {
        val client = remoteClient ?: return
        val localDir = File(_uiState.value.leftPanel.currentPath)
        for (file in remoteFiles) {
            if (file.isDirectory) {
                val destDir = File(localDir, file.name)
                transferEngine.enqueueDirectoryDownload(client, file.path, destDir.absolutePath) {
                    viewModelScope.launch { navigateLocal(localDir) }
                }
            } else {
                val localPath = File(localDir, file.name).absolutePath
                transferEngine.enqueueDownload(client, file.path, localPath, file.size) {
                    viewModelScope.launch { navigateLocal(localDir) }
                }
            }
        }
        _uiState.update { it.copy(transferMessage = "Transferring ${remoteFiles.size} item(s) to local...") }
    }

    fun clearTransferMessage() = _uiState.update { it.copy(transferMessage = null) }

    override fun onCleared() {
        super.onCleared()
        remoteClient?.disconnect()
    }
}
