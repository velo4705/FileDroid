package com.filedroid.tunnel

/**
 * Configuration for a relay tunnel.
 *
 * The relay server sits between two FileDroid instances:
 * - The **host** runs the FTP/SFTP server and registers a tunnel on the relay
 * - The **client** connects to the relay and requests to join that tunnel
 * - The relay forwards raw TCP bytes between them over WebSocket
 */
data class TunnelConfig(
    /** WebSocket URL of the relay server, e.g. "wss://relay.filedroid.io/ws" */
    val relayUrl: String,
    /** Unique tunnel ID shared between host and client (like a room code) */
    val tunnelId: String,
    /** Username for relay authentication */
    val username: String = "",
    /** Password for relay authentication */
    val password: String = "",
    /**
     * Public ports to expose on the relay. Only used by hosts.
     * Each entry maps protocol name to local port number.
     * The relay will open TCP listeners on public ports and bridge
     * incoming connections to these local ports through the tunnel.
     * e.g. mapOf("ftp" to 2121, "sftp" to 2222)
     */
    val publicPorts: Map<String, Int> = emptyMap()
) {
    companion object {
        /** Default relay server. Users can override this with their own server. */
        const val DEFAULT_RELAY_URL = "wss://filedroid-production.up.railway.app/ws"
    }
}
