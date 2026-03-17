package com.clouddrive.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clouddrive.CloudDriveApp
import com.clouddrive.s3.SettingsManager
import com.clouddrive.sync.db.SyncStatus
import com.clouddrive.sync.db.SyncedFileEntity
import com.clouddrive.transfer.TransferItem
import com.clouddrive.transfer.TransferManager
import com.clouddrive.transfer.TransferSource
import com.clouddrive.transfer.TransferType
import java.util.UUID

class GallerySyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_BUCKET_NAME = "bucket_name"
        private const val BATCH_SIZE = 20
    }

    override suspend fun doWork(): Result {
        val profileName = inputData.getString(KEY_PROFILE_NAME) ?: return Result.failure()
        val bucketName = inputData.getString(KEY_BUCKET_NAME) ?: return Result.failure()

        val app = applicationContext as? CloudDriveApp ?: return Result.failure()
        val settingsManager = SettingsManager(applicationContext)
        val db = app.syncDatabase
        val dao = db.syncedFileDao()

        if (!settingsManager.isGallerySyncEnabled(profileName)) {
            return Result.success()
        }

        val config = settingsManager.getProfileConfig(profileName) ?: return Result.failure()
        val s3Prefix = settingsManager.getGallerySyncPrefix(profileName)
        val enabledFolders = settingsManager.getGallerySyncFolders(profileName)

        if (enabledFolders.isEmpty()) return Result.success()

        // Phase 1: Scan for new files and insert as PENDING
        val newFiles = GalleryScanner.findNewFiles(
            context = applicationContext,
            profileName = profileName,
            bucket = bucketName,
            s3Prefix = s3Prefix,
            enabledFolders = enabledFolders,
            db = db,
        )

        if (newFiles.isNotEmpty()) {
            dao.insertAll(newFiles)
        }

        // Phase 2: Upload pending files in batches
        val effectiveConfig = config.copy(bucketName = bucketName)

        while (!isStopped) {
            val pendingBatch = dao.getPendingFiles(profileName, bucketName, BATCH_SIZE)
            if (pendingBatch.isEmpty()) break

            uploadBatch(pendingBatch, effectiveConfig, settingsManager, profileName, dao)
        }

        settingsManager.setGallerySyncLastRun(profileName, System.currentTimeMillis())

        return Result.success()
    }

    private suspend fun uploadBatch(
        files: List<SyncedFileEntity>,
        config: com.clouddrive.s3.S3Config,
        settingsManager: SettingsManager,
        profileName: String,
        dao: com.clouddrive.sync.db.SyncedFileDao,
    ) {
        val batchId = "gallery-sync-${UUID.randomUUID()}"

        // Mark as uploading in Room
        files.forEach { file ->
            dao.updateStatus(file.id, SyncStatus.UPLOADING)
        }

        val transferItems = files.map { file ->
            TransferItem(
                batchId = batchId,
                fileName = file.fileName,
                s3Key = file.s3Key,
                type = TransferType.UPLOAD,
                totalBytes = file.fileSize,
                sourceUri = file.mediaStoreUri,
                contentType = file.mimeType,
                bucketName = config.bucketName,
                source = TransferSource.GALLERY_SYNC,
            )
        }

        // Map transfer IDs to Room IDs for status tracking
        val transferToRoom = transferItems.zip(files).associate { (transfer, room) ->
            transfer.id to room.id
        }

        TransferManager.enqueueBatch(
            transferItems,
            config,
            applicationContext,
            settingsManager,
            profileName,
        )

        // Wait for this batch to complete by polling TransferManager state
        waitForBatchCompletion(batchId, transferToRoom, dao)
    }

    private suspend fun waitForBatchCompletion(
        batchId: String,
        transferToRoom: Map<String, Long>,
        dao: com.clouddrive.sync.db.SyncedFileDao,
    ) {
        val completed = mutableSetOf<String>()

        TransferManager.transfers.collect { transfers ->
            val batchTransfers = transfers.filter { it.batchId == batchId }

            for (transfer in batchTransfers) {
                if (transfer.id in completed) continue

                val roomId = transferToRoom[transfer.id] ?: continue

                when (transfer.state) {
                    com.clouddrive.transfer.TransferState.COMPLETED -> {
                        dao.updateStatus(
                            id = roomId,
                            status = SyncStatus.COMPLETED,
                            syncedAt = System.currentTimeMillis(),
                        )
                        completed.add(transfer.id)
                    }
                    com.clouddrive.transfer.TransferState.FAILED,
                    com.clouddrive.transfer.TransferState.CANCELLED -> {
                        dao.updateStatus(
                            id = roomId,
                            status = SyncStatus.FAILED,
                            error = transfer.errorMessage,
                            retryIncrement = 1,
                        )
                        completed.add(transfer.id)
                    }
                    else -> { /* still in progress */ }
                }
            }

            // All transfers in batch resolved
            if (completed.size >= transferToRoom.size) {
                return@collect
            }
        }
    }
}
