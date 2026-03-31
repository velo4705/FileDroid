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
    val localSftpPort: Int = 0,
    /** Public ports assigned by the relay, e.g. {"ftp": 3021, "sftp": 3022}
     *  Any FTP/SFTP client worldwide can connect to these ports to reach the host. */
    val publicPorts: Map<String, Int> = emptyMap(),
    /** Device name of the peer (host's device name shown to client, or client count shown to host) */
    val peerDeviceName: String = ""
)
