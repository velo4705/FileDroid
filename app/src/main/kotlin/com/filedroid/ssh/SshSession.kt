package com.filedroid.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.InputStream
import java.io.OutputStream

private fun safeConfig() = DefaultConfig().apply {
    keyExchangeFactories = keyExchangeFactories.filter { factory ->
        val name = factory.name
        !name.contains("x25519", ignoreCase = true) &&
        !name.contains("x448", ignoreCase = true)
    }
}

/** Represents a single interactive SSH shell session with auto-reconnect (R6.4). */
class SshSession(
    val id: String,
    val label: String
) {
    private var ssh: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null

    // Stored for reconnect
    private var lastHost: String = ""
    private var lastPort: Int = 22
    private var lastUsername: String = ""
    private var lastPassword: String = ""

    private val _output = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 512)
    val output: SharedFlow<String> = _output.asSharedFlow()

    var isConnected: Boolean = false
        private set

    suspend fun connect(host: String, port: Int, username: String, password: String): Result<Unit> =
        runCatching {
            lastHost = host; lastPort = port; lastUsername = username; lastPassword = password
            doConnect(host, port, username, password)
        }

    private fun doConnect(host: String, port: Int, username: String, password: String) {
        val client = SSHClient(safeConfig())
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connectTimeout = 10_000
        client.connect(host, port)
        client.authPassword(username, password)

        val sess = client.startSession()
        sess.allocateDefaultPTY()
        val sh = sess.startShell()

        ssh = client
        session = sess
        shell = sh
        outputStream = sh.outputStream
        isConnected = true

        readJob = CoroutineScope(Dispatchers.IO).launch {
            readOutput(sh.inputStream)
        }
    }

    private suspend fun readOutput(input: InputStream) {
        val buf = ByteArray(4096)
        try {
            while (isConnected) {
                val n = input.read(buf)
                if (n == -1) break
                _output.emit(String(buf, 0, n, Charsets.UTF_8))
            }
        } catch (_: Exception) {
            // dropped
        } finally {
            isConnected = false
            _output.emit("\r\n[Session closed — attempting reconnect…]\r\n")
            tryReconnect()
        }
    }

    /** Auto-reconnect up to 3 times with 3s back-off (R6.4). */
    private suspend fun tryReconnect() {
        if (lastHost.isBlank()) return
        repeat(3) { attempt ->
            delay(3_000L * (attempt + 1))
            runCatching { doConnect(lastHost, lastPort, lastUsername, lastPassword) }
                .onSuccess {
                    _output.emit("\r\n[Reconnected]\r\n")
                    return
                }
                .onFailure {
                    _output.emit("\r\n[Reconnect attempt ${attempt + 1}/3 failed]\r\n")
                }
        }
        _output.emit("\r\n[Could not reconnect after 3 attempts]\r\n")
    }

    fun send(text: String) {
        runCatching {
            outputStream?.write(text.toByteArray(Charsets.UTF_8))
            outputStream?.flush()
        }
    }

    fun disconnect() {
        lastHost = "" // prevent auto-reconnect on intentional disconnect
        isConnected = false
        readJob?.cancel()
        runCatching { shell?.close() }
        runCatching { session?.close() }
        runCatching { ssh?.disconnect() }
    }
}
