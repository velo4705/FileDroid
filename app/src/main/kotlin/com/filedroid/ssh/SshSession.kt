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

    private val _output = MutableSharedFlow<String>(replay = 512, extraBufferCapacity = 1024)
    val output: SharedFlow<String> = _output.asSharedFlow()

    // Last command sent (without trailing newline) — used to strip echo from output
    @Volatile private var lastSent: String = ""

    var isConnected = false
        private set

    /** Blocking connect — call from Dispatchers.IO. */
    fun connectBlocking(host: String, port: Int, username: String, password: String) {
        lastHost = host; lastPort = port; lastUser = username; lastPass = password
        doConnect(host, port, username, password)
    }

    private fun doConnect(host: String, port: Int, username: String, password: String) {
        val client = SSHClient(DefaultConfig())
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connectTimeout = 10_000
        client.connect(host, port)
        client.authPassword(username, password)

        val sess = client.startSession()
        sess.allocatePTY("xterm", 220, 50, 0, 0, mutableMapOf())
        val sh = sess.startShell()

        ssh = client
        shellSession = sess
        shell = sh
        stdin = sh.outputStream
        isConnected = true

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
                withContext(Dispatchers.IO) { doConnect(lastHost, lastPort, lastUser, lastPass) }
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
     * Removes the echoed command from [chunk]. The shell sends back exactly what we typed
     * (possibly with ANSI codes interspersed) somewhere in the chunk — strip it out by
     * doing a substring match on the ANSI-stripped version, then remove the corresponding
     * span from the raw chunk.
     */
    private fun filterEcho(chunk: String): String {
        val cmd = lastSent
        if (cmd.isEmpty()) return chunk

        val stripped = stripAnsi(chunk)

        // Find where the command text appears in the stripped version
        val idx = stripped.indexOf(cmd)
        if (idx == -1) return chunk

        // Map the index back to the raw chunk by walking both strings in parallel
        var rawIdx = 0
        var strippedPos = 0
        val ansiPattern = Regex("\u001B(?:\\[[?!]?[0-9;]*[A-Za-z]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[^\\[\\]])")

        // Build a list of (rawStart, rawEnd) for each visible character in chunk
        val rawPositions = mutableListOf<Int>()
        var pos = 0
        while (pos < chunk.length) {
            val escMatch = ansiPattern.find(chunk, pos)
            if (escMatch != null && escMatch.range.first == pos) {
                // Skip escape sequence — no visible chars
                pos = escMatch.range.last + 1
            } else {
                rawPositions.add(pos)
                pos++
            }
        }

        if (idx >= rawPositions.size || idx + cmd.length > rawPositions.size) return chunk

        val rawStart = rawPositions[idx]
        // rawEnd: position after the last char of cmd in raw string
        val rawEnd = if (idx + cmd.length < rawPositions.size) rawPositions[idx + cmd.length]
                     else chunk.length

        return chunk.removeRange(rawStart, rawEnd)
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
