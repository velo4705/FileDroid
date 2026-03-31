package com.filedroid.tunnel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * - Opens local ServerSockets (2121 for FTP, 2222 for SFTP)
 * - When a local FTP/SFTP client connects, sends open_stream to the relay
 * - The relay forwards the stream to the host
 * - The host connects to its local FTP/SFTP server and bridges data
 * - Data flows: local client → local proxy → relay → host local server
 */
@Singleton
class TunnelManager @Inject constructor(
    private val relayClient: RelayClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Local proxy server sockets — these let local clients connect to the tunnel. */
    private var localProxies = mutableListOf<ServerSocket>()

    /** Active stream bridges — each maps a stream ID to a local socket. Thread-safe. */
    private val bridges = java.util.concurrent.ConcurrentHashMap<Int, Socket>()

    /** Pending data buffers for streams whose local socket isn't ready yet (host-side). */
    private val pendingBuffers = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.ConcurrentLinkedQueue<ByteArray>>()

    private val nextStreamId = java.util.concurrent.atomic.AtomicInteger(1)

    private var currentTunnelId: String = ""

    /** Host-side: local ports for the FTP and SFTP servers (to connect incoming streams to). */
    private var hostFtpPort: Int = 0
    private var hostSftpPort: Int = 0

    val state = relayClient.state

    /** Callback invoked when the relay assigns public ports. */
    var onPublicPortsUpdated: ((Map<String, Int>) -> Unit)? = null

    fun startHosting(relayConfig: TunnelConfig, ftpPort: Int, sftpPort: Int) {
        // Close any leftover local proxies from a previous client session on this device.
        // This prevents proxy sockets from intercepting connections meant for the SFTP server.
        if (localProxies.isNotEmpty()) {
            android.util.Log.w("TunnelManager", "startHosting: clearing ${localProxies.size} leftover local proxies")
            localProxies.forEach { runCatching { it.close() } }
            localProxies.clear()
        }
        bridges.clear()
        pendingBuffers.clear()

        currentTunnelId = relayConfig.tunnelId
        hostFtpPort = ftpPort
        hostSftpPort = sftpPort
        relayClient.onDataReceived = { streamId, data -> handleDataFromRelay(streamId, data) }
        relayClient.onStreamOpened = { streamId, protocol -> handleStreamOpenedAsHost(streamId, protocol) }
        relayClient.onStreamClosed = { streamId -> handleStreamClosed(streamId) }

        relayClient.connectAsHost(relayConfig)

        scope.launch {
            relayClient.state.collect { state ->
                if (state.status == TunnelStatus.CONNECTED && state.publicPorts.isNotEmpty()) {
                    onPublicPortsUpdated?.invoke(state.publicPorts)
                }
            }
        }
    }

    /**
     * Connect as a client to a remote host via the relay.
     * Creates local proxy sockets so the FTP/SFTP client (RemoteBrowserViewModel)
     * can connect to localhost, and the tunnel bridges through the relay to the host.
     */
    fun startClient(relayConfig: TunnelConfig, ftpPort: Int = 2121, sftpPort: Int = 2222) {
        android.util.Log.d("TunnelManager", "startClient: tunnelId=${relayConfig.tunnelId} relay=${relayConfig.relayUrl}")

        // Only close existing proxies if ports changed (avoids killing proxies mid-use during reconnect)
        val needsNewProxies = localProxies.isEmpty()
                || !localProxies.any { it.localPort == ftpPort }
                || !localProxies.any { it.localPort == sftpPort }

        // Disconnect the relay cleanly — don't kill local proxies since they may still be needed
        relayClient.disconnect()
        bridges.clear()
        nextStreamId.set(1)

        currentTunnelId = relayConfig.tunnelId
        relayClient.onDataReceived = { streamId, data -> handleDataFromRelay(streamId, data) }
        relayClient.onStreamOpened = { streamId, protocol -> handleStreamOpenedAsClient(streamId) }
        relayClient.onStreamClosed = { streamId -> handleStreamClosed(streamId) }

        // Create local proxy sockets only if needed
        if (needsNewProxies) {
            // Close old proxies first
            localProxies.forEach { runCatching { it.close() } }
            localProxies.clear()

            if (ftpPort > 0) createLocalProxyBlocking(ftpPort, "ftp", relayConfig.tunnelId)
            if (sftpPort > 0) createLocalProxyBlocking(sftpPort, "sftp", relayConfig.tunnelId)
        }

        relayClient.connectAsClient(relayConfig)
    }

    /**
     * Binds a local ServerSocket synchronously, then starts an async accept loop.
     * This ensures the port is listening before the caller continues.
     */
    private fun createLocalProxyBlocking(localPort: Int, protocol: String, tunnelId: String) {
        val serverSocket = java.net.ServerSocket(localPort, 50, java.net.InetAddress.getByName("127.0.0.1"))
        localProxies.add(serverSocket)
        android.util.Log.d("TunnelManager", "Local proxy bound: $protocol://127.0.0.1:$localPort")

        scope.launch {
            try {
                while (!serverSocket.isClosed) {
                    val localSocket = withContext(Dispatchers.IO) { serverSocket.accept() }
                    val streamId = nextStreamId.getAndIncrement()
                    bridges[streamId] = localSocket
                    android.util.Log.d("TunnelManager", "Local proxy accepted: $protocol:$localPort → stream #$streamId")

                    // Tell the relay to open a stream to the other side
                    relayClient.sendControl(
                        """{"action":"open_stream","tunnelId":"$tunnelId","streamId":$streamId,"protocol":"$protocol"}"""
                    )

                    launch { bridgeLocalToRelay(streamId, localSocket) }
                }
            } catch (e: Exception) {
                android.util.Log.e("TunnelManager", "Local proxy accept loop FAILED on port $localPort ($protocol): ${e.message}", e)
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
                android.util.Log.d("TunnelManager", "bridgeLocalToRelay: stream#$streamId → relay $n bytes")
                relayClient.send(streamId, buf.copyOf(n))
            }
        } catch (e: Exception) {
            android.util.Log.e("TunnelManager", "bridgeLocalToRelay: stream#$streamId error: ${e.message}")
        } finally {
            handleStreamClosed(streamId)
        }
    }

    /**
     * Host-side: when the relay sends a stream_open event (a client connected via the relay),
     * connect to the local FTP/SFTP server and bridge the stream.
     */
    private fun handleStreamOpenedAsHost(streamId: Int, protocol: String) {
        android.util.Log.d("TunnelManager", "handleStreamOpenedAsHost: stream#$streamId protocol=$protocol ftpPort=$hostFtpPort sftpPort=$hostSftpPort")
        val port = when (protocol) {
            "ftp" -> hostFtpPort
            "sftp" -> hostSftpPort
            else -> {
                if (hostFtpPort > 0) hostFtpPort
                else if (hostSftpPort > 0) hostSftpPort
                else return
            }
        }
        if (port <= 0) {
            android.util.Log.e("TunnelManager", "handleStreamOpenedAsHost: port is $port, aborting")
            return
        }

        android.util.Log.d("TunnelManager", "handleStreamOpenedAsHost: connecting to 127.0.0.1:$port")
        // Create a pending buffer so data arriving before the local socket is ready gets queued
        pendingBuffers[streamId] = java.util.concurrent.ConcurrentLinkedQueue()

        scope.launch {
            try {
                val socket = withContext(Dispatchers.IO) {
                    Socket("127.0.0.1", port)
                }
                android.util.Log.d("TunnelManager", "handleStreamOpenedAsHost: connected to 127.0.0.1:$port, bridging stream#$streamId")
                // Flush any data that arrived while connecting
                val output = socket.getOutputStream()
                val buffer = pendingBuffers.remove(streamId)
                if (buffer != null) {
                    while (true) {
                        val data = buffer.poll() ?: break
                        output.write(data)
                        output.flush()
                    }
                }
                bridges[streamId] = socket
                launch { bridgeLocalToRelay(streamId, socket) }
            } catch (e: Exception) {
                pendingBuffers.remove(streamId)
                bridges.remove(streamId)
                android.util.Log.e("TunnelManager", "handleStreamOpenedAsHost: FAILED to connect to 127.0.0.1:$port — ${e.message}", e)
                relayClient.sendControl(
                    """{"action":"close_stream","tunnelId":"$currentTunnelId","streamId":$streamId}"""
                )
            }
        }
    }

    /**
     * Client-side: when the relay sends a stream_open event back to us,
     * we don't need to do anything — the stream is already in our bridges map
     * from createLocalProxy. The stream_open is just an acknowledgment.
     */
    private fun handleStreamOpenedAsClient(streamId: Int) {
        // No-op: the client already has the local socket in bridges
    }

    private fun handleStreamClosed(streamId: Int) {
        bridges.remove(streamId)?.let { socket ->
            runCatching { socket.close() }
        }
        pendingBuffers.remove(streamId)
    }

    private fun handleDataFromRelay(streamId: Int, data: ByteArray) {
        val socket = bridges[streamId]
        if (socket != null) {
            try {
                val output = socket.getOutputStream()
                output?.write(data)
                output?.flush()
                android.util.Log.d("TunnelManager", "handleDataFromRelay: stream#$streamId ← relay ${data.size} bytes")
            } catch (e: Exception) {
                android.util.Log.e("TunnelManager", "handleDataFromRelay: stream#$streamId write error: ${e.message}")
                handleStreamClosed(streamId)
            }
        } else {
            // No bridge yet — buffer for when the local socket connects (host-side race)
            val buffer = pendingBuffers[streamId]
            if (buffer != null) {
                buffer.add(data)
                android.util.Log.d("TunnelManager", "handleDataFromRelay: stream#$streamId ← relay ${data.size} bytes (buffered)")
            } else {
                android.util.Log.w("TunnelManager", "handleDataFromRelay: stream#$streamId — no bridge, dropping ${data.size} bytes")
            }
        }
    }

    fun stop() {
        localProxies.forEach { runCatching { it.close() } }
        localProxies.clear()
        bridges.values.forEach { runCatching { it.close() } }
        bridges.clear()
        pendingBuffers.clear()
        relayClient.disconnect()
    }

    fun isActive() = relayClient.isConnected()
}
