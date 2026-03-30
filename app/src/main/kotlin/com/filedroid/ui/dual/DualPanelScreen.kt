package com.filedroid.ui.dual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.data.ConnectionProfile
import com.filedroid.ui.profiles.ProfileListScreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualPanelScreen(
    onNavigateBack: () -> Unit,
    viewModel: DualPanelViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Snackbar for transfer messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.transferMessage) {
        uiState.transferMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTransferMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual Panel") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.remoteState.isConnected) {
                        Text(
                            uiState.remoteState.profile?.label ?: "Remote",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    } else {
                        TextButton(onClick = { viewModel.showProfilePicker() }) {
                            Text("Connect", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Left panel: Local files ──────────────────────────────
            FilePanel(
                state = uiState.leftPanel,
                title = "Local",
                onEntryClick = { file ->
                    if (file.isDirectory) viewModel.navigateLocal(java.io.File(file.path))
                },
                onNavigateUp = if (viewModel.canNavigateLocalUp()) ({ viewModel.navigateLocalUp() }) else null,
                onDragStart = { /* handled by long press drag */ },
                modifier = Modifier.weight(1f),
                panelColor = MaterialTheme.colorScheme.surface
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // ── Right panel: Remote files ────────────────────────────
            FilePanel(
                state = uiState.rightPanel,
                title = if (uiState.remoteState.isConnected) "Remote" else "Not connected",
                onEntryClick = { file ->
                    if (file.isDirectory) viewModel.navigateRemote(file.path)
                },
                onNavigateUp = if (uiState.rightPanel.currentPath != "/" && uiState.rightPanel.currentPath.isNotBlank()) ({
                    viewModel.navigateRemoteUp()
                }) else null,
                onDragStart = { /* handled */ },
                modifier = Modifier.weight(1f),
                panelColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        }

        // Transfer buttons floating between panels
        TransferActions(
            leftPanel = uiState.leftPanel,
            rightPanel = uiState.rightPanel,
            isConnected = uiState.remoteState.isConnected,
            onUpload = { selectedFiles ->
                viewModel.uploadToRemote(selectedFiles)
            },
            onDownload = { selectedFiles ->
                viewModel.downloadToLocal(selectedFiles)
            }
        )
    }

    // Profile picker dialog
    if (uiState.showProfilePicker) {
        ProfilePickerDialog(
            viewModel = viewModel
        )
    }
}

@Composable
private fun FilePanel(
    state: PanelState,
    title: String,
    onEntryClick: (PanelFile) -> Unit,
    onNavigateUp: (() -> Unit)?,
    onDragStart: (PanelFile) -> Unit,
    modifier: Modifier = Modifier,
    panelColor: Color = MaterialTheme.colorScheme.surface
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(panelColor)
    ) {
        // Panel header with breadcrumbs
        PanelHeader(
            title = title,
            breadcrumbs = state.breadcrumbs,
            onNavigateUp = onNavigateUp
        )

        // File list
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.entries.isEmpty() -> Text(
                    if (state.isLocal) "Empty folder" else "No files",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.outline
                )
                else -> PanelFileList(
                    entries = state.entries,
                    onEntryClick = onEntryClick,
                    onNavigateUp = onNavigateUp,
                    onDragStart = onDragStart
                )
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    breadcrumbs: List<String>,
    onNavigateUp: (() -> Unit)?
) {
    Surface(tonalElevation = 2.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onNavigateUp != null) {
                    IconButton(onClick = onNavigateUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", modifier = Modifier.size(16.dp))
                    }
                }
                Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(breadcrumbs) { crumb ->
                    Text(
                        text = crumb,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    if (crumb != breadcrumbs.last()) {
                        Text("/", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelFileList(
    entries: List<PanelFile>,
    onEntryClick: (PanelFile) -> Unit,
    onNavigateUp: (() -> Unit)?,
    onDragStart: (PanelFile) -> Unit
) {
    // Multi-select state
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    val inSelectionMode = selectedPaths.isNotEmpty()

    LazyColumn {
        if (onNavigateUp != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = {
                            if (inSelectionMode) selectedPaths = emptySet() else onNavigateUp()
                        })
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("..", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider()
            }
        }
        items(entries, key = { it.path }) { file ->
            PanelFileRow(
                file = file,
                isSelected = file.path in selectedPaths,
                onClick = {
                    if (inSelectionMode) {
                        selectedPaths = if (file.path in selectedPaths) selectedPaths - file.path else selectedPaths + file.path
                    } else {
                        onEntryClick(file)
                    }
                },
                onLongPress = {
                    selectedPaths = if (file.path in selectedPaths) selectedPaths - file.path else selectedPaths + file.path
                },
                onDragStart = { onDragStart(file) }
            )
            HorizontalDivider()
        }

        // Selection action bar at bottom of list
        if (inSelectionMode) {
            item {
                SelectionActionBar(
                    selectedCount = selectedPaths.size,
                    onClear = { selectedPaths = emptySet() },
                    // Transfer actions are wired externally
                    isRemote = entries.firstOrNull()?.isLocal == false
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PanelFileRow(
    file: PanelFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(Icons.Default.CheckBox, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        } else {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (file.isDirectory) "Folder" else formatSize(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    isRemote: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun TransferActions(
    leftPanel: PanelState,
    rightPanel: PanelState,
    isConnected: Boolean,
    onUpload: (List<PanelFile>) -> Unit,
    onDownload: (List<PanelFile>) -> Unit
) {
    // These would typically be triggered from a floating action button or toolbar
    // For the dual panel, we'll show transfer buttons in the top bar area
    // The actual transfer is triggered by long-press selection on file rows
}

@Composable
private fun ProfilePickerDialog(viewModel: DualPanelViewModel) {
    // Inline profile list dialog
    AlertDialog(
        onDismissRequest = { viewModel.hideProfilePicker() },
        title = { Text("Connect to Remote") },
        text = {
            Text("Go to Profiles to select a connection, or use the standalone browser.")
        },
        confirmButton = {
            TextButton(onClick = { viewModel.hideProfilePicker() }) { Text("OK") }
        }
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
