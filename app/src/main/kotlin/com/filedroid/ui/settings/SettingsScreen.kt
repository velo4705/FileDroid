package com.filedroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    themeVm: ThemeViewModel = hiltViewModel()
) {
    val prefs by themeVm.prefs.collectAsState()

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
        ) {
            // Appearance section
            Text(
                "Appearance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 1. Theme mode
            SettingsDropdown(
                label = "Theme",
                options = ThemeMode.entries.map { it.label() },
                selected = prefs.mode.label(),
                onSelect = { idx -> themeVm.setMode(ThemeMode.entries[idx]) }
            )
            HorizontalDivider()

            // 2. Accent color (hidden for Material You)
            if (prefs.mode != ThemeMode.MATERIAL_YOU) {
                SettingsDropdown(
                    label = "Accent Color",
                    options = AccentColor.entries.map { it.label },
                    selected = prefs.accent.label,
                    onSelect = { idx -> themeVm.setAccent(AccentColor.entries[idx]) }
                )
                HorizontalDivider()
            }

            // 3. Font size
            SettingsDropdown(
                label = "Font Size",
                options = FontSize.entries.map { it.label },
                selected = prefs.fontSize.label,
                onSelect = { idx -> themeVm.setFontSize(FontSize.entries[idx]) }
            )
            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Server",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SettingsItem(label = "FTP Settings", onClick = onNavigateToFtp)
            HorizontalDivider()
            SettingsItem(label = "SFTP Settings", onClick = onNavigateToSftp)
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box {
            Text(selected, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun SettingsItem(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.LIGHT       -> "Light"
    ThemeMode.DARK        -> "Dark"
    ThemeMode.MATERIAL_YOU -> "Material You"
}
