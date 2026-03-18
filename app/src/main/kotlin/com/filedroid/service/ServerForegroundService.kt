package com.filedroid.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.filedroid.MainActivity
import com.filedroid.server.FtpServerManager
import com.filedroid.server.ServerConfig
import com.filedroid.server.SftpServerManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ServerForegroundService : Service() {

    @Inject lateinit var ftpManager: FtpServerManager
    @Inject lateinit var sftpManager: SftpServerManager

    companion object {
        const val ACTION_START = "com.filedroid.START_SERVER"
        const val ACTION_STOP = "com.filedroid.STOP_SERVER"
        const val EXTRA_CONFIG = "server_config"
        private const val CHANNEL_ID = "filedroid_server"
        private const val NOTIF_ID = 1001

        fun startIntent(context: Context, config: ServerConfig) =
            Intent(context, ServerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG, config.toBundle())
            }

        fun stopIntent(context: Context) =
            Intent(context, ServerForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getBundleExtra(EXTRA_CONFIG)?.toServerConfig() ?: return START_NOT_STICKY
                startServers(config)
            }
            ACTION_STOP -> stopServers()
        }
        return START_STICKY
    }

    private fun startServers(config: ServerConfig) {
        val active = mutableListOf<String>()
        if (config.ftpEnabled) {
            ftpManager.start(config).onSuccess { active.add("FTP :${config.ftpPort}") }
        }
        if (config.sftpEnabled) {
            val keyFile = File(filesDir, "host_key.ser")
            sftpManager.start(config, keyFile).onSuccess { active.add("SFTP :${config.sftpPort}") }
        }
        val label = if (active.isEmpty()) "No servers running" else active.joinToString(" | ")
        startForeground(NOTIF_ID, buildNotification(label))
    }

    private fun stopServers() {
        ftpManager.stop()
        sftpManager.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(statusText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("FileDroid Server")
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "FileDroid Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "FTP/SFTP server status" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

// Minimal Bundle helpers to pass ServerConfig via Intent extras
private fun ServerConfig.toBundle() = android.os.Bundle().apply {
    putInt("ftpPort", ftpPort); putInt("sftpPort", sftpPort)
    putString("rootPath", rootPath); putString("username", username)
    putString("password", password)
    putBoolean("ftpEnabled", ftpEnabled); putBoolean("sftpEnabled", sftpEnabled)
    putBoolean("anonymousEnabled", anonymousEnabled)
    putInt("maxSessions", maxSessions); putInt("idleTimeout", idleTimeoutSeconds)
}

private fun android.os.Bundle.toServerConfig() = ServerConfig(
    ftpPort = getInt("ftpPort", 2121), sftpPort = getInt("sftpPort", 2222),
    rootPath = getString("rootPath", ""), username = getString("username", ""),
    password = getString("password", ""),
    ftpEnabled = getBoolean("ftpEnabled"), sftpEnabled = getBoolean("sftpEnabled"),
    anonymousEnabled = getBoolean("anonymousEnabled"),
    maxSessions = getInt("maxSessions", 5), idleTimeoutSeconds = getInt("idleTimeout", 300)
)
