package com.filedroid.remote

import java.io.InputStream
import java.io.OutputStream

interface RemoteClient {
    suspend fun connect(host: String, port: Int, username: String, password: String): Result<Unit>
    suspend fun connectAnonymous(host: String, port: Int): Result<Unit>
    suspend fun listDirectory(path: String): Result<List<RemoteFile>>
    suspend fun download(remotePath: String, out: OutputStream): Result<Unit>
    suspend fun upload(inputStream: InputStream, remotePath: String): Result<Unit>
    suspend fun createDirectory(path: String): Result<Unit>
    suspend fun rename(from: String, to: String): Result<Unit>
    suspend fun delete(path: String): Result<Unit>
    fun disconnect()
    fun isConnected(): Boolean
}
