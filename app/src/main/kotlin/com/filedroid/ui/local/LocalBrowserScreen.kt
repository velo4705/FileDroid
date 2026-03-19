package com.filedroid.ui.local

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.local.LocalBrowserViewModel
import com.filedroid.local.LocalFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBrowserScreen(
    onNavigateBack: () -> Unit,
    viewModel: LocalBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inSelectionMode = uiState.selectedPaths.isNotEmpty()
    var searchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Filter entries by search query
    val displayedEntries = remember(uiState.entries, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) uiState.entries
        else uiState.entries.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }
    }

    BackHandler {
        when {
            searchActive -> { searchActive = false; viewModel.setSearchQuery("") }
            inSelectionMode -> viewModel.clearSelection()
            else -> if (!viewModel.navigateUp()) onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            when {
                searchActive -> SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onClose = { searchActive = false; viewModel.setSearchQuery("") },
                    focusRequester = focusRequester
                )
                inSelectionMode -> TopAppBar(
                    title = { Text("${uiState.selectedPaths.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* upload wired via TransferEngine in dual-panel */ }) {
                            Icon(Icons.Default.Upload, contentDescription = "Upload selected")
                        }
                    }
                )
                else -> TopAppBar(
                    title = {
                        BreadcrumbRow(
                            breadcrumbs = uiState.breadcrumbs,
                            currentPath = uiState.currentPath
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (!viewModel.navigateUp()) onNavigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        if (uiState.termuxAvailable) {
                            IconButton(onClick = { viewModel.navigateToTermux() }) {
                                Icon(Icons.Default.Terminal, contentDescription = "Termux storage")
                            }
                        }
                        IconButton(onClick = { viewModel.showCreateFolder() }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                        }
                    }
                )
            }
        },
        snackbarHost = {
            uiState.error?.let { error ->
                LaunchedEffect(error) { viewModel.clearError() }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> ErrorMessage(uiState.error!!) { viewModel.clearError() }
                displayedEntries.isEmpty() && searchActive ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results for \"${uiState.searchQuery}\"",
                            color = MaterialTheme.colorScheme.outline)
                    }
                displayedEntries.isEmpty() -> EmptyFolder()
                else -> FileList(
                    entries = displayedEntries,
                    selectedPaths = uiState.selectedPaths,
                    onEntryClick = { file ->
                        if (inSelectionMode) viewModel.toggleSelection(file)
                        else if (file.isDirectory) viewModel.navigateTo(file.file)
                    },
                    onLongPress = { file -> viewModel.toggleSelection(file) },
                    onRename = { viewModel.showRename(it) },
                    onDelete = { viewModel.showDeleteConfirm(it) },
                    onNavigateUp = if (!searchActive && viewModel.canNavigateUp()) ({ viewModel.navigateUp() }) else null
                )
            }
        }
    }

    // Auto-focus search field when activated
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    // Dialogs
    if (uiState.showCreateFolderDialog) {
        InputDialog(
            title = "New Folder",
            label = "Folder name",
            onConfirm = { viewModel.createFolder(it) },
            onDismiss = { viewModel.dismissDialogs() }
        )
    }

    if (uiState.showRenameDialog) {
        InputDialog(
            title = "Rename",
            label = "New name",
            initialValue = uiState.selectedFile?.name ?: "",
            onConfirm = { viewModel.rename(it) },
            onDismiss = { viewModel.dismissDialogs() }
        )
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialogs() },
            title = { Text("Delete") },
            text = { Text("Delete \"${uiState.selectedFile?.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDialogs() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search files…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        }
    )
}

@Composable
private fun BreadcrumbRow(breadcrumbs: List<String>, currentPath: String) {
    LazyRow(verticalAlignment = Alignment.CenterVertically) {
        items(breadcrumbs) { crumb ->
            Text(
                text = crumb,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            if (crumb != breadcrumbs.last()) {
                Text("/", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun FileList(
    entries: List<LocalFile>,
    selectedPaths: Set<String>,
    onEntryClick: (LocalFile) -> Unit,
    onLongPress: (LocalFile) -> Unit,
    onRename: (LocalFile) -> Unit,
    onDelete: (LocalFile) -> Unit,
    onNavigateUp: (() -> Unit)? = null
) {
    LazyColumn {
        // ".." parent directory entry
        if (onNavigateUp != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateUp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("..", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider()
            }
        }
        items(entries, key = { it.path }) { file ->
            FileRow(
                file = file,
                isSelected = file.path in selectedPaths,
                onClick = { onEntryClick(file) },
                onLongPress = { onLongPress(file) },
                onRename = { onRename(file) },
                onDelete = { onDelete(file) }
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: LocalFile,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(Icons.Default.CheckBox, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        } else {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (file.isDirectory) "Folder" else "${formatSize(file.size)}  •  ${formatDate(file.lastModified)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
            }
        }
    }
}

@Composable
private fun EmptyFolder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Empty folder", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ErrorMessage(message: String, onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@Composable
private fun InputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
