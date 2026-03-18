package com.filedroid.server

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
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

    fun start(config: ServerConfig, hostKeyFile: File): Result<Unit> = runCatching {
        val sshd = SshServer.setUpDefaultServer()
        sshd.port = config.sftpPort

        // Host key
        sshd.keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile.toPath())

        // Password auth
        sshd.passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            username == config.username && password == config.password
        }

        // SFTP subsystem
        sshd.subsystemFactories = listOf(SftpSubsystemFactory())

        // Virtual filesystem rooted at config.rootPath
        sshd.fileSystemFactory = VirtualFileSystemFactory(Paths.get(config.rootPath))

        sshd.start()
        server = sshd
    }

    fun stop() {
        server?.stop(true)
        server = null
    }

    fun isRunning(): Boolean = server?.isStarted == true
}
