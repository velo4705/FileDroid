package com.filedroid.tunnel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages tunnel bridging — connects local server ports to remote clients via the relay.
 *
 * When acting as **host**:
 * - Opens a local ServerSocket for each protocol (FTP/SFTP)
 * - For each incoming local connection, opens a stream on the relay
 * - Relays data between the local server socket and the relay stream
 *
 * When acting as **client**:
 * - Opens a local ServerSocket that the FTP/SFTP client connects to
 * - For each local connection, opens a stream on the relay to the host
 * - Relays data between the local client socket and the relay stream
 */
@Singleton
class TunnelManager @Inject constructor(
    private val relayClient: RelayClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Local proxy server sockets — these let local clients connect to the tunnel. */
    private var localProxies = mutableListOf<ServerSocket>()

    /** Active stream bridges — each maps a stream ID to a local socket. */
    private val bridges = mutableMapOf<Int, Socket>()

    private var nextStreamId = 1

    val state: StateFlow<TunnelState> = relayClient.state

    /**
     * Start hosting tunnel endpoints.
     * Creates local proxy sockets on the given ports and bridges them through the relay.
     */
    fun startHosting(relayConfig: TunnelConfig, ftpPort: Int, sftpPort: Int) {
        relayClient.onDataReceived = { streamId, data -> handleDataFromRelay(streamId, data) }
        relayClient.onStreamOpened = { streamId -> handleStreamOpened(streamId) }
        relayClient.onStreamClosed = { streamId -> handleStreamClosed(streamId) }

        relayClient.connectAsHost(relayConfig)

        relayClient.onTunnelReady = { address ->
            // Create local proxy sockets that forward to the relay
            if (ftpPort > 0) createLocalProxy(ftpPort, "ftp", relayConfig.tunnelId)
            if (sftpPort > 0) createLocalProxy(sftpPort, "sftp", relayConfig.tunnelId)
        }
    }

    /**
     * Connect as a client to a remote host via the relay.
     * Creates a local proxy socket so the FTP/SFTP client connects locally, then gets
     * relayed to the remote host.
     */
    fun startClient(relayConfig: TunnelConfig) {
        relayClient.onDataReceived = { streamId, data -> handleDataFromRelay(streamId, data) }
        relayClient.onStreamOpened = { streamId -> handleStreamOpened(streamId) }
        relayClient.onStreamClosed = { streamId -> handleStreamClosed(streamId) }

        relayClient.connectAsClient(relayConfig)
    }

    private fun createLocalProxy(localPort: Int, protocol: String, tunnelId: String) {
        scope.launch {
            try {
                // Bind to localhost only — external connections go through the relay
                val serverSocket = ServerSocket(localPort, 50, java.net.InetAddress.getByName("127.0.0.1"))
                localProxies.add(serverSocket)

                while (!serverSocket.isClosed) {
                    val localSocket = serverSocket.accept()
                    val streamId = synchronized(this) { nextStreamId++ }
                    bridges[streamId] = localSocket

                    // Notify relay that a new stream is opened
                    relayClient.sendControl(
                        """{"action":"open_stream","tunnelId":"$tunnelId","streamId":$streamId,"protocol":"$protocol"}"""
                    )

                    // Start reading from local socket and sending to relay
                    launch { bridgeLocalToRelay(streamId, localSocket) }
                }
            } catch (e: Exception) {
                // Proxy socket closed or error
            }
        }
    }

    private suspend fun bridgeLocalToRelay(streamId: Int, socket: Socket) {
        try {
            val input = socket.getInputStream()
            val buf = ByteArray(8192)
            while (!socket.isClosed) {
                val n = withContext(Dispatchers.IO) { input.read(buf) }
                if (n == -1) break
                relayClient.send(streamId, buf.copyOf(n))
            }
        } catch (_: Exception) {
        } finally {
            handleStreamClosed(streamId)
        }
    }

    private fun handleStreamOpened(streamId: Int) {
        // For client mode — the relay tells us a stream is ready to receive data
        // The actual bridging is done when data arrives
    }

    private fun handleStreamClosed(streamId: Int) {
        synchronized(this) {
            bridges.remove(streamId)?.let { socket ->
                runCatching { socket.close() }
            }
        }
    }

    private fun handleDataFromRelay(streamId: Int, data: ByteArray) {
        val socket = bridges[streamId] ?: return
        try {
            val output = socket.getOutputStream()
            output?.write(data)
            output?.flush()
        } catch (_: Exception) {
            handleStreamClosed(streamId)
        }
    }

    fun stop() {
        localProxies.forEach { runCatching { it.close() } }
        localProxies.clear()
        bridges.values.forEach { runCatching { it.close() } }
        bridges.clear()
        relayClient.disconnect()
    }

    fun isActive() = relayClient.isConnected()
}
