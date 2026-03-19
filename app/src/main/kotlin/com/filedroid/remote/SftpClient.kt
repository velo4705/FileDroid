package com.filedroid.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Build an SSHClient config that avoids X25519/X448 (requires BC on Android). */
private fun safeConfig() = DefaultConfig()

class SftpClient @Inject constructor() : RemoteClient {

    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null

    override suspend fun connect(host: String, port: Int, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                val client = SSHClient(safeConfig())
                client.addHostKeyVerifier(PromiscuousVerifier())
                client.connectTimeout = 10_000
                client.connect(host, port)
                client.authPassword(username, password)
                sftp = client.newSFTPClient()
                ssh = client
            }
        }

    /**
     * R2.8 — authenticate with a PEM/OpenSSH private key instead of a password.
     */
    suspend fun connectWithKey(
        host: String,
        port: Int,
        username: String,
        privateKeyPem: String,
        passphrase: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching<Unit> {
            val keyFile = java.io.File.createTempFile("sshj_key_", null).apply {
                writeText(privateKeyPem)
                deleteOnExit()
            }
            val client = SSHClient(safeConfig())
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 10_000
            client.connect(host, port)
            val keyProvider: KeyProvider = if (passphrase.isNullOrEmpty()) {
                client.loadKeys(keyFile.absolutePath)
            } else {
                client.loadKeys(keyFile.absolutePath, passphrase)
            }
            client.authPublickey(username, keyProvider)
            sftp = client.newSFTPClient()
            ssh = client
            keyFile.delete()
        }
    }

    override suspend fun connectAnonymous(host: String, port: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("SFTP does not support anonymous connections"))

    override suspend fun listDirectory(path: String): Result<List<RemoteFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                sftp!!.ls(path).map { entry ->
                    RemoteFile(
                        name = entry.name,
                        path = "$path/${entry.name}".replace("//", "/"),
                        isDirectory = entry.isDirectory,
                        size = entry.attributes.size,
                        lastModified = entry.attributes.mtime * 1000L
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }
        }

    override suspend fun download(remotePath: String, out: OutputStream): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                sftp!!.open(remotePath).use { file ->
                    file.ReadAheadRemoteFileInputStream(16).use { it.copyTo(out) }
                }
            }
        }

    override suspend fun upload(inputStream: InputStream, remotePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                sftp!!.open(
                    remotePath,
                    setOf(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
                ).use { file ->
                    file.RemoteFileOutputStream().use { inputStream.copyTo(it) }
                }
            }
        }

    override suspend fun createDirectory(path: String): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching<Unit> { sftp!!.mkdir(path) } }

    override suspend fun rename(from: String, to: String): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching<Unit> { sftp!!.rename(from, to) } }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching<Unit> { sftp!!.rm(path) } }

    override fun disconnect() {
        runCatching { sftp?.close() }
        runCatching { ssh?.disconnect() }
        sftp = null; ssh = null
    }

    override fun isConnected(): Boolean = ssh?.isConnected == true
}
