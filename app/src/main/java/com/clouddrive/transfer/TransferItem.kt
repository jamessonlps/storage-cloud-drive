package com.clouddrive.transfer

import java.util.UUID

enum class TransferState {
    QUEUED, UPLOADING, DOWNLOADING, COMPLETED, FAILED, CANCELLED, RETRYING, PAUSED
}

enum class TransferType {
    UPLOAD, DOWNLOAD
}

data class TransferItem(
    val id: String = UUID.randomUUID().toString(),
    val batchId: String = UUID.randomUUID().toString(),
    val fileName: String,
    val s3Key: String,
    val type: TransferType,
    val state: TransferState = TransferState.QUEUED,
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val totalParts: Int = 0,
    val completedParts: Int = 0,
    val sourceUri: String? = null,
    val contentType: String? = null,
    val prefix: String = "",
    val bucketName: String = "",
) {
    val progress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val isMultipart: Boolean
        get() = totalParts > 0

    val isActive: Boolean
        get() = state in setOf(TransferState.UPLOADING, TransferState.DOWNLOADING, TransferState.RETRYING, TransferState.QUEUED)
}
