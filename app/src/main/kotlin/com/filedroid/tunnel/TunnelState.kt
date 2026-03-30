package com.filedroid.tunnel

enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class TunnelState(
    val status: TunnelStatus = TunnelStatus.DISCONNECTED,
    val relayUrl: String = "",
    val tunnelId: String = "",
    val error: String? = null,
    /** The public-facing address on the relay, e.g. "relay.filedroid.io:8021" */
    val relayAddress: String = "",
    /** Local proxy ports available for the FTP/SFTP client to connect to when tunnel is active */
    val localFtpPort: Int = 0,
    val localSftpPort: Int = 0
)
