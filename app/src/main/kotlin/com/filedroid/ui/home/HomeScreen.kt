package com.filedroid.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.filedroid.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToLocalBrowser: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToTransfers: () -> Unit,
    onNavigateToServer: () -> Unit,
    onNavigateToSsh: () -> Unit,
    onNavigateToTunnel: () -> Unit,
    onNavigateToDualPanel: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by updateViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Re-check immediately when this screen first appears (e.g. after first-launch dialog)
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Auto-check for updates on first launch
    LaunchedEffect(Unit) { updateViewModel.checkForUpdates() }

    // Re-check on every resume (covers returning from Settings permission page)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launcher for normal READ/WRITE permissions (Android < 11)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FileDroid") },
                actions = {
                    IconButton(onClick = { updateViewModel.checkForUpdates() }) {
                        Icon(Icons.Default.CloudQueue, contentDescription = "Check for updates")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Network status ───────────────────────────────────────────
            if (uiState.localIp.isNotEmpty() || uiState.publicIp.isNotEmpty()) {
                var ipsVisible by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Network", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            TextButton(onClick = { ipsVisible = !ipsVisible }) {
                                Text(if (ipsVisible) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (ipsVisible) {
                            if (uiState.localIp.isNotEmpty()) {
                                IpRow("Local (same network)", uiState.localIp) {
                                    clipboard.setText(AnnotatedString(uiState.localIp))
                                }
                            }
                            if (uiState.publicIp.isNotEmpty()) {
                                IpRow("Public (outside network)", uiState.publicIp) {
                                    clipboard.setText(AnnotatedString(uiState.publicIp))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = onNavigateToLocalBrowser,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse Files")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateToProfiles,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudQueue, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect to Remote")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateToTransfers,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Transfers")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateToSsh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SSH Manager")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateToTunnel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudQueue, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Relay Tunnel")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateToDualPanel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ViewStream, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dual Panel")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToServer,
                enabled = uiState.canStartServer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Server")
            }

            if (!uiState.canStartServer) {
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    !uiState.hasServerPassword -> {
                        Text(
                            "Set a server password in Settings first.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    !uiState.storagePermissionGranted -> {
                        Text(
                            "Storage permission required.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                // Android 11+ — send to special "All files access" settings page
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                // Android 10 and below — normal runtime permission request
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }

    // Update dialog
    if (updateState.showDialog && updateState.updateInfo != null) {
        val info = updateState.updateInfo!!
        if (info.isUpdateAvailable) {
            AlertDialog(
                onDismissRequest = { updateViewModel.dismissDialog() },
                title = { Text("Update available") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Version ${info.latestVersion} is available. (Current: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName})")
                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                info.releaseNotes.take(300),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (info.downloadUrl != null) updateViewModel.openDownload()
                        else updateViewModel.openReleasesPage()
                        updateViewModel.dismissDialog()
                    }) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = { updateViewModel.openReleasesPage(); updateViewModel.dismissDialog() }) {
                        Text("View release")
                    }
                }
            )
        } else {
            val error = updateState.error
            if (error != null && error.contains("latest")) {
                // "You're on the latest version" info
                AlertDialog(
                    onDismissRequest = { updateViewModel.clearError(); updateViewModel.dismissDialog() },
                    title = { Text("Up to date") },
                    text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { updateViewModel.clearError(); updateViewModel.dismissDialog() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun IpRow(label: String, ip: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(ip, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
        }
    }
}
