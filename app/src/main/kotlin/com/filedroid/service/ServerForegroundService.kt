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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ServerForegroundService : Service() {

    @Inject lateinit var ftpManager: FtpServerManager
    @Inject lateinit var sftpManager: SftpServerManager
    @Inject lateinit var tunnelManager: com.filedroid.tunnel.TunnelManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeConfig: ServerConfig? = null
    private var connectivityManager: ConnectivityManager? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNotification()
        override fun onLost(network: Network) = updateNotification()
    }

    companion object {
        const val ACTION_START = "com.filedroid.START_SERVER"
        const val ACTION_STOP = "com.filedroid.STOP_SERVER"
        const val ACTION_PUBLIC_PORTS = "com.filedroid.PUBLIC_PORTS"
        const val EXTRA_CONFIG = "server_config"
        const val EXTRA_PUBLIC_PORTS = "public_ports"
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

        // Pre-warm RSA key generation so the first SFTP start is fast
        serviceScope.launch {
            runCatching { java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
        }
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
        val resolvedRoot = config.rootPath.ifBlank {
            android.os.Environment.getExternalStorageDirectory().absolutePath
        }
        val resolved = config.copy(rootPath = resolvedRoot)
        activeConfig = resolved

        // Must call startForeground on the main thread before doing any work
        startForeground(NOTIF_ID, buildNotification(resolved))

        // Server socket binding must happen off the main thread
        serviceScope.launch {
            if (resolved.ftpEnabled) ftpManager.start(resolved).onFailure {
                updateNotificationError("FTP failed: ${it.message}")
                if (!resolved.sftpEnabled) stopSelf()
            }
            if (resolved.sftpEnabled) {
                sftpManager.start(resolved).onFailure {
                    val msg = it.message ?: it.cause?.message ?: it::class.simpleName ?: "unknown error"
                    updateNotificationError("SFTP failed: $msg")
                    if (!resolved.ftpEnabled || !ftpManager.isRunning()) stopSelf()
                }
            }
            // M10 — Start relay tunnel if configured
            if (resolved.tunnelEnabled && resolved.relayUrl.isNotBlank() && resolved.tunnelId.isNotBlank()) {
                val publicPorts = mutableMapOf<String, Int>()
                if (resolved.ftpEnabled) {
                    publicPorts["ftp"] = resolved.ftpPort
                    // Register passive data ports for FTP passive mode
                    publicPorts["ftp-data-1"] = 20000
                    publicPorts["ftp-data-2"] = 20001
                }
                if (resolved.sftpEnabled) publicPorts["sftp"] = resolved.sftpPort
                val tunnelConfig = com.filedroid.tunnel.TunnelConfig(
                    relayUrl = resolved.relayUrl,
                    tunnelId = resolved.tunnelId,
                    username = resolved.username,
                    password = resolved.password,
                    publicPorts = publicPorts
                )
                tunnelManager.onPublicPortsUpdated = { ports ->
                    // Broadcast public ports for the UI to display
                    val intent = android.content.Intent(ACTION_PUBLIC_PORTS).apply {
                        putExtra(EXTRA_PUBLIC_PORTS, android.os.Bundle().apply {
                            for ((k, v) in ports) putInt(k, v)
                        })
                    }
                    sendBroadcast(intent)
                }
                tunnelManager.startHosting(
                    tunnelConfig,
                    ftpPort = if (resolved.ftpEnabled) resolved.ftpPort else 0,
                    sftpPort = if (resolved.sftpEnabled) resolved.sftpPort else 0
                )
            }
        }
    }

    private fun stopServers() {
        serviceScope.launch {
            ftpManager.stop()
            sftpManager.stop()
            tunnelManager.stop()
        }
        activeConfig = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val config = activeConfig ?: return
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(config))
    }

    private fun updateNotificationError(error: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("FileDroid Server Error")
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setOngoing(false)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        serviceScope.cancel()
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
    putBoolean("tunnelEnabled", tunnelEnabled)
    putString("relayUrl", relayUrl)
    putString("tunnelId", tunnelId)
}

private fun android.os.Bundle.toServerConfig() = ServerConfig(
    ftpPort = getInt("ftpPort", 2121), sftpPort = getInt("sftpPort", 2222),
    rootPath = getString("rootPath", ""), username = getString("username", ""),
    password = getString("password", ""),
    ftpEnabled = getBoolean("ftpEnabled"), sftpEnabled = getBoolean("sftpEnabled"),
    anonymousEnabled = getBoolean("anonymousEnabled"),
    maxSessions = getInt("maxSessions", 5), idleTimeoutSeconds = getInt("idleTimeout", 300),
    bindAddress = getString("bindAddress", ""),
    tunnelEnabled = getBoolean("tunnelEnabled"),
    relayUrl = getString("relayUrl", ""),
    tunnelId = getString("tunnelId", "")
)
