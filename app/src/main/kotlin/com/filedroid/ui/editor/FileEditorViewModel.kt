package com.filedroid.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.remote.RemoteClient
import com.filedroid.remote.SftpClient
import com.filedroid.remote.FtpClient
import com.filedroid.data.ConnectionProfileRepository
import com.filedroid.data.Protocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

data class FileEditorUiState(
    val fileName: String = "",
    val content: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val error: String? = null,
    val savedToast: Boolean = false,
    val showDiscardDialog: Boolean = false
)

/**
 * Handles both local and remote file editing.
 *
 * Nav args (all strings via SavedStateHandle):
 *   - "localPath"   — absolute local path (mutually exclusive with remote args)
 *   - "profileId"   — remote profile id (Long as String)
 *   - "remotePath"  — remote file path
 *   - "isNew"       — "true" if creating a new file
 */
@HiltViewModel
class FileEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val profileRepo: ConnectionProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileEditorUiState())
    val uiState: StateFlow<FileEditorUiState> = _uiState.asStateFlow()

    private var localFile: File? = null
    private var remoteClient: RemoteClient? = null
    private var remotePath: String? = null
    private var originalContent: String = ""

    init {
        val localPath = savedState.get<String>("path")
        val profileIdStr = savedState.get<String>("profileId")
        val remPath = savedState.get<String>("remotePath")
        val isNew = savedState.get<String>("isNew") == "true"

        when {
            localPath != null -> loadLocal(localPath, isNew)
            profileIdStr != null && remPath != null ->
                loadRemote(profileIdStr.toLong(), remPath, isNew)
            else -> _uiState.update { it.copy(isLoading = false, error = "No file specified") }
        }
    }

    private fun loadLocal(path: String, isNew: Boolean) {
        val file = File(path)
        localFile = file
        _uiState.update { it.copy(fileName = file.name) }
        if (isNew) {
            _uiState.update { it.copy(isLoading = false, content = "", isDirty = false) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (!file.exists() || file.length() > MAX_EDIT_BYTES)
                    error("File too large to edit (max ${MAX_EDIT_BYTES / 1024}KB)")
                file.readText()
            }.fold(
                onSuccess = { text ->
                    originalContent = text
                    _uiState.update { it.copy(isLoading = false, content = text) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    private fun loadRemote(profileId: Long, path: String, isNew: Boolean) {
        remotePath = path
        _uiState.update { it.copy(fileName = path.substringAfterLast("/")) }
        viewModelScope.launch(Dispatchers.IO) {
            val profile = profileRepo.getById(profileId)
            if (profile == null) {
                _uiState.update { it.copy(isLoading = false, error = "Profile not found") }
                return@launch
            }
            val client: RemoteClient = when (profile.protocol) {
                Protocol.SFTP -> SftpClient()
                Protocol.FTP  -> FtpClient()
                Protocol.FTPS -> FtpClient().also { it.useFtps() }
            }
            val password = profileRepo.getPassword(profile) ?: ""
            val connResult = if (profile.anonymous)
                client.connectAnonymous(profile.host, profile.port)
            else
                client.connect(profile.host, profile.port, profile.username, password)

            connResult.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = "Connect failed: ${e.message}") }
                return@launch
            }
            remoteClient = client

            if (isNew) {
                _uiState.update { it.copy(isLoading = false, content = "", isDirty = false) }
                return@launch
            }

            val buf = ByteArrayOutputStream()
            client.download(path, buf).fold(
                onSuccess = {
                    val text = buf.toString(Charsets.UTF_8.name())
                    if (text.length > MAX_EDIT_CHARS)
                        _uiState.update { it.copy(isLoading = false, error = "File too large to edit") }
                    else {
                        originalContent = text
                        _uiState.update { it.copy(isLoading = false, content = text) }
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    fun onContentChange(text: String) {
        _uiState.update { it.copy(content = text, isDirty = text != originalContent) }
    }

    fun save() {
        val content = _uiState.value.content
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = localFile?.let { file ->
                runCatching { file.writeText(content) }
            } ?: remoteClient?.let { client ->
                val bytes = content.toByteArray(Charsets.UTF_8)
                client.upload(bytes.inputStream(), remotePath!!)
            } ?: Result.failure(Exception("No target"))

            result.fold(
                onSuccess = {
                    originalContent = content
                    _uiState.update { it.copy(isSaving = false, isDirty = false, savedToast = true) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSaving = false, error = "Save failed: ${e.message}") }
                }
            )
        }
    }

    fun showDiscardDialog() = _uiState.update { it.copy(showDiscardDialog = true) }
    fun dismissDiscardDialog() = _uiState.update { it.copy(showDiscardDialog = false) }
    fun clearSavedToast() = _uiState.update { it.copy(savedToast = false) }

    override fun onCleared() {
        super.onCleared()
        remoteClient?.disconnect()
    }

    companion object {
        private const val MAX_EDIT_BYTES = 512 * 1024L   // 512 KB
        private const val MAX_EDIT_CHARS = 512 * 1024
    }
}
