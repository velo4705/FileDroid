package com.filedroid.server

data class ServerConfig(
    val ftpPort: Int = 2121,
    val sftpPort: Int = 2222,
    val rootPath: String = "",
    val username: String = "",
    val password: String = "",
    val ftpEnabled: Boolean = false,
    val sftpEnabled: Boolean = false,
    val anonymousEnabled: Boolean = false,
    val maxSessions: Int = 5,
    val idleTimeoutSeconds: Int = 300,
    /** R7.4 — IP address of the interface to bind to. Empty string = bind to all interfaces (0.0.0.0). */
    val bindAddress: String = "",
    /** M10 — Enable relay tunnel for mobile data access. */
    val tunnelEnabled: Boolean = false,
    /** M10 — Relay server WebSocket URL. */
    val relayUrl: String = "",
    /** M10 — Tunnel ID shared between host and client. */
    val tunnelId: String = ""
)
