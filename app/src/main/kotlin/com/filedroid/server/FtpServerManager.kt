package com.filedroid.server

import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.FtpFileSystemView
import org.apache.ftpserver.ftplet.User
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.TransferRatePermission
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.usermanager.impl.PropertiesUserManager
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory
import org.apache.ftpserver.FtpServer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpServerManager @Inject constructor() {

    private var server: FtpServer? = null

    fun start(config: ServerConfig): Result<Unit> = runCatching {
        val factory = FtpServerFactory()

        // Listener
        val listenerFactory = ListenerFactory()
        listenerFactory.port = config.ftpPort
        factory.addListener("default", listenerFactory.createListener())

        // User manager
        val userManager = factory.userManager
        val user = BaseUser().apply {
            name = config.username
            password = config.password
            homeDirectory = config.rootPath
            isEnabled = true
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
                isEnabled = true
                authorities = listOf(ConcurrentLoginPermission(config.maxSessions, config.maxSessions))
            }
            userManager.save(anon)
        }

        // Native filesystem rooted at config.rootPath
        factory.fileSystem = NativeFileSystemFactory()

        server = factory.createServer()
        server!!.start()
    }

    fun stop() {
        server?.stop()
        server = null
    }

    fun isRunning(): Boolean = server != null && !server!!.isStopped
}
