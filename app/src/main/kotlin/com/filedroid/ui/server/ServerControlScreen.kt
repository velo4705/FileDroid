package com.filedroid.ui.server

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var ftpChecked by remember { mutableStateOf(true) }
    var sftpChecked by remember { mutableStateOf(true) }
    val anyRunning = uiState.ftpRunning || uiState.sftpRunning

    // M10 — tunnel settings
    var tunnelEnabled by remember { mutableStateOf(uiState.tunnelEnabled) }
    var relayUrl by remember { mutableStateOf(uiState.relayUrl) }
    var tunnelId by remember { mutableStateOf(uiState.tunnelId) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    var pendingStart by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingStart?.let { (ftp, sftp) -> viewModel.startServers(ftp, sftp) }
            pendingStart = null
        } else {
            val activity = context as? android.app.Activity
            val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.POST_NOTIFICATIONS
            )
            if (!canAskAgain) showSettingsDialog = true
            pendingStart = null
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Notification permission required") },
            text = { Text("FileDroid needs notification permission to show the server status while it runs in the background. Please enable it in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }

    fun requestStartServers(ftp: Boolean, sftp: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (alreadyGranted) {
                viewModel.startServers(ftp, sftp)
            } else {
                // Always try the system dialog first.
                // If permanently denied, the launcher result comes back false and we show Settings.
                pendingStart = ftp to sftp
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.startServers(ftp, sftp)
        }
    }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
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

                // M10 — tunnel configuration
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Remote Access Tunnel", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tunnelEnabled, onCheckedChange = { tunnelEnabled = it })
                    Text("Enable relay tunnel (mobile data access)")
                }
                if (tunnelEnabled) {
                    OutlinedTextField(
                        value = relayUrl,
                        onValueChange = { relayUrl = it },
                        label = { Text("Relay server URL") },
                        placeholder = { Text("wss://relay.example.com/ws") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tunnelId,
                        onValueChange = { tunnelId = it },
                        label = { Text("Tunnel ID") },
                        placeholder = { Text("my-device-123") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    viewModel.setTunnelConfig(tunnelEnabled, relayUrl, tunnelId)
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
                    onClick = { requestStartServers(ftpChecked, sftpChecked) },
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
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
