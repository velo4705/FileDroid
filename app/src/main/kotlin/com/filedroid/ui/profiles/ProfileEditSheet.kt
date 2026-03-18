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
    onSave: (label: String, protocol: Protocol, host: String, port: Int,
             username: String, password: String, initialPath: String, anonymous: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(profile?.label ?: "") }
    var protocol by remember { mutableStateOf(profile?.protocol ?: Protocol.SFTP) }
    var host by remember { mutableStateOf(profile?.host ?: "") }
    var port by remember { mutableStateOf(profile?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(profile?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var initialPath by remember { mutableStateOf(profile?.initialRemotePath ?: "/") }
    var anonymous by remember { mutableStateOf(profile?.anonymous ?: false) }

    val portError = port.toIntOrNull()?.let { if (it in 1..65535) null else "Invalid port" } ?: "Invalid port"
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
                            port = when (p) { Protocol.SFTP -> "22"; Protocol.FTP -> "21"; Protocol.FTPS -> "990" }
                        },
                        label = { Text(p.name) }
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
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            OutlinedTextField(value = initialPath, onValueChange = { initialPath = it },
                label = { Text("Initial remote path") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Button(
                onClick = {
                    onSave(label, protocol, host, port.toInt(), username, password, initialPath, anonymous)
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}
