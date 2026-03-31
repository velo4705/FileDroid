package com.filedroid.tunnel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a WebSocket connection to the relay server.
 *
 * Protocol (text frames for control, binary frames for data):
 * - Client sends: {"action":"register","tunnelId":"xxx","role":"host"}
 * - Client sends: {"action":"join","tunnelId":"xxx","role":"client"}
 * - Server responds: {"status":"ok","tunnelId":"xxx","address":"relay:port"}
 * - Server responds: {"status":"error","message":"..."}
 * - Binary frames carry raw TCP data, prefixed with a 4-byte stream ID (big-endian)
 */
@Singleton
class RelayClient @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null

    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    /** Outgoing data stream — other components write here to send data through the tunnel. */
    private val outgoingData = Channel<ByteArray>(Channel.BUFFERED)

    /** Incoming data callback — set by the tunnel manager to receive forwarded data. */
    @Volatile var onDataReceived: ((streamId: Int, data: ByteArray) -> Unit)? = null

    /** Called when the relay confirms registration/join. */
    @Volatile var onTunnelReady: ((address: String) -> Unit)? = null

    /** Called when the relay signals a new peer stream is open. */
    @Volatile var onStreamOpened: ((streamId: Int, protocol: String) -> Unit)? = null

    /** Called when the relay signals a peer stream is closed. */
    @Volatile var onStreamClosed: ((streamId: Int) -> Unit)? = null

    /** Connect as the tunnel host (runs the FTP/SFTP server locally). */
    fun connectAsHost(config: TunnelConfig) = connect(config, "host")

    /** Connect as a tunnel client (wants to reach a remote FTP/SFTP server). */
    fun connectAsClient(config: TunnelConfig) = connect(config, "client")

    private fun connect(config: TunnelConfig, role: String) {
        // Clean up any existing connection before starting a new one
        cleanup()

        _state.update {
            it.copy(
                status = TunnelStatus.CONNECTING,
                relayUrl = config.relayUrl,
                tunnelId = config.tunnelId,
                error = null
            )
        }

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // no read timeout for WebSocket
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(config.relayUrl)
            .build()

        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send registration/join message
                val deviceName = android.os.Build.MODEL ?: "Unknown device"
                android.util.Log.d("RelayClient", "WebSocket opened, sending $role registration for tunnel ${config.tunnelId}")
                val msg = if (role == "host") {
                    val portsArray = config.publicPorts.entries.joinToString(",") { (proto, port) ->
                        """{"protocol":"$proto","port":$port}"""
                    }
                    """{"action":"register","tunnelId":"${config.tunnelId}","username":"${config.username}","password":"${config.password}","deviceName":"$deviceName","ports":[$portsArray]}"""
                } else {
                    """{"action":"join","tunnelId":"${config.tunnelId}","username":"${config.username}","password":"${config.password}","deviceName":"$deviceName"}"""
                }
                webSocket.send(msg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleControlMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryFrame(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.update { it.copy(status = TunnelStatus.DISCONNECTED) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e("RelayClient", "WebSocket failure: ${t.message}", t)
                _state.update { it.copy(status = TunnelStatus.ERROR, error = t.message) }
            }
        })
    }

    private fun handleControlMessage(text: String) {
        // Simple JSON parsing — avoids adding a JSON dependency for minimal messages
        if (text.contains("\"event\"") || text.contains("\"status\"")) {
            android.util.Log.d("RelayClient", "MSG: $text")
        }
        when {
            text.contains("\"status\":\"ok\"") -> {
                val addr = extractJsonValue(text, "address") ?: ""
                val publicPorts = parsePortsObject(text)
                val peerName = extractJsonValue(text, "deviceName") ?: ""
                android.util.Log.d("RelayClient", "Tunnel connected! peer=$peerName relayAddr=$addr")
                _state.update { it.copy(status = TunnelStatus.CONNECTED, relayAddress = addr, publicPorts = publicPorts, peerDeviceName = peerName) }
                onTunnelReady?.invoke(addr)
            }
            text.contains("\"status\":\"error\"") -> {
                val msg = extractJsonValue(text, "message") ?: "Unknown error"
                android.util.Log.e("RelayClient", "Tunnel error: $msg")
                _state.update { it.copy(status = TunnelStatus.ERROR, error = msg) }
            }
            text.contains("\"event\":\"stream_open\"") -> {
                val id = extractJsonValue(text, "streamId")?.toIntOrNull() ?: return
                val protocol = extractJsonValue(text, "protocol") ?: "unknown"
                onStreamOpened?.invoke(id, protocol)
            }
            text.contains("\"event\":\"stream_close\"") -> {
                val id = extractJsonValue(text, "streamId")?.toIntOrNull() ?: return
                onStreamClosed?.invoke(id)
            }
            text.contains("\"event\":\"client_joined\"") -> {
                val clientCount = extractJsonValue(text, "clientCount") ?: ""
                val clientName = extractJsonValue(text, "deviceName") ?: ""
                val display = if (clientName.isNotBlank()) "$clientName ($clientCount connected)" else "$clientCount connected"
                _state.update { it.copy(peerDeviceName = display) }
            }
        }
    }

    /**
     * Binary frame format:
     * [4 bytes: stream ID (big-endian)] [N bytes: payload]
     */
    private fun handleBinaryFrame(bytes: ByteArray) {
        if (bytes.size < 4) return
        val streamId = ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
        val payload = bytes.copyOfRange(4, bytes.size)
        onDataReceived?.invoke(streamId, payload)
    }

    /** Send data through the tunnel for a specific stream. */
    fun send(streamId: Int, data: ByteArray) {
        if (!isConnected()) return
        val frame = ByteArray(4 + data.size)
        frame[0] = ((streamId shr 24) and 0xFF).toByte()
        frame[1] = ((streamId shr 16) and 0xFF).toByte()
        frame[2] = ((streamId shr 8) and 0xFF).toByte()
        frame[3] = (streamId and 0xFF).toByte()
        System.arraycopy(data, 0, frame, 4, data.size)
        webSocket?.send(okio.ByteString.of(*frame))
    }

    /** Send a control message (JSON text frame). */
    fun sendControl(json: String) {
        webSocket?.send(json)
    }

    fun disconnect() {
        cleanup()
        onTunnelReady = null
        onDataReceived = null
        onStreamOpened = null
        onStreamClosed = null
        _state.update { TunnelState() }
    }

    /** Internal cleanup — closes WebSocket without clearing callbacks (safe to call mid-reconnect). */
    private fun cleanup() {
        webSocket?.close(1000, "disconnect")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
    }

    fun isConnected() = _state.value.status == TunnelStatus.CONNECTED

    companion object {
        /** Extract a string or number value from a simple JSON object. */
        fun extractJsonValue(json: String, key: String): String? {
            // Match both quoted strings and unquoted numbers
            val pattern = "\"$key\"\\s*:\\s*(?:\"([^\"]*)\"|([\\d.]+))"
            val match = Regex(pattern).find(json) ?: return null
            return match.groupValues[1].ifEmpty { match.groupValues[2] }
        }

        /**
         * Parse a simple JSON object of string/number pairs into a map.
         * e.g. '{"ftp":3021,"sftp":3022}' → {"ftp" to 3021, "sftp" to 3022}
         */
        fun parsePortsObject(json: String): Map<String, Int> {
            val pattern = "\"ports\"\\s*:\\s*\\{([^}]*)\\}"
            val match = Regex(pattern).find(json) ?: return emptyMap()
            val inner = match.groupValues.getOrNull(1) ?: return emptyMap()
            val pairs = "\"([a-zA-Z0-9_]+)\"\\s*:\\s*(\\d+)".toRegex()
            return pairs.findAll(inner).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        }
    }
}
