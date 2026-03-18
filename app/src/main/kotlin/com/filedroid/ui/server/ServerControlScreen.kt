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

            // Protocol toggles + interface picker (only when stopped)
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

                // R7.4 — network interface picker
                if (uiState.availableInterfaces.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Bind to interface:", style = MaterialTheme.typography.labelMedium)
                    InterfacePicker(
                        interfaces = uiState.availableInterfaces,
                        selected = uiState.bindAddress,
                        onSelect = { viewModel.setBindAddress(it) }
                    )
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

/** R7.4 — dropdown to pick a network interface or "All interfaces". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterfacePicker(
    interfaces: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = "All interfaces (0.0.0.0)"
    val displayLabel = if (selected.isBlank()) allLabel
    else interfaces.firstOrNull { it.second == selected }?.first ?: selected

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Network interface") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = { onSelect(""); expanded = false }
            )
            interfaces.forEach { (label, ip) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(ip); expanded = false }
                )
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
