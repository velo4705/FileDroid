package com.filedroid.ui.ssh

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollState as rememberVScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshTerminalScreen(
    onNavigateBack: () -> Unit,
    initialHost: String = "",
    initialPort: Int = 22,
    initialUsername: String = "",
    initialPassword: String = "",
    viewModel: SshTerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConnectDialog by remember { mutableStateOf(false) }

    // Auto-connect if launched from a profile
    LaunchedEffect(initialHost) {
        if (initialHost.isNotBlank() && uiState.tabs.isEmpty()) {
            viewModel.openSession(initialHost, initialPort, initialUsername, initialPassword)
        } else if (uiState.tabs.isEmpty()) {
            showConnectDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH Manager") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showConnectDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New session")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize
().padding(padding)) {
            if (uiState.tabs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active sessions. Tap + to connect.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                // Tab row
                ScrollableTabRow(
                    selectedTabIndex = uiState.activeTabIndex.coerceAtMost(uiState.tabs.lastIndex),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == uiState.activeTabIndex,
                            onClick = { viewModel.selectTab(index) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tab.session.label, maxLines = 1)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.closeTab(tab.session.id) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close tab", modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        )
                    }
                }

                val activeTab = uiState.tabs.getOrNull(uiState.activeTabIndex)
                if (activeTab != null) {
                    TerminalPane(
                        tab = activeTab,
                        onSend = { viewModel.sendInput(activeTab.session.id, it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showConnectDialog) {
        ConnectDialog(
            onDismiss = { showConnectDialog = false },
            onConnect = { host, port, user, pass ->
                showConnectDialog = false
                viewModel.openSession(host, port, user, pass)
            }
        )
    }
}

@Composable
private fun TerminalPane(
    tab: TabState,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    val vScroll = rememberVScrollState()
    val hScroll = rememberScrollState()

    LaunchedEffect(tab.buffer) { vScroll.animateScrollTo(vScroll.maxValue) }
    LaunchedEffect(input) { vScroll.animateScrollTo(vScroll.maxValue) }

    Column(modifier = modifier.background(Color.Black)) {
        when {
            tab.connecting -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.Green)
            }
            tab.error != null -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Connection failed:\n${tab.error}", color = Color.Red, fontFamily = FontFamily.Monospace)
            }
            else -> {
                // Blinking cursor
                var cursorVisible by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(500)
                        cursorVisible = !cursorVisible
                    }
                }
                val displayText = remember(tab.buffer, input, cursorVisible) {
                    // Show buffer as-is, then the current input on the same line as the prompt
                    // The buffer already ends with the prompt, so just append input + cursor
                    parseAnsi(tab.buffer + input + if (cursorVisible) "█" else " ")
                }
                Text(
                    text = displayText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                        .padding(8.dp)
                )
            }
        }

        // Input row — send full line on Enter/Send button only
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onSend(input + "\n")
                    input = ""
                }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (input.isEmpty()) Text("Type command…", color = Color.Gray, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    inner()
                }
            )
            IconButton(onClick = { onSend(input + "\n"); input = "" }) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Green)
            }
        }
    }
}

@Composable
private fun ConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (host: String, port: Int, user: String, pass: String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New SSH Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, singleLine = true)
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(host, port.toIntOrNull() ?: 22, user, pass) },
                enabled = host.isNotBlank() && user.isNotBlank()
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Strip all ANSI/VT escape sequences and parse SGR color codes → AnnotatedString. */
private fun parseAnsi(raw: String): AnnotatedString {
    val text = raw.replace("\r\n", "\n").replace("\r", "\n")
    return buildAnnotatedString {
        // Matches all escape sequences:
        // CSI: ESC [ <params> <final>  (covers [?2004h, [K, [m, [1;32m, etc.)
        // OSC: ESC ] ... ST/BEL
        // Two-char: ESC <any single char>
        val escRegex = Regex("\u001B(?:\\[[?!]?[0-9;]*[A-Za-z]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[^\\[\\]])")
        val sgrRegex = Regex("\u001B\\[([0-9;]*)m")
        var currentColor: Color? = null
        var cursor = 0

        for (match in escRegex.findAll(text)) {
            val before = text.substring(cursor, match.range.first)
            if (before.isNotEmpty()) {
                if (currentColor != null) withStyle(SpanStyle(color = currentColor)) { append(before) }
                else append(before)
            }
            cursor = match.range.last + 1
            val sgrMatch = sgrRegex.matchEntire(match.value)
            if (sgrMatch != null) currentColor = ansiCodeToColor(sgrMatch.groupValues[1])
        }
        val remaining = text.substring(cursor)
        if (remaining.isNotEmpty()) {
            if (currentColor != null) withStyle(SpanStyle(color = currentColor)) { append(remaining) }
            else append(remaining)
        }
    }
}

private fun ansiCodeToColor(codes: String): Color? {
    val parts = codes.split(";").mapNotNull { it.toIntOrNull() }
    return when {
        parts.isEmpty() || parts.contains(0) -> Color.White
        parts.contains(31) -> Color.Red
        parts.contains(32) -> Color(0xFF00FF00)
        parts.contains(33) -> Color.Yellow
        parts.contains(34) -> Color(0xFF6699FF)
        parts.contains(35) -> Color(0xFFFF66FF)
        parts.contains(36) -> Color.Cyan
        parts.contains(37) -> Color.White
        parts.contains(90) -> Color.Gray
        parts.contains(91) -> Color(0xFFFF6666)
        parts.contains(92) -> Color(0xFF66FF66)
        parts.contains(93) -> Color(0xFFFFFF66)
        else -> null
    }
}
