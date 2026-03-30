package com.filedroid.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.OutputStream

class SshSession(val id: String, val label: String) {

    // Session-scoped coroutine scope — cancelled on disconnect
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var ssh: SSHClient? = null
    private var shellSession: Session? = null
    private var shell: Session.Shell? = null
    private var stdin: OutputStream? = null
    private var readJob: Job? = null

    private var lastHost = ""; private var lastPort = 22
    private var lastUser = ""; private var lastPass = ""
    private var lastPrivateKey = ""; private var lastPassphrase: String? = null
    private var lastUseKey = false

    private val _output = MutableSharedFlow<String>(replay = 512, extraBufferCapacity = 1024)
    val output: SharedFlow<String> = _output.asSharedFlow()

    /** Accumulated buffer for echo filtering across chunks. */
    private val echoBuffer = StringBuilder()

    // Last command sent (without trailing newline) — used to strip echo from output
    @Volatile private var lastSent: String = ""

    var isConnected = false
        private set

    /** Blocking connect with password — call from Dispatchers.IO. */
    fun connectBlocking(host: String, port: Int, username: String, password: String) {
        lastHost = host; lastPort = port; lastUser = username; lastPass = password
        lastUseKey = false
        doConnect(host, port, username, password = password, privateKey = null, passphrase = null)
    }

    /** Blocking connect with private key — call from Dispatchers.IO. */
    fun connectBlockingWithKey(host: String, port: Int, username: String, privateKey: String, passphrase: String?) {
        lastHost = host; lastPort = port; lastUser = username
        lastPrivateKey = privateKey; lastPassphrase = passphrase; lastUseKey = true
        doConnect(host, port, username, password = null, privateKey = privateKey, passphrase = passphrase)
    }

    private fun doConnect(
        host: String, port: Int, username: String,
        password: String? = null, privateKey: String? = null, passphrase: String? = null
    ) {
        val client = SSHClient(DefaultConfig())
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connectTimeout = 10_000
        client.connect(host, port)

        when {
            privateKey != null -> {
                val keyFile = java.io.File.createTempFile("ssh_key_", null).apply {
                    writeText(privateKey)
                }
                try {
                    val keyProvider = if (passphrase.isNullOrEmpty()) {
                        client.loadKeys(keyFile.absolutePath)
                    } else {
                        client.loadKeys(keyFile.absolutePath, passphrase)
                    }
                    client.authPublickey(username, keyProvider)
                } finally {
                    keyFile.delete()
                }
            }
            password != null -> {
                client.authPassword(username, password)
            }
            else -> throw IllegalArgumentException("Either password or privateKey must be provided")
        }

        val sess = client.startSession()
        sess.allocatePTY("xterm", 220, 50, 0, 0, mutableMapOf())
        val sh = sess.startShell()

        ssh = client
        shellSession = sess
        shell = sh
        stdin = sh.outputStream
        isConnected = true
        echoBuffer.clear()

        // Read loop runs in the session scope — tied to this session's lifetime
        readJob = scope.launch {
            val buf = ByteArray(4096)
            try {
                while (isConnected) {
                    val n = withContext(Dispatchers.IO) { sh.inputStream.read(buf) }
                    if (n == -1) break
                    val chunk = String(buf, 0, n, Charsets.UTF_8)
                    _output.emit(filterEcho(chunk))
                }
            } catch (_: Exception) { }
            finally {
                isConnected = false
                _output.emit("\r\n[Session closed]\r\n")
                tryReconnect()
            }
        }
    }

    private suspend fun tryReconnect() {
        if (lastHost.isBlank()) return
        repeat(3) { attempt ->
            delay(3_000L * (attempt + 1))
            runCatching {
                withContext(Dispatchers.IO) {
                    if (lastUseKey) {
                        doConnect(lastHost, lastPort, lastUser, privateKey = lastPrivateKey, passphrase = lastPassphrase)
                    } else {
                        doConnect(lastHost, lastPort, lastUser, password = lastPass)
                    }
                }
            }.onSuccess {
                _output.emit("\r\n[Reconnected]\r\n")
                return
            }.onFailure {
                _output.emit("\r\n[Reconnect ${attempt + 1}/3 failed: ${it.message}]\r\n")
            }
        }
        _output.emit("\r\n[Could not reconnect]\r\n")
    }

    fun send(text: String) {
        lastSent = text.trimEnd('\n', '\r')
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    stdin?.write(text.toByteArray(Charsets.UTF_8))
                    stdin?.flush()
                }
            }
        }
    }

    /**
     * Removes the echoed command from [chunk]. Uses a persistent echoBuffer to handle
     * echo that arrives across multiple read chunks (the common case on Linux/macOS/Windows).
     */
    private fun filterEcho(chunk: String): String {
        val cmd = lastSent
        if (cmd.isEmpty()) return chunk

        // If the echo buffer has content, try to match against accumulated + new chunk
        val fullEcho = echoBuffer.toString() + chunk
        val stripped = stripAnsi(fullEcho)

        val cmdStart = stripped.indexOf(cmd)
        if (cmdStart == -1) {
            // No match yet — buffer this chunk and wait for more data
            echoBuffer.append(chunk)
            // Prevent unbounded growth — if buffer gets too large, flush it
            if (echoBuffer.length > 4096) {
                val flushed = echoBuffer.toString()
                echoBuffer.clear()
                return flushed
            }
            return ""
        }

        // Found the echo — clear state
        lastSent = ""
        echoBuffer.clear()

        val cmdEnd = cmdStart + cmd.length
        val ansiPattern = Regex("\u001B(?:\\[[?!]?[0-9;]*[A-Za-z]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[^\\[\\]])")
        val result = StringBuilder()
        var strippedIdx = 0
        var pos = 0

        while (pos < fullEcho.length) {
            val escMatch = ansiPattern.find(fullEcho, pos)
            if (escMatch != null && escMatch.range.first == pos) {
                val nextVisibleInCmd = strippedIdx in cmdStart until cmdEnd
                if (!nextVisibleInCmd) result.append(escMatch.value)
                pos = escMatch.range.last + 1
            } else {
                if (strippedIdx !in cmdStart until cmdEnd) result.append(fullEcho[pos])
                strippedIdx++
                pos++
            }
        }
        return result.toString()
    }

    private fun stripAnsi(s: String): String =
        s.replace(Regex("\u001B(?:\\[[?!]?[0-9;]*[A-Za-z]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[^\\[\\]])"), "")

    fun disconnect() {
        lastHost = ""
        isConnected = false
        readJob?.cancel()
        runCatching { shell?.close() }
        runCatching { shellSession?.close() }
        runCatching { ssh?.disconnect() }
        scope.cancel()
    }
}
