package com.filedroid.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class FtpClient @Inject constructor() : RemoteClient {

    private var client: FTPClient = FTPClient()

    fun useFtps() { client = FTPSClient("TLS", false) }

    override suspend fun connect(host: String, port: Int, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                client.connectTimeout = 10_000
                client.defaultTimeout = 10_000
                client.connect(host, port)
                check(client.login(username, password)) { "Login failed" }
                client.enterLocalPassiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)
            }
        }

    override suspend fun connectAnonymous(host: String, port: Int): Result<Unit> =
        connect(host, port, "anonymous", "anonymous@")

    override suspend fun listDirectory(path: String): Result<List<RemoteFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                (client.listFiles(path) ?: emptyArray()).map { f ->
                    RemoteFile(
                        name = f.name,
                        path = "$path/${f.name}".replace("//", "/"),
                        isDirectory = f.isDirectory,
                        size = f.size,
                        lastModified = f.timestamp?.timeInMillis ?: 0L
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }
        }

    override suspend fun download(remotePath: String, out: OutputStream): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                check(client.retrieveFile(remotePath, out)) { "Download failed: $remotePath" }
            }
        }

    override suspend fun upload(inputStream: InputStream, remotePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                check(client.storeFile(remotePath, inputStream)) { "Upload failed: $remotePath" }
            }
        }

    override suspend fun createDirectory(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> { check(client.makeDirectory(path)) { "mkdir failed: $path" } }
        }

    override suspend fun rename(from: String, to: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> { check(client.rename(from, to)) { "Rename failed" } }
        }

    override suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                val deleted = client.deleteFile(path) || client.removeDirectory(path)
                check(deleted) { "Delete failed: $path" }
            }
        }

    override fun disconnect() { runCatching { client.logout(); client.disconnect() } }
    override fun isConnected(): Boolean = client.isConnected
}
