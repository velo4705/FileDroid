package com.filedroid.update

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val error: String? = null,
    val showDialog: Boolean = false
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** Current app version code from PackageManager. */
    private val currentVersionCode: Int
        get() = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()

    /** Current app version name from PackageManager. */
    private val currentVersionName: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

    fun checkForUpdates() {
        _uiState.update { it.copy(isChecking = true, error = null) }
        viewModelScope.launch {
            updateChecker.checkForUpdates(
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName
            ).fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            updateInfo = info,
                            showDialog = true
                        )
                    }
                    if (!info.isUpdateAvailable) {
                        _uiState.update { it.copy(error = "You're on the latest version.") }
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isChecking = false, error = "Failed to check for updates: ${e.message}")
                    }
                }
            )
        }
    }

    fun openReleasesPage() {
        val url = _uiState.value.updateInfo?.releaseUrl ?: return
        updateChecker.openReleasesPage(context, url)
    }

    fun openDownload() {
        val url = _uiState.value.updateInfo?.downloadUrl ?: return
        updateChecker.openDownload(context, url)
    }

    fun dismissDialog() = _uiState.update { it.copy(showDialog = false) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
