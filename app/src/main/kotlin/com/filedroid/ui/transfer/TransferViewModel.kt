package com.filedroid.ui.transfer

import androidx.lifecycle.ViewModel
import com.filedroid.remote.RemoteClient
import com.filedroid.transfer.TransferEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import com.filedroid.transfer.TransferJob
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val engine: TransferEngine
) : ViewModel() {

    val jobs: StateFlow<List<TransferJob>> = engine.jobs

    fun download(client: RemoteClient, remotePath: String, localPath: String, totalBytes: Long = 0L) {
        engine.enqueueDownload(client, remotePath, localPath, totalBytes)
    }

    fun upload(client: RemoteClient, localPath: String, remotePath: String) {
        engine.enqueueUpload(client, localPath, remotePath)
    }

    fun cancel(jobId: String) = engine.cancel(jobId)

    fun retry(jobId: String, client: RemoteClient) = engine.retry(jobId, client)

    fun clearCompleted() = engine.clearCompleted()
}
