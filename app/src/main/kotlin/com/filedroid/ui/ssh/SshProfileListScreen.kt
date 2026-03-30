package com.filedroid.ui.ssh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.data.SshProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshProfileListScreen(
    onNavigateBack: () -> Unit,
    onConnect: (SshProfile, String) -> Unit,
    viewModel: SshProfileViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    var editTarget by remember { mutableStateOf<SshProfile?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH Profiles") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editTarget = null; showSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add SSH profile")
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No SSH profiles. Tap + to add one.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(profiles, key = { it.id }) { profile ->
                    SshProfileRow(
                        profile = profile,
                        onClick = { onConnect(profile, viewModel.getPassword(profile)) },
                        onEdit = { editTarget = profile; showSheet = true },
                        onDelete = { viewModel.delete(profile) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showSheet) {
        SshProfileEditSheet(
            profile = editTarget,
            onSave = { label, host, port, user, pass, useKey, key, phrase ->
                viewModel.save(editTarget, label, host, port, user, pass, useKey, key, phrase)
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

@Composable
private fun SshProfileRow(
    profile: SshProfile,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.label, style = MaterialTheme.typography.bodyLarge)
            Text("${profile.username}@${profile.host}:${profile.port}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Options")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() },
                    leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SshProfileEditSheet(
    profile: SshProfile?,
    onSave: (label: String, host: String, port: Int, username: String,
             password: String, usePrivateKey: Boolean, privateKey: String, passphrase: String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(profile?.label ?: "") }
    var host by remember { mutableStateOf(profile?.host ?: "") }
    var port by remember { mutableStateOf(profile?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(profile?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var usePrivateKey by remember { mutableStateOf(profile?.usePrivateKey ?: false) }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }

    val portError: String? = when (val n = port.toIntOrNull()) {
        null -> "Invalid port"
        in 1..65535 -> null
        else -> "Invalid port"
    }
    val canSave = label.isNotBlank() && host.isNotBlank() && portError == null && username.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (profile == null) "New SSH Profile" else "Edit SSH Profile",
                style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = label, onValueChange = { label = it },
                label = { Text("Label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = host, onValueChange = { host = it },
                label = { Text("Host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = port, onValueChange = { port = it },
                label = { Text("Port") }, isError = portError != null,
                supportingText = portError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = username, onValueChange = { username = it },
                label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            // Private key toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = usePrivateKey, onCheckedChange = { usePrivateKey = it })
                Text("Use private key authentication")
            }

            if (usePrivateKey) {
                OutlinedTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    label = { Text("Private key (PEM / OpenSSH)") },
                    placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    maxLines = 8
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Key passphrase (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text(if (profile == null) "Password" else "Password (leave blank to keep)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            Button(
                onClick = { onSave(label, host, port.toInt(), username, password, usePrivateKey, privateKey, passphrase) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}
