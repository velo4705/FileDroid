package com.filedroid.server

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import java.io.File
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpServerManager @Inject constructor() {

    private var server: SshServer? = null
    private var running = false

    fun start(config: ServerConfig, hostKeyFile: File): Result<Unit> = runCatching {
        val sshd = SshServer.setUpDefaultServer()
        sshd.port = config.sftpPort

        if (config.bindAddress.isNotBlank()) {
            sshd.host = config.bindAddress
        }

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
        server = sshd
        running = true
    }

    fun stop() {
        running = false
        server?.stop(true)
        server = null
    }

    fun isRunning(): Boolean = running && server != null && !server!!.isClosed
}
