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
    val bindAddress: String = ""
)
