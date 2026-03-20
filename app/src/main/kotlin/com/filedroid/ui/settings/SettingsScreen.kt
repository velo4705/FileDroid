package com.filedroid.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.ui.theme.AccentColor
import com.filedroid.ui.theme.FontSize
import com.filedroid.ui.theme.ThemeMode
import com.filedroid.ui.theme.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToFtp: () -> Unit,
    onNavigateToSftp: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    themeVm: ThemeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val prefs by themeVm.prefs.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Appearance ──────────────────────────────────────────────
            SectionLabel("Appearance")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsDropdown(
                        label = "Theme",
                        options = ThemeMode.entries.map { it.label() },
                        selected = prefs.mode.label(),
                        onSelect = { themeVm.setMode(ThemeMode.entries[it]) }
                    )
                    if (prefs.mode != ThemeMode.MATERIAL_YOU) {
                        HorizontalDivider()
                        SettingsDropdown(
                            label = "Accent Color",
                            options = AccentColor.entries.map { it.label },
                            selected = prefs.accent.label,
                            onSelect = { themeVm.setAccent(AccentColor.entries[it]) }
                        )
                    }
                    HorizontalDivider()
                    SettingsDropdown(
                        label = "Font Size",
                        options = FontSize.entries.map { it.label },
                        selected = prefs.fontSize.label,
                        onSelect = { themeVm.setFontSize(FontSize.entries[it]) }
                    )
                }
            }

            // ── Server Credentials ───────────────────────────────────────
            SectionLabel("Server Credentials")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Used when clients connect to FileDroid's FTP/SFTP server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)

                    OutlinedTextField(
                        value = uiState.serverUsername,
                        onValueChange = { viewModel.updateServerUsername(it) },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.serverPassword,
                        onValueChange = { viewModel.updateServerPassword(it) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "Hide" else "Show",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.serverRootPath,
                        onValueChange = { viewModel.updateServerRootPath(it) },
                        label = { Text("Root path") },
                        placeholder = { Text("/storage/emulated/0") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.saveServerCredentials() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save") }

                    if (uiState.passwordSaved) {
                        Text("Saved.", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── Server Ports ─────────────────────────────────────────────
            SectionLabel("Server Ports")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.ftpPort,
                        onValueChange = { viewModel.updateFtpPort(it) },
                        label = { Text("FTP Port") },
                        isError = uiState.ftpPortError != null,
                        supportingText = uiState.ftpPortError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.sftpPort,
                        onValueChange = { viewModel.updateSftpPort(it) },
                        label = { Text("SFTP Port") },
                        isError = uiState.sftpPortError != null,
                        supportingText = uiState.sftpPortError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.savePorts() },
                        enabled = uiState.ftpPortError == null && uiState.sftpPortError == null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Ports") }
                }
            }

            // ── Credits ──────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "FileDroid — Free & Open Source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    "GPL-3.0 · github.com/imloafy/FileDroid",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { idx, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onSelect(idx); expanded = false }
                    )
                }
            }
        }
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.LIGHT        -> "Light"
    ThemeMode.DARK         -> "Dark"
    ThemeMode.AMOLED       -> "AMOLED Black"
    ThemeMode.SYSTEM       -> "System"
    ThemeMode.MATERIAL_YOU -> "Material You"
}
