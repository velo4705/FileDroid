package com.filedroid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Protocol { FTP, FTPS, SFTP }

@Entity(tableName = "connection_profiles")
data class ConnectionProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val protocol: Protocol,
    val host: String,
    val port: Int,
    val username: String,
    /** Encrypted key name in CredentialStore — never stored plaintext here. */
    val credentialKey: String,
    val initialRemotePath: String = "/",
    val anonymous: Boolean = false,
    val usePrivateKey: Boolean = false
)
