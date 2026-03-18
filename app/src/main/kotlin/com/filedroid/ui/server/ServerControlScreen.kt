package com.filedroid.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var ftpChecked by remember { mutableStateOf(true) }
    var sftpChecked by remember { mutableStateOf(true) }
    val anyRunning = uiState.ftpRunning || uiState.sftpRunning

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Control") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleSmall)
                    StatusRow("FTP", uiState.ftpRunning, uiState.ftpPort)
                    StatusRow("SFTP", uiState.sftpRunning, uiState.sftpPort)
                    if (uiState.rootPath.isNotEmpty()) {
                        Text("Root: ${uiState.rootPath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Protocol toggles (only when stopped)
            if (!anyRunning) {
                Text("Enable protocols:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ftpChecked, onCheckedChange = { ftpChecked = it })
                    Text("FTP  (port ${uiState.ftpPort})")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sftpChecked, onCheckedChange = { sftpChecked = it })
                    Text("SFTP  (port ${uiState.sftpPort})")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (anyRunning) {
                Button(
                    onClick = { viewModel.stopServers() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Servers")
                }
            } else {
                Button(
                    onClick = { viewModel.startServers(ftpChecked, sftpChecked) },
                    enabled = ftpChecked || sftpChecked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Servers")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, running: Boolean, port: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Badge(containerColor = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) {}
        Text("$label :$port — ${if (running) "Running" else "Stopped"}",
            style = MaterialTheme.typography.bodyMedium)
    }
}
