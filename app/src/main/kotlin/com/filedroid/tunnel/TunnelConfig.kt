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
    val password: String = ""
)
