package com.filedroid.server

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpServerManager @Inject constructor() {

    private var server: SshServer? = null
    private var running = false

    fun start(config: ServerConfig, hostKeyFile: File): Result<Unit> = runCatching {
        // Stop any existing instance first
        runCatching { server?.stop(true) }
        server = null
        running = false

        val sshd = SshServer.setUpDefaultServer()
        sshd.port = config.sftpPort

        // Explicitly bind to all interfaces or the selected one
        sshd.host = if (config.bindAddress.isNotBlank()) config.bindAddress else "0.0.0.0"

        val keyProvider = SimpleGeneratorHostKeyProvider(hostKeyFile.toPath()).apply {
            algorithm = KeyPairProvider.SSH_RSA
            keySize = 2048
        }
        sshd.keyPairProvider = keyProvider

        sshd.passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            username == config.username && password == config.password
        }

        sshd.subsystemFactories = listOf(SftpSubsystemFactory())
        sshd.fileSystemFactory = VirtualFileSystemFactory(Paths.get(config.rootPath))

        sshd.start()

        // sshd.start() is non-blocking — poll until the port is actually bound (up to 10s)
        val bindHost = if (config.bindAddress.isNotBlank()) config.bindAddress else "0.0.0.0"
        val deadline = System.currentTimeMillis() + 10_000
        var bound = false
        while (System.currentTimeMillis() < deadline) {
            if (isPortBound(bindHost, config.sftpPort)) { bound = true; break }
            Thread.sleep(200)
        }
        if (!bound) throw IllegalStateException("SFTP server did not bind to port ${config.sftpPort} within 10s")

        server = sshd
        running = true
    }

    fun stop() {
        running = false
        runCatching { server?.stop(true) }
        server = null
    }

    fun isRunning(): Boolean = running && server != null && !server!!.isClosed

    private fun isPortBound(host: String, port: Int): Boolean = try {
        ServerSocket().use { s ->
            s.reuseAddress = true
            s.bind(InetSocketAddress(host, port))
            false // bound = port was free = server not listening yet
        }
    } catch (_: Exception) {
        true // failed to bind = port in use = server is listening
    }
}
