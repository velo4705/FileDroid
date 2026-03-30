package com.filedroid.ui.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.filedroid.data.ConnectionProfile
import com.filedroid.data.Protocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    profile: ConnectionProfile?,
    existingPassword: String = "",   // pre-filled when editing
    existingPassphrase: String = "", // pre-filled when editing key-based profile
    onSave: (label: String, protocol: Protocol, host: String, port: Int,
             username: String, password: String, initialPath: String, anonymous: Boolean,
             usePrivateKey: Boolean, privateKey: String, passphrase: String,
             ftpsImplicit: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(profile?.label ?: "") }
    var protocol by remember { mutableStateOf(profile?.protocol ?: Protocol.SFTP) }
    var host by remember { mutableStateOf(profile?.host ?: "") }
    var port by remember { mutableStateOf(profile?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(profile?.username ?: "") }
    // Pre-fill password when editing so user doesn't have to re-enter it
    var password by remember { mutableStateOf(existingPassword) }
    var initialPath by remember { mutableStateOf(profile?.initialRemotePath ?: "~") }
    var anonymous by remember { mutableStateOf(profile?.anonymous ?: false) }
    var usePrivateKey by remember { mutableStateOf(profile?.usePrivateKey ?: false) }
    var privateKey by remember { mutableStateOf(if (profile?.usePrivateKey == true) existingPassword else "") }
    var passphrase by remember { mutableStateOf(if (profile?.usePrivateKey == true) existingPassphrase else "") }
    var ftpsImplicit by remember { mutableStateOf(profile?.ftpsImplicit ?: false) }

    val portError: String? = when (val n = port.toIntOrNull()) {
        null -> "Invalid port"
        in 1..65535 -> null
        else -> "Invalid port"
    }
    val canSave = label.isNotBlank() && host.isNotBlank() && portError == null &&
            (anonymous || username.isNotBlank())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (profile == null) "New Connection" else "Edit Connection",
                style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(value = label, onValueChange = { label = it },
                label = { Text("Label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            // Protocol selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Protocol.entries.forEach { p ->
                    FilterChip(
                        selected = protocol == p,
                        onClick = {
                            protocol = p
                            port = when (p) {
                                Protocol.SFTP -> "22"
                                Protocol.FTP -> "21"
                                Protocol.FTPS -> if (ftpsImplicit) "990" else "21"
                            }
                            if (p != Protocol.SFTP) usePrivateKey = false
                        },
                        label = { Text(p.name) }
                    )
                }
            }

            // FTPS explicit/implicit toggle
            if (protocol == Protocol.FTPS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !ftpsImplicit,
                        onClick = {
                            ftpsImplicit = false
                            port = "21"
                        },
                        label = { Text("Explicit (AUTH TLS)") }
                    )
                    FilterChip(
                        selected = ftpsImplicit,
                        onClick = {
                            ftpsImplicit = true
                            port = "990"
                        },
                        label = { Text("Implicit (TLS)") }
                    )
                }
            }

            OutlinedTextField(value = host, onValueChange = { host = it },
                label = { Text("Host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            OutlinedTextField(value = port, onValueChange = { port = it },
                label = { Text("Port") }, isError = portError != null,
                supportingText = portError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = anonymous, onCheckedChange = { anonymous = it })
                Text("Anonymous access")
            }

            if (!anonymous) {
                OutlinedTextField(value = username, onValueChange = { username = it },
                    label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                // R2.8 — private-key toggle (SFTP only)
                if (protocol == Protocol.SFTP) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = usePrivateKey, onCheckedChange = { usePrivateKey = it })
                        Text("Use private key authentication")
                    }
                }

                if (protocol == Protocol.SFTP && usePrivateKey) {
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
                } else if (!anonymous) {
                    OutlinedTextField(value = password, onValueChange = { password = it },
                        label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }

            OutlinedTextField(value = initialPath, onValueChange = { initialPath = it },
                label = { Text("Initial remote path") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Button(
                onClick = {
                    onSave(label, protocol, host, port.toInt(), username, password,
                        initialPath, anonymous, usePrivateKey, privateKey, passphrase,
                        ftpsImplicit)
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}
