package com.filedroid.ui.remote

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
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
import com.filedroid.transfer.TransferJob
import com.filedroid.transfer.TransferStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    val downloadedToPath: String? = null,
    val uploadedFileName: String? = null,
    val activeUploads: List<TransferJob> = emptyList()
)

@HiltViewModel
class RemoteBrowserViewModel @Inject constructor(
    private val profileRepo: ConnectionProfileRepository,
    private val transferEngine: TransferEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteBrowserUiState())
    val uiState: StateFlow<RemoteBrowserUiState> = _uiState.asStateFlow()

    private var client: RemoteClient? = null
    private val backStack = ArrayDeque<String>()

    init {
        // Keep activeUploads in sync with TransferEngine so the UI can show progress
        transferEngine.jobs
            .onEach { jobs ->
                _uiState.update { it.copy(activeUploads = jobs.filter { j -> j.status == TransferStatus.IN_PROGRESS }) }
            }
            .launchIn(viewModelScope)
    }

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
                Protocol.FTP  -> FtpClient()
                Protocol.FTPS -> FtpClient().also { it.useFtps(implicit = profile.ftpsImplicit) }
            }
            val result = when {
                profile.anonymous -> remoteClient.connectAnonymous(profile.host, profile.port)
                profile.usePrivateKey && remoteClient is SftpClient -> {
                    val privateKey = profileRepo.getPassword(profile) ?: ""
                    val passphrase = profileRepo.getPassphrase(profile)
                    remoteClient.connectWithKey(
                        profile.host, profile.port, profile.username,
                        privateKey, passphrase
                    )
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
                    // Resolve "~" to the actual home directory on the server
                    val initialPath = when {
                        profile.initialRemotePath == "~" && remoteClient is SftpClient ->
                            remoteClient.resolveHomePath()
                        profile.initialRemotePath == "~" ->
                            "/" // FTP has no home dir concept, fall back to root
                        else -> profile.initialRemotePath
                    }
                    navigateTo(initialPath)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isConnecting = false, error = e.message) }
                }
            )
        }
    }

    fun navigateTo(path: String) {
        if (path.contains("..")) {
            _uiState.update { it.copy(error = "Access denied: invalid path") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = client?.listDirectory(path)
            if (result == null) {
                _uiState.update { it.copy(isLoading = false, showReconnectPrompt = true) }
                return@launch
            }
            result.fold(
                onSuccess = { entries ->
                    backStack.addLast(path)
                    _uiState.update { it.copy(currentPath = path, entries = entries, isLoading = false) }
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
        if (file.isDirectory) {
            // Recursive folder download
            val localDir = File(destDir, file.name)
            transferEngine.enqueueDirectoryDownload(c, file.path, localDir.absolutePath)
        } else {
            val localPath = File(destDir, file.name).absolutePath
            transferEngine.enqueueDownload(c, file.path, localPath, file.size)
        }
        _uiState.update { it.copy(downloadedToPath = file.name) }
    }

    /** Upload a single file picked from the device. */
    fun uploadFile(uri: Uri) {
        val c = client ?: return
        val fileName = resolveFileName(uri)
        val remotePath = _uiState.value.currentPath.trimEnd('/') + "/$fileName"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tmp = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                transferEngine.enqueueUpload(c, tmp.absolutePath, remotePath) {
                    // Refresh directory listing once upload completes
                    viewModelScope.launch { refresh() }
                }
                _uiState.update { it.copy(uploadedFileName = fileName) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Upload failed: ${e.message}") }
            }
        }
    }

    /** Upload a folder tree picked from the device (URI is a document tree). */
    fun uploadFolder(uri: Uri) {
        val c = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                // Use the actual folder name from the document tree
                val folderName = doc?.name ?: "folder_${System.currentTimeMillis()}"
                val tmpDir = File(context.cacheDir, folderName)
                // Copy contents directly into tmpDir (not into a sub-folder)
                tmpDir.mkdirs()
                doc?.listFiles()?.forEach { child ->
                    copyDocumentFile(child, tmpDir)
                }
                val remotePath = _uiState.value.currentPath.trimEnd('/') + "/$folderName"
                transferEngine.enqueueDirectoryUpload(c, tmpDir.absolutePath, remotePath) {
                    viewModelScope.launch { refresh() }
                }
                _uiState.update { it.copy(uploadedFileName = "$folderName/") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Folder upload failed: ${e.message}") }
            }
        }
    }

    /** Resolve the human-readable display name from a content URI. */
    private fun resolveFileName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment?.substringAfterLast("/")
            ?: "upload_${System.currentTimeMillis()}"
    }

    private fun copyDocumentFile(
        doc: androidx.documentfile.provider.DocumentFile,
        destDir: File
    ) {
        if (doc.isDirectory) {
            val subDir = File(destDir, doc.name ?: "folder")
            subDir.mkdirs()
            doc.listFiles().forEach { copyDocumentFile(it, subDir) }
        } else {
            val dest = File(destDir, doc.name ?: "file")
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }
    }

    fun clearUploadedToast() = _uiState.update { it.copy(uploadedFileName = null) }
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
