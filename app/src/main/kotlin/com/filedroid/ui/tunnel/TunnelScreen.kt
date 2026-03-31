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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.tunnel.ConnectionCode
import com.filedroid.tunnel.TunnelStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelScreen(
    onNavigateBack: () -> Unit,
    viewModel: TunnelViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var connectionCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Access") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connection Status", style = MaterialTheme.typography.titleSmall)
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
                                TunnelStatus.CONNECTED -> "Connected"
                                TunnelStatus.CONNECTING -> "Connecting..."
                                TunnelStatus.ERROR -> "Connection failed"
                                TunnelStatus.DISCONNECTED -> "Not connected"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (state.status == TunnelStatus.ERROR && state.error != null) {
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Connected — show info
            if (state.status == TunnelStatus.CONNECTED) {
                val peerName = state.peerDeviceName
                Text(
                    if (peerName.isNotBlank()) "Connected to $peerName"
                    else "Connected to remote device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Browse remote files using these connection details:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Host: 127.0.0.1",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("FTP port: 2121",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("SFTP port: 2222",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("Use your server username and password.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
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

            // Not connected — show code input
            if (state.status == TunnelStatus.DISCONNECTED || state.status == TunnelStatus.ERROR) {
                Text(
                    "Enter the 4-word code shown on the other device to connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = connectionCode,
                    onValueChange = { connectionCode = it.lowercase().replace(" ", "-") },
                    label = { Text("Connection code") },
                    placeholder = { Text("ocean-blue-river-sun") },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                val isValid = ConnectionCode.isValid(connectionCode)
                Button(
                    onClick = { viewModel.connectWithCode(connectionCode) },
                    enabled = isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }

                // Connecting indicator
                if (state.status == TunnelStatus.CONNECTING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
