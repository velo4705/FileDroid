package com.filedroid.ui.remote

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.remote.RemoteFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteBrowserScreen(
    profileId: Long,
    onNavigateBack: () -> Unit,
    onOpenFile: (path: String) -> Unit = {},
    onNewFile: (dir: String) -> Unit = {},
    viewModel: RemoteBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.profile
    var newFolderName by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var showNewFileDialog by remember { mutableStateOf(false) }

    val displayedEntries = remember(uiState.entries, searchQuery) {
        if (searchQuery.isBlank()) uiState.entries
        else uiState.entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.uploadFile(it) } }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { viewModel.uploadFolder(it) } }

    // Connect on first composition
    LaunchedEffect(profileId) {
        if (!uiState.isConnected && !uiState.isConnecting) {
            viewModel.loadAndConnect(profileId)
        }
    }

    Scaffold(
        topBar = {
            if (searchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search files…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searchActive = false; searchQuery = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            } else {
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
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { filePicker.launch("*/*") }) {
                            Icon(Icons.Default.Upload, contentDescription = "Upload file")
                        }
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Upload folder")
                        }
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "New folder")
                        }
                        IconButton(onClick = { showNewFileDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New file")
                        }
                        IconButton(onClick = { viewModel.navigateTo(uiState.currentPath) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            }
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
                        Text("Connecting to ${profile?.host ?: "…"}…")
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
                            // ".." parent directory entry
                            if (uiState.currentPath.isNotBlank() && uiState.currentPath != "/" && !searchActive) {
                                item {
                                    ParentDirRow(onClick = { viewModel.navigateUp() })
                                    HorizontalDivider()
                                }
                            }
                            if (displayedEntries.isEmpty() && searchQuery.isNotBlank()) {
                                item {
                                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No results for \"$searchQuery\"",
                                            color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                            items(displayedEntries, key = { it.path }) { file ->
                                RemoteFileRow(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) viewModel.navigateTo(file.path)
                                        else onOpenFile(file.path)
                                    },
                                    onDownload = { viewModel.download(file) },
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

            // Download queued toast
            if (uiState.downloadedToPath != null) {
                LaunchedEffect(uiState.downloadedToPath) {
                    kotlinx.coroutines.delay(2500)
                    viewModel.clearDownloadedToast()
                }
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                ) { Text("Download queued → Downloads/") }
            }

            // Upload queued toast
            if (uiState.uploadedFileName != null) {
                LaunchedEffect(uiState.uploadedFileName) {
                    kotlinx.coroutines.delay(2500)
                    viewModel.clearUploadedToast()
                }
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                ) { Text("Uploading ${uiState.uploadedFileName}…") }
            }
        }
    }

    // Auto-focus search field when activated
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    // Reconnect prompt (R2.7)
    if (uiState.showReconnectPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissReconnectPrompt() },
            title = { Text("Connection lost") },
            text = { Text("The connection to ${uiState.profile?.host} was dropped. Reconnect?") },
            confirmButton = {
                TextButton(onClick = { viewModel.reconnect() }) { Text("Reconnect") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissReconnectPrompt() }) { Text("Dismiss") }
            }
        )
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

    // New file dialog
    if (showNewFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New File") },
            text = {
                OutlinedTextField(value = newFileName, onValueChange = { newFileName = it },
                    label = { Text("File name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewFileDialog = false
                        val path = "${uiState.currentPath.trimEnd('/')}/$newFileName"
                        onNewFile(path)
                    },
                    enabled = newFileName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RemoteFileRow(
    file: RemoteFile,
    onClick: () -> Unit,
    onDownload: () -> Unit,
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
                DropdownMenuItem(
                    text = { Text(if (file.isDirectory) "Download folder" else "Download") },
                    onClick = { showMenu = false; onDownload() },
                    leadingIcon = { Icon(Icons.Default.Download, null) }
                )
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

@Composable
private fun ParentDirRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text("..", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}
