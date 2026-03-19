package com.filedroid.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.filedroid.MainActivity
import com.filedroid.server.FtpServerManager
import com.filedroid.server.ServerConfig
import com.filedroid.server.SftpServerManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

@AndroidEntryPoint
class ServerForegroundService : Service() {

    @Inject lateinit var ftpManager: FtpServerManager
    @Inject lateinit var sftpManager: SftpServerManager

    private var activeConfig: ServerConfig? = null
    private var connectivityManager: ConnectivityManager? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNotification()
        override fun onLost(network: Network) = updateNotification()
    }

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
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivityManager?.registerNetworkCallback(req, networkCallback)
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
        // Fall back to external storage root if no root path was configured
        val resolvedRoot = config.rootPath.ifBlank {
            android.os.Environment.getExternalStorageDirectory().absolutePath
        }
        val resolved = config.copy(rootPath = resolvedRoot)
        activeConfig = resolved

        // Must call startForeground before any heavy work on Android 8+
        startForeground(NOTIF_ID, buildNotification(resolved))

        if (resolved.ftpEnabled) ftpManager.start(resolved).onFailure { stopSelf() }
        if (resolved.sftpEnabled) {
            val keyFile = File(filesDir, "host_key.ser")
            sftpManager.start(resolved, keyFile).onFailure { stopSelf() }
        }
    }

    private fun stopServers() {
        ftpManager.stop()
        sftpManager.stop()
        activeConfig = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val config = activeConfig ?: return
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(config))
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(config: ServerConfig) = run {
        val ip = getLocalIpAddress() ?: "unknown IP"
        val parts = mutableListOf<String>()
        if (config.ftpEnabled) parts.add("FTP $ip:${config.ftpPort}")
        if (config.sftpEnabled) parts.add("SFTP $ip:${config.sftpPort}")
        val statusText = if (parts.isEmpty()) "No servers running" else parts.joinToString("  |  ")

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
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "FileDroid Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "FTP/SFTP server status" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun getLocalIpAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    }.getOrNull()
}

// Bundle helpers
private fun ServerConfig.toBundle() = android.os.Bundle().apply {
    putInt("ftpPort", ftpPort); putInt("sftpPort", sftpPort)
    putString("rootPath", rootPath); putString("username", username)
    putString("password", password)
    putBoolean("ftpEnabled", ftpEnabled); putBoolean("sftpEnabled", sftpEnabled)
    putBoolean("anonymousEnabled", anonymousEnabled)
    putInt("maxSessions", maxSessions); putInt("idleTimeout", idleTimeoutSeconds)
    putString("bindAddress", bindAddress)
}

private fun android.os.Bundle.toServerConfig() = ServerConfig(
    ftpPort = getInt("ftpPort", 2121), sftpPort = getInt("sftpPort", 2222),
    rootPath = getString("rootPath", ""), username = getString("username", ""),
    password = getString("password", ""),
    ftpEnabled = getBoolean("ftpEnabled"), sftpEnabled = getBoolean("sftpEnabled"),
    anonymousEnabled = getBoolean("anonymousEnabled"),
    maxSessions = getInt("maxSessions", 5), idleTimeoutSeconds = getInt("idleTimeout", 300),
    bindAddress = getString("bindAddress", "")
)
