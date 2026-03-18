package com.filedroid.ui.ssh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filedroid.ssh.SshSession
import com.filedroid.ssh.SshSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TabState(
    val session: SshSession,
    val buffer: String = "",
    val connecting: Boolean = false,
    val error: String? = null
)

data class SshUiState(
    val tabs: List<TabState> = emptyList(),
    val activeTabIndex: Int = 0
)

@HiltViewModel
class SshTerminalViewModel @Inject constructor(
    private val sessionManager: SshSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SshUiState())
    val uiState: StateFlow<SshUiState> = _uiState.asStateFlow()

    fun openSession(host: String, port: Int, username: String, password: String) {
        val session = sessionManager.createSession("$username@$host")
        val tab = TabState(session = session, connecting = true)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + tab,
                activeTabIndex = state.tabs.size
            )
        }

        viewModelScope.launch {
            val result = session.connect(host, port, username, password)
            updateTab(session.id) { it.copy(connecting = false, error = result.exceptionOrNull()?.message) }

            if (result.isSuccess) {
                session.output.collect { chunk ->
                    updateTab(session.id) { it.copy(buffer = it.buffer + chunk) }
                }
            }
        }
    }

    fun sendInput(sessionId: String, text: String) {
        sessionManager.getSession(sessionId)?.send(text)
    }

    fun closeTab(sessionId: String) {
        sessionManager.closeSession(sessionId)
        _uiState.update { state ->
            val newTabs = state.tabs.filter { it.session.id != sessionId }
            val newIndex = (state.activeTabIndex).coerceAtMost((newTabs.size - 1).coerceAtLeast(0))
            state.copy(tabs = newTabs, activeTabIndex = newIndex)
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(activeTabIndex = index) }
    }

    private fun updateTab(sessionId: String, transform: (TabState) -> TabState) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.session.id == sessionId) transform(it) else it })
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionManager.closeAll()
    }
}
