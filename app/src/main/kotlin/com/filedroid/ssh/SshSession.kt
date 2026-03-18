package com.filedroid.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.InputStream
import java.io.OutputStream

/** Represents a single interactive SSH shell session. */
class SshSession(
    val id: String,
    val label: String
) {
    private var ssh: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null

    private val _output = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 512)
    val output: SharedFlow<String> = _output.asSharedFlow()

    var isConnected: Boolean = false
        private set

    suspend fun connect(host: String, port: Int, username: String, password: String): Result<Unit> =
        runCatching {
            val client = SSHClient()
            client.addHostKeyVerifier(PromiscuousVerifier())
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

            // Start reading output in background
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
            // session closed
        } finally {
            isConnected = false
            _output.emit("\r\n[Session closed]\r\n")
        }
    }

    fun send(text: String) {
        runCatching {
            outputStream?.write(text.toByteArray(Charsets.UTF_8))
            outputStream?.flush()
        }
    }

    fun disconnect() {
        isConnected = false
        readJob?.cancel()
        runCatching { shell?.close() }
        runCatching { session?.close() }
        runCatching { ssh?.disconnect() }
    }
}
