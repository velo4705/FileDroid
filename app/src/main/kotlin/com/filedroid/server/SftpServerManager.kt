package com.filedroid.server

import org.apache.sshd.common.file.FileSystemFactory
import org.apache.sshd.common.file.root.RootedFileSystemProvider
import org.apache.sshd.common.keyprovider.MappedKeyPairProvider
import org.apache.sshd.common.session.SessionContext
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Paths
import java.security.KeyPairGenerator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpServerManager @Inject constructor() {

    private var server: SshServer? = null
    private var running = false

    fun start(config: ServerConfig): Result<Unit> = runCatching {
        // Stop any existing instance first
        runCatching { server?.stop(true) }
        server = null
        running = false

        val rootPath = config.rootPath.ifBlank {
            android.os.Environment.getExternalStorageDirectory().absolutePath
        }.let { java.io.File(it).canonicalPath }

        // MINA reads user.home as a fallback in multiple places — set it before server init
        System.setProperty("user.home", rootPath)

        // Android's ServiceLoader needs the app classloader — MINA uses it to find its factories
        val prevCl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = SshServer::class.java.classLoader
        val sshd = try {
            SshServer.setUpDefaultServer()
        } finally {
            Thread.currentThread().contextClassLoader = prevCl
        }
        sshd.port = config.sftpPort
        sshd.host = if (config.bindAddress.isNotBlank()) config.bindAddress else "0.0.0.0"

        // Generate RSA host key in-memory — SimpleGeneratorHostKeyProvider uses Java
        // serialization which is unreliable on Android and causes "no resolved signatures"
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        sshd.keyPairProvider = MappedKeyPairProvider(keyPair)

        sshd.passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            username == config.username && password == config.password
        }

        val rootNio = Paths.get(rootPath)
        // RootedFileSystem jails the SFTP client inside rootPath — they see / but it maps to rootPath
        val rootedFs = RootedFileSystemProvider().newFileSystem(rootNio, emptyMap<String, Any>())
        sshd.fileSystemFactory = object : FileSystemFactory {
            override fun getUserHomeDir(session: SessionContext) = rootedFs.getPath("/")
            override fun createFileSystem(session: SessionContext) = rootedFs
        }
        sshd.subsystemFactories = listOf(SftpSubsystemFactory())

        sshd.start()

        // Poll until port is bound on loopback (up to 10s)
        val deadline = System.currentTimeMillis() + 10_000
        var bound = false
        while (System.currentTimeMillis() < deadline) {
            if (isPortBound("127.0.0.1", config.sftpPort)) { bound = true; break }
            Thread.sleep(200)
        }
        if (!bound) throw IllegalStateException("SFTP server did not bind to port ${config.sftpPort} within 10s")

        // Also verify it's reachable on the WiFi interface
        val wifiIp = getWifiIp()
        if (wifiIp != null && !isPortBound(wifiIp, config.sftpPort)) {
            android.util.Log.w("SftpServerManager", "Port ${config.sftpPort} not reachable on $wifiIp — may be blocked by Android firewall")
        }

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
            false // bound = port was free = server not listening
        }
    } catch (_: Exception) {
        true // failed to bind = port in use = server is listening
    }

    private fun getWifiIp(): String? = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { it is java.net.Inet4Address }
            ?.hostAddress
    }.getOrNull()
}
