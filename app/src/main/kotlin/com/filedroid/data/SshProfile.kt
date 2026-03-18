package com.filedroid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_profiles")
data class SshProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    /** Key in CredentialStore where the password is stored. */
    val credentialKey: String
)
