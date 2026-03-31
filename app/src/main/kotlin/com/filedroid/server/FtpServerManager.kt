package com.filedroid.server

import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.DefaultFtpReply
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult
import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.TransferRatePermission
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory
import org.apache.ftpserver.FtpServer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Rejects any command whose argument contains path traversal sequences (R7.5). */
private class PathTraversalGuard : DefaultFtplet() {
    private fun isSafe(path: String?) = path == null || !path.contains("..")

    override fun beforeCommand(session: FtpSession, request: FtpRequest): FtpletResult {
        if (!isSafe(request.argument)) {
            session.write(DefaultFtpReply(550, "Permission denied: invalid path"))
            return FtpletResult.SKIP
        }
        return FtpletResult.DEFAULT
    }
}

@Singleton
class FtpServerManager @Inject constructor() {

    private var server: FtpServer? = null

    fun start(config: ServerConfig): Result<Unit> = runCatching {
        val factory = FtpServerFactory()

        // Listener
        val listenerFactory = ListenerFactory()
        listenerFactory.port = config.ftpPort
        // R7.4 — bind to selected network interface
        if (config.bindAddress.isNotBlank()) {
            listenerFactory.serverAddress = config.bindAddress
        }

        factory.addListener("default", listenerFactory.createListener())

        // User manager
        val userManager = factory.userManager
        val user = BaseUser().apply {
            name = config.username
            password = config.password
            homeDirectory = config.rootPath
            authorities = buildList<Authority> {
                add(WritePermission())
                add(ConcurrentLoginPermission(config.maxSessions, config.maxSessions))
                add(TransferRatePermission(0, 0))
            }
        }
        userManager.save(user)

        if (config.anonymousEnabled) {
            val anon = BaseUser().apply {
                name = "anonymous"
                password = ""
                homeDirectory = config.rootPath
                authorities = listOf(ConcurrentLoginPermission(config.maxSessions, config.maxSessions))
            }
            userManager.save(anon)
        }

        // Native filesystem rooted at config.rootPath
        factory.fileSystem = NativeFileSystemFactory()

        // Path traversal guard (R7.5)
        @Suppress("UNCHECKED_CAST")
        factory.ftplets = mutableMapOf<String, org.apache.ftpserver.ftplet.Ftplet>("guard" to PathTraversalGuard())

        server = factory.createServer()
        server!!.start()
    }

    fun stop() {
        server?.stop()
        server = null
    }

    fun isRunning(): Boolean = server != null && !server!!.isStopped
}
