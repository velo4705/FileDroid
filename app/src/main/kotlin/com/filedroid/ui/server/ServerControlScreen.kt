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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.tunnel.ConnectionCode
import com.filedroid.tunnel.TunnelConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var ftpChecked by remember { mutableStateOf(true) }
    var sftpChecked by remember { mutableStateOf(true) }
    val anyRunning = uiState.ftpRunning || uiState.sftpRunning

    // Remote access code — auto-generated, shareable
    var tunnelEnabled by remember { mutableStateOf(uiState.tunnelEnabled) }
    var connectionCode by remember { mutableStateOf(uiState.tunnelId.ifBlank { ConnectionCode.generate() }) }

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

            // Remote access tunnel status
            val relayHost = com.filedroid.tunnel.TunnelConfig.DEFAULT_RELAY_URL
                .removePrefix("ws://").removePrefix("wss://").removeSuffix("/ws")
            if (anyRunning && tunnelEnabled) {
                if (uiState.publicPorts.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Remote Access Active", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Share the connection code from the Home screen with another FileDroid device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            uiState.publicPorts.forEach { (protocol, publicPort) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "$protocol → $relayHost:$publicPort",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString("$relayHost:$publicPort"))
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Remote Access", style = MaterialTheme.typography.titleSmall)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Connecting to relay...", style = MaterialTheme.typography.labelSmall)
                        }
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

                // Remote access — share code with other devices
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Remote Access", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tunnelEnabled, onCheckedChange = {
                        tunnelEnabled = it
                        if (it && connectionCode.isBlank()) connectionCode = ConnectionCode.generate()
                    })
                    Text("Access this device from anywhere")
                }
                if (tunnelEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Share this code with the other device:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Connection code display
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = connectionCode,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(connectionCode))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy code")
                                }
                                IconButton(onClick = {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, "Connect to my FileDroid with code: $connectionCode")
                                        type = "text/plain"
                                    }
                                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share code"))
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share code")
                                }
                            }
                        }
                    }
                    TextButton(onClick = { connectionCode = ConnectionCode.generate() }) {
                        Text("Generate new code")
                    }
                    // Save the config
                    viewModel.setTunnelConfig(tunnelEnabled, TunnelConfig.DEFAULT_RELAY_URL, connectionCode)
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
