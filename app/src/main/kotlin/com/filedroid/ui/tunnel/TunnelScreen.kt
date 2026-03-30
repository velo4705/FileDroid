package com.filedroid.ui.tunnel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.tunnel.TunnelStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelScreen(
    onNavigateBack: () -> Unit,
    viewModel: TunnelViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var relayUrl by remember { mutableStateOf(viewModel.getRelayUrl()) }
    var tunnelId by remember { mutableStateOf(viewModel.getTunnelId()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relay Tunnel") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Relay tunnel lets you access this device's FTP/SFTP servers over mobile data. " +
                        "Connect to a relay server, share the tunnel ID with the remote client.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tunnel Status", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge(
                            containerColor = when (state.status) {
                                TunnelStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                TunnelStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary
                                TunnelStatus.ERROR -> MaterialTheme.colorScheme.error
                                TunnelStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
                            }
                        ) {}
                        Text(
                            when (state.status) {
                                TunnelStatus.CONNECTED -> "Connected to relay"
                                TunnelStatus.CONNECTING -> "Connecting..."
                                TunnelStatus.ERROR -> "Error: ${state.error ?: "unknown"}"
                                TunnelStatus.DISCONNECTED -> "Disconnected"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (state.relayAddress.isNotEmpty()) {
                        Text(
                            "Relay address: ${state.relayAddress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Connection instructions when connected
            if (state.status == TunnelStatus.CONNECTED) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How to connect", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "From another FileDroid device, create a connection profile with:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("Host: relay server host from Relay address", style = MaterialTheme.typography.bodySmall)
                        Text("Port: from Relay address above", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Or open the Relay Tunnel screen on the client device and enter the same Relay URL and Tunnel ID.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            // Connection form (only when disconnected)
            if (state.status == TunnelStatus.DISCONNECTED || state.status == TunnelStatus.ERROR) {
                OutlinedTextField(
                    value = relayUrl,
                    onValueChange = { relayUrl = it },
                    label = { Text("Relay server URL") },
                    placeholder = { Text("wss://relay.example.com/ws") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = tunnelId,
                    onValueChange = { tunnelId = it },
                    label = { Text("Tunnel ID") },
                    placeholder = { Text("my-device-123") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = { viewModel.connectAsClient(relayUrl, tunnelId, username, password) },
                    enabled = relayUrl.isNotBlank() && tunnelId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect to Relay")
                }
            }

            // Disconnect button when connected/connecting
            if (state.status == TunnelStatus.CONNECTED || state.status == TunnelStatus.CONNECTING) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.disconnect() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect")
                }
            }
        }
    }
}
