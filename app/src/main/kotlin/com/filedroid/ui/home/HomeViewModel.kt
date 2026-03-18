package com.filedroid.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import com.filedroid.permission.PermissionManager
import com.filedroid.security.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class HomeUiState(
    val hasServerPassword: Boolean = false,
    val storagePermissionGranted: Boolean = false,
    /** Derived: true only when both hasServerPassword and storagePermissionGranted are true. */
    val canStartServer: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val credentialStore: CredentialStore,
    private val permissionManager: PermissionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-evaluate state — call after returning from permission flow or settings. */
    fun refresh() {
        val hasPassword = credentialStore.hasServerPassword()
        val hasPermission = permissionManager.isStoragePermissionGranted(context)
        _uiState.update {
            HomeUiState(
                hasServerPassword = hasPassword,
                storagePermissionGranted = hasPermission,
                canStartServer = hasPassword && hasPermission
            )
        }
    }
}
