package com.filedroid.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.permission.PermissionManager
import com.filedroid.security.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import javax.inject.Inject

data class HomeUiState(
    val hasServerPassword: Boolean = false,
    val storagePermissionGranted: Boolean = false,
    val canStartServer: Boolean = false,
    val localIp: String = "",
    val publicIp: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val credentialStore: CredentialStore,
    private val permissionManager: PermissionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = fetchIps()
        override fun onLost(network: Network) = fetchIps()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = fetchIps()
    }

    init {
        refresh()
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().build(),
            networkCallback
        )
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    fun refresh() {
        val hasPassword = credentialStore.hasServerPassword()
        val hasPermission = permissionManager.isStoragePermissionGranted(context)
        _uiState.update {
            it.copy(
                hasServerPassword = hasPassword,
                storagePermissionGranted = hasPermission,
                canStartServer = hasPassword && hasPermission
            )
        }
        fetchIps()
    }

    private fun fetchIps() {
        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { getLocalIp() }
            _uiState.update { it.copy(localIp = local) }
            val public = withContext(Dispatchers.IO) { getPublicIp() }
            _uiState.update { it.copy(publicIp = public) }
        }
    }

    private fun getLocalIp(): String = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { it is Inet4Address }
            ?.hostAddress ?: ""
    }.getOrDefault("")

    private fun getPublicIp(): String = runCatching {
        URL("https://api4.my-ip.io/ip").readText().trim()
    }.getOrDefault("")
}
