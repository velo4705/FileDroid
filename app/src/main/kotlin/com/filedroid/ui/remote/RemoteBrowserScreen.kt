package com.filedroid.ui.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.remote.RemoteFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteBrowserScreen(
    profileId: Long,
    onNavigateBack: () -> Unit,
    viewModel: RemoteBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.profile
    var newFolderName by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // Connect on first composition
    LaunchedEffect(profileId) {
        if (!uiState.isConnected && !uiState.isConnecting) {
            viewModel.loadAndConnect(profileId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(profile?.label ?: "Remote Browser", style = MaterialTheme.typography.titleMedium)
                        Text(
                            uiState.currentPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) {
                            viewModel.disconnect()
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                    }
                    IconButton(onClick = { viewModel.navigateTo(uiState.currentPath) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isConnecting -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Connecting to ${profile.host}…")
                    }
                }
                uiState.error != null && !uiState.isConnected -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null,
                            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(uiState.error ?: "Connection failed", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { profile?.let { viewModel.connect(it) } }) { Text("Retry") }
                    }
                }
                else -> {
                    if (uiState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                    if (uiState.entries.isEmpty() && !uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Empty directory", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.entries, key = { it.path }) { file ->
                                RemoteFileRow(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) viewModel.navigateTo(file.path)
                                    },
                                    onRename = { viewModel.showRename(file) },
                                    onDelete = { viewModel.showDeleteConfirm(file) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // Snackbar for non-fatal errors
            if (uiState.error != null && uiState.isConnected) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }
                ) { Text(uiState.error ?: "") }
            }
        }
    }

    // Rename dialog
    if (uiState.showRenameDialog) {
        var newName by remember { mutableStateOf(uiState.selectedFile?.name ?: "") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialogs() },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it },
                    label = { Text("New name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.rename(newName) }, enabled = newName.isNotBlank()) {
                    Text("Rename")
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissDialogs() }) { Text("Cancel") } }
        )
    }

    // Delete confirm dialog
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialogs() },
            title = { Text("Delete") },
            text = { Text("Delete \"${uiState.selectedFile?.name}\"?") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissDialogs() }) { Text("Cancel") } }
        )
    }

    // New folder dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it },
                    label = { Text("Folder name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createDirectory(newFolderName); showNewFolderDialog = false; newFolderName = "" },
                    enabled = newFolderName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RemoteFileRow(
    file: RemoteFile,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium)
            if (!file.isDirectory) {
                Text(formatSize(file.size), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() },
                    leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null) })
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
