package com.filedroid.transfer

import com.filedroid.remote.RemoteClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferEngine @Inject constructor() {

    private val _jobs = MutableStateFlow<List<TransferJob>>(emptyList())
    val jobs: StateFlow<List<TransferJob>> = _jobs.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    fun enqueueDownload(client: RemoteClient, remotePath: String, localPath: String, totalBytes: Long = 0L, onDone: (() -> Unit)? = null) {
        val job = TransferJob(
            direction = TransferDirection.DOWNLOAD,
            fileName = remotePath.substringAfterLast("/"),
            localPath = localPath,
            remotePath = remotePath,
            totalBytes = totalBytes
        )
        addJob(job)
        execute(job, client, onDone)
    }

    fun enqueueUpload(client: RemoteClient, localPath: String, remotePath: String, onDone: (() -> Unit)? = null) {
        val file = File(localPath)
        val job = TransferJob(
            direction = TransferDirection.UPLOAD,
            fileName = file.name,
            localPath = localPath,
            remotePath = remotePath,
            totalBytes = file.length()
        )
        addJob(job)
        execute(job, client, onDone)
    }

    /** Recursively download a remote directory to a local path. */
    fun enqueueDirectoryDownload(client: RemoteClient, remotePath: String, localPath: String, onDone: (() -> Unit)? = null) {
        val job = TransferJob(
            direction = TransferDirection.DOWNLOAD,
            fileName = remotePath.substringAfterLast("/") + "/",
            localPath = localPath,
            remotePath = remotePath,
            totalBytes = 0L
        )
        addJob(job)
        val coroutine = scope.launch {
            updateJob(job.id) { it.copy(status = TransferStatus.IN_PROGRESS) }
            val result = runCatching { downloadDirRecursive(client, remotePath, File(localPath)) }
            result.fold(
                onSuccess = {
                    updateJob(job.id) { it.copy(status = TransferStatus.DONE) }
                    onDone?.invoke()
                },
                onFailure = { e ->
                    if (e is CancellationException) updateJob(job.id) { it.copy(status = TransferStatus.CANCELLED) }
                    else updateJob(job.id) { it.copy(status = TransferStatus.FAILED, errorMessage = e.message) }
                }
            )
        }
        activeJobs[job.id] = coroutine
    }

    /** Recursively upload a local directory to a remote path. */
    fun enqueueDirectoryUpload(
        client: RemoteClient,
        localPath: String,
        remotePath: String,
        onDone: (() -> Unit)? = null
    ) {
        val dir = File(localPath)
        val job = TransferJob(
            direction = TransferDirection.UPLOAD,
            fileName = dir.name + "/",
            localPath = localPath,
            remotePath = remotePath,
            totalBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        )
        addJob(job)
        val coroutine = scope.launch {
            updateJob(job.id) { it.copy(status = TransferStatus.IN_PROGRESS) }
            val result = runCatching { uploadDirRecursive(client, dir, remotePath, job.id) }
            result.fold(
                onSuccess = {
                    updateJob(job.id) { it.copy(status = TransferStatus.DONE) }
                    onDone?.invoke()
                },
                onFailure = { e ->
                    if (e is CancellationException) updateJob(job.id) { it.copy(status = TransferStatus.CANCELLED) }
                    else updateJob(job.id) { it.copy(status = TransferStatus.FAILED, errorMessage = e.message) }
                }
            )
        }
        activeJobs[job.id] = coroutine
    }

    private suspend fun downloadDirRecursive(client: RemoteClient, remotePath: String, localDir: File) {
        localDir.mkdirs()
        val entries = client.listDirectory(remotePath).getOrThrow()
        for (entry in entries) {
            val childLocal = File(localDir, entry.name)
            if (entry.isDirectory) {
                downloadDirRecursive(client, entry.path, childLocal)
            } else {
                childLocal.parentFile?.mkdirs()
                val out = childLocal.outputStream()
                client.download(entry.path, out).also { out.close() }.getOrThrow()
            }
        }
    }

    private suspend fun uploadDirRecursive(client: RemoteClient, localDir: File, remotePath: String, jobId: String? = null) {
        client.createDirectory(remotePath) // ignore error if already exists
        for (child in (localDir.listFiles() ?: emptyArray())) {
            val childRemote = "$remotePath/${child.name}"
            if (child.isDirectory) {
                uploadDirRecursive(client, child, childRemote, jobId)
            } else {
                val input = if (jobId != null) {
                    ProgressInputStream(child.inputStream(), child.length()) { transferred, speed ->
                        // accumulate into parent job
                        _jobs.update { list ->
                            list.map { job ->
                                if (job.id == jobId)
                                    job.copy(
                                        transferredBytes = (job.transferredBytes + transferred).coerceAtMost(job.totalBytes),
                                        speedBytesPerSec = speed
                                    )
                                else job
                            }
                        }
                    }
                } else child.inputStream()
                client.upload(input, childRemote).also { input.close() }.getOrThrow()
            }
        }
    }

    fun cancel(jobId: String) {
        activeJobs[jobId]?.cancel()
        updateJob(jobId) { it.copy(status = TransferStatus.CANCELLED) }
    }

    fun retry(jobId: String, client: RemoteClient) {
        val job = _jobs.value.find { it.id == jobId } ?: return
        val reset = job.copy(status = TransferStatus.QUEUED, transferredBytes = 0L, errorMessage = null)
        updateJob(jobId) { reset }
        execute(reset, client)
    }

    fun clearCompleted() {
        _jobs.update { list -> list.filter { it.status != TransferStatus.DONE && it.status != TransferStatus.FAILED && it.status != TransferStatus.CANCELLED } }
    }

    private fun addJob(job: TransferJob) {
        _jobs.update { it + job }
    }

    private fun execute(job: TransferJob, client: RemoteClient, onDone: (() -> Unit)? = null) {
        val coroutine = scope.launch {
            updateJob(job.id) { it.copy(status = TransferStatus.IN_PROGRESS) }
            val result = if (job.direction == TransferDirection.DOWNLOAD) {
                runDownload(job, client)
            } else {
                runUpload(job, client)
            }
            result.fold(
                onSuccess = {
                    updateJob(job.id) { it.copy(status = TransferStatus.DONE, transferredBytes = it.totalBytes) }
                    onDone?.invoke()
                },
                onFailure = { e ->
                    if (e is CancellationException) {
                        updateJob(job.id) { it.copy(status = TransferStatus.CANCELLED) }
                    } else {
                        updateJob(job.id) { it.copy(status = TransferStatus.FAILED, errorMessage = e.message) }
                    }
                }
            )
        }
        activeJobs[job.id] = coroutine
    }

    private suspend fun runDownload(job: TransferJob, client: RemoteClient): Result<Unit> {
        val dest = File(job.localPath)
        dest.parentFile?.mkdirs()
        return try {
            val out = ProgressOutputStream(dest.outputStream(), job.totalBytes) { transferred, speed ->
                updateJob(job.id) { it.copy(transferredBytes = transferred, speedBytesPerSec = speed) }
            }
            client.download(job.remotePath, out).also { out.close() }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun runUpload(job: TransferJob, client: RemoteClient): Result<Unit> {
        val src = File(job.localPath)
        return try {
            val input = ProgressInputStream(src.inputStream(), job.totalBytes) { transferred, speed ->
                updateJob(job.id) { it.copy(transferredBytes = transferred, speedBytesPerSec = speed) }
            }
            client.upload(input, job.remotePath).also { input.close() }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateJob(id: String, transform: (TransferJob) -> TransferJob) {
        _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }
}

/** Wraps an OutputStream and reports progress. */
private class ProgressOutputStream(
    private val delegate: OutputStream,
    private val total: Long,
    private val onProgress: (transferred: Long, speedBps: Long) -> Unit
) : OutputStream() {
    private var transferred = 0L
    private var lastTime = System.currentTimeMillis()
    private var lastBytes = 0L

    override fun write(b: Int) { delegate.write(b); report(1) }
    override fun write(b: ByteArray, off: Int, len: Int) { delegate.write(b, off, len); report(len.toLong()) }
    override fun close() { delegate.close() }

    private fun report(bytes: Long) {
        transferred += bytes
        val now = System.currentTimeMillis()
        val elapsed = now - lastTime
        if (elapsed >= 500) {
            val speed = ((transferred - lastBytes) * 1000L) / elapsed
            onProgress(transferred, speed)
            lastTime = now; lastBytes = transferred
        }
    }
}

/** Wraps an InputStream and reports progress. */
private class ProgressInputStream(
    private val delegate: InputStream,
    private val total: Long,
    private val onProgress: (transferred: Long, speedBps: Long) -> Unit
) : InputStream() {
    private var transferred = 0L
    private var lastTime = System.currentTimeMillis()
    private var lastBytes = 0L

    override fun read(): Int = delegate.read().also { if (it != -1) report(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int =
        delegate.read(b, off, len).also { if (it > 0) report(it.toLong()) }
    override fun close() { delegate.close() }

    private fun report(bytes: Long) {
        transferred += bytes
        val now = System.currentTimeMillis()
        val elapsed = now - lastTime
        if (elapsed >= 500) {
            val speed = ((transferred - lastBytes) * 1000L) / elapsed
            onProgress(transferred, speed)
            lastTime = now; lastBytes = transferred
        }
    }
}
