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
import net.schmizz.sshj.connection.channel.direct.PTYMode
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
        // Disable remote echo — we send full lines, so the PTY echoing chars back causes duplicates
        val ptyModes = mutableMapOf<PTYMode, Int>(PTYMode.ECHO to 0)
        sess.allocatePTY("xterm", 220, 50, 0, 0, ptyModes)
        val sh = sess.startShell()

        ssh = client
        shellSession = sess
        shell = sh
        stdin = sh.outputStream
        isConnected = true

        // Send stty -echo to suppress shell-level line echo, then a sentinel so we know
        // when the shell has processed it and we can start showing output to the user.
        val sentinel = "__FILEDROID_READY__"
        sh.outputStream.write("stty -echo; echo $sentinel\n".toByteArray(Charsets.UTF_8))
        sh.outputStream.flush()

        // Read loop runs in the session scope — tied to this session's lifetime
        readJob = scope.launch {
            val buf = ByteArray(4096)
            val initBuf = StringBuilder()
            var ready = false
            try {
                while (isConnected) {
                    val n = withContext(Dispatchers.IO) { sh.inputStream.read(buf) }
                    if (n == -1) break
                    val chunk = String(buf, 0, n, Charsets.UTF_8)
                    if (!ready) {
                        // Swallow everything until we see the sentinel line
                        initBuf.append(chunk)
                        val combined = initBuf.toString()
                        val idx = combined.indexOf(sentinel)
                        if (idx != -1) {
                            ready = true
                            // Emit anything that came after the sentinel on the same read
                            val after = combined.substring(idx + sentinel.length)
                                .trimStart('\r', '\n')
                            if (after.isNotEmpty()) _output.emit(after)
                        }
                    } else {
                        // Strip any line that is just the echoed command we sent
                        val filtered = if (lastSent.isNotEmpty()) {
                            chunk.replace(lastSent + "\r\n", "")
                                 .replace(lastSent + "\n", "")
                        } else chunk
                        if (filtered.isNotEmpty()) _output.emit(filtered)
                    }
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
