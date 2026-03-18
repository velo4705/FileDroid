package com.filedroid.transfer

import java.util.UUID

enum class TransferDirection { UPLOAD, DOWNLOAD }

enum class TransferStatus { QUEUED, IN_PROGRESS, DONE, FAILED, CANCELLED }

data class TransferJob(
    val id: String = UUID.randomUUID().toString(),
    val direction: TransferDirection,
    val fileName: String,
    val localPath: String,
    val remotePath: String,
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val errorMessage: String? = null,
    val speedBytesPerSec: Long = 0L
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) transferredBytes.toFloat() / totalBytes else 0f

    val progressPercent: Int get() = (progressFraction * 100).toInt()

    val etaSeconds: Long
        get() = if (speedBytesPerSec > 0 && totalBytes > 0)
            (totalBytes - transferredBytes) / speedBytesPerSec else -1L
}
