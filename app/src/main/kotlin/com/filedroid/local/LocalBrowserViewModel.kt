package com.filedroid.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class LocalBrowserUiState(
    val currentPath: String = "",
    val breadcrumbs: List<String> = emptyList(),
    val entries: List<LocalFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val termuxAvailable: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val selectedFile: LocalFile? = null,
    val selectedPaths: Set<String> = emptySet(),
    val searchQuery: String = ""
)

@HiltViewModel
class LocalBrowserViewModel @Inject constructor(
    private val repo: LocalFileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalBrowserUiState())
    val uiState: StateFlow<LocalBrowserUiState> = _uiState.asStateFlow()

    // Back-stack for navigation
    private val backStack = ArrayDeque<File>()

    init {
        val root = repo.getDefaultRoot()
        _uiState.update { it.copy(termuxAvailable = repo.getTermuxHome() != null) }
        navigateTo(root)
    }

    fun navigateTo(dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.listDirectory(dir).fold(
                onSuccess = { entries ->
                    backStack.addLast(dir)
                    _uiState.update {
                        it.copy(
                            currentPath = dir.absolutePath,
                            breadcrumbs = buildBreadcrumbs(dir),
                            entries = entries,
                            isLoading = false
                        )
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
        val parent = backStack.removeLast() // will be re-added by navigateTo
        navigateTo(parent)
        return true
    }

    fun canNavigateUp(): Boolean = backStack.size > 1

    fun navigateToTermux() {
        repo.getTermuxHome()?.let { navigateTo(it) }
    }

    fun createFolder(name: String) {
        val current = File(_uiState.value.currentPath)
        viewModelScope.launch(Dispatchers.IO) {
            repo.createDirectory(current, name).fold(
                onSuccess = { refresh() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
        dismissDialogs()
    }

    fun rename(newName: String) {
        val file = _uiState.value.selectedFile?.file ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.rename(file, newName).fold(
                onSuccess = { refresh() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
        dismissDialogs()
    }

    fun delete() {
        val file = _uiState.value.selectedFile?.file ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(file).fold(
                onSuccess = { refresh() },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
        dismissDialogs()
    }

    fun showCreateFolder() = _uiState.update { it.copy(showCreateFolderDialog = true) }
    fun showRename(file: LocalFile) = _uiState.update { it.copy(showRenameDialog = true, selectedFile = file) }
    fun showDeleteConfirm(file: LocalFile) = _uiState.update { it.copy(showDeleteConfirm = true, selectedFile = file) }
    fun dismissDialogs() = _uiState.update {
        it.copy(showCreateFolderDialog = false, showRenameDialog = false, showDeleteConfirm = false, selectedFile = null)
    }
    fun clearError() = _uiState.update { it.copy(error = null) }

    // Multi-select (R1.4)
    fun toggleSelection(file: LocalFile) {
        _uiState.update { state ->
            val current = state.selectedPaths.toMutableSet()
            if (file.path in current) current.remove(file.path) else current.add(file.path)
            state.copy(selectedPaths = current)
        }
    }

    fun clearSelection() = _uiState.update { it.copy(selectedPaths = emptySet()) }

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun getSelectedFiles(): List<LocalFile> {
        val paths = _uiState.value.selectedPaths
        return _uiState.value.entries.filter { it.path in paths }
    }

    private fun refresh() = navigateTo(File(_uiState.value.currentPath))

    private fun buildBreadcrumbs(dir: File): List<String> {
        val parts = mutableListOf<String>()
        var current: File? = dir
        while (current != null) {
            parts.add(0, current.name.ifEmpty { "/" })
            current = current.parentFile
        }
        return parts
    }
}
