package com.clouddrive.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.clouddrive.crypto.EncryptionManager
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3Repository
import com.clouddrive.s3.SettingsManager
import com.clouddrive.service.ACTION_TRANSFER_COMPLETE
import com.clouddrive.service.TransferService
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object TransferManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(3)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfers: StateFlow<List<TransferItem>> = _transfers.asStateFlow()

    val activeCount: StateFlow<Int> = MutableStateFlow(0).also { flow ->
        scope.launch {
            _transfers.collect { list ->
                (flow as MutableStateFlow).value = list.count { it.isActive }
            }
        }
    }

    fun enqueue(
        item: TransferItem,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        _transfers.value = _transfers.value + item

        // Persist URI permission for retry support
        if (item.sourceUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    Uri.parse(item.sourceUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // Some URIs don't support persistable permissions
            }
        }

        // Ensure foreground service is running
        TransferService.ensureRunning(context)

        val job = scope.launch {
            semaphore.acquire()
            try {
                when (item.type) {
                    TransferType.UPLOAD -> executeUpload(item, config, context, settingsManager, profileName)
                    TransferType.DOWNLOAD -> executeDownload(item, config, context, settingsManager, profileName)
                }
            } catch (e: CancellationException) {
                updateState(item.id, TransferState.CANCELLED)
            } finally {
                semaphore.release()
                jobs.remove(item.id)
                context.sendBroadcast(Intent(ACTION_TRANSFER_COMPLETE))
            }
        }
        jobs[item.id] = job
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        updateState(id, TransferState.CANCELLED)
    }

    fun retry(id: String, config: S3Config, context: Context) {
        val item = _transfers.value.find { it.id == id } ?: return
        val retryItem = item.copy(
            state = TransferState.QUEUED,
            transferredBytes = 0,
            completedParts = 0,
            errorMessage = null,
            retryCount = 0,
        )
        _transfers.value = _transfers.value.map { if (it.id == id) retryItem else it }

        TransferService.ensureRunning(context)

        val job = scope.launch {
            semaphore.acquire()
            try {
                when (retryItem.type) {
                    TransferType.UPLOAD -> executeUpload(retryItem, config, context)
                    TransferType.DOWNLOAD -> executeDownload(retryItem, config, context)
                }
            } catch (e: CancellationException) {
                updateState(id, TransferState.CANCELLED)
            } finally {
                semaphore.release()
                jobs.remove(id)
                context.sendBroadcast(Intent(ACTION_TRANSFER_COMPLETE))
            }
        }
        jobs[id] = job
    }

    fun remove(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        _transfers.value = _transfers.value.filter { it.id != id }
    }

    fun clearCompleted() {
        _transfers.value = _transfers.value.filter {
            it.state != TransferState.COMPLETED && it.state != TransferState.CANCELLED
        }
    }

    private fun updateState(id: String, state: TransferState, error: String? = null) {
        _transfers.value = _transfers.value.map {
            if (it.id == id) {
                it.copy(
                    state = state,
                    errorMessage = error,
                    completedAt = if (state == TransferState.COMPLETED) System.currentTimeMillis() else it.completedAt,
                )
            } else it
        }
    }

    private fun updateProgress(id: String, transferredBytes: Long) {
        _transfers.value = _transfers.value.map {
            if (it.id == id) it.copy(transferredBytes = transferredBytes) else it
        }
    }

    private fun updateMultipartProgress(id: String, completedParts: Int, transferredBytes: Long) {
        _transfers.value = _transfers.value.map {
            if (it.id == id) it.copy(
                completedParts = completedParts,
                transferredBytes = transferredBytes,
            ) else it
        }
    }

    private suspend fun executeUpload(
        item: TransferItem,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        val repository = S3Repository(config)
        var currentRetry = 0

        // Check encryption
        val encryptionEnabled = settingsManager != null && profileName != null &&
            EncryptionManager.isEnabled(settingsManager, profileName)

        while (true) {
            try {
                updateState(item.id, TransferState.UPLOADING)

                val uri = Uri.parse(item.sourceUri)
                val rawStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Nao foi possivel ler o arquivo")

                rawStream.use { stream ->
                    if (encryptionEnabled) {
                        val key = EncryptionManager.getOrCreateKey(settingsManager!!, profileName!!)
                        val plainBytes = stream.readBytes()
                        val encryptedBytes = EncryptionManager.encrypt(plainBytes, key)
                        val encryptedSize = encryptedBytes.size.toLong()
                        val metadata = mapOf("encrypted" to "true")
                        val encStream = ByteArrayInputStream(encryptedBytes)

                        if (encryptedSize > MULTIPART_THRESHOLD) {
                            val totalParts = ((encryptedSize + PART_SIZE - 1) / PART_SIZE).toInt()
                            _transfers.value = _transfers.value.map {
                                if (it.id == item.id) it.copy(totalParts = totalParts) else it
                            }
                            repository.uploadMultipart(
                                key = item.s3Key,
                                inputStream = encStream,
                                contentType = item.contentType,
                                totalSize = encryptedSize,
                                partSizeBytes = PART_SIZE,
                                metadata = metadata,
                            ) { _, completedPartsCount, bytesTransferred ->
                                updateMultipartProgress(item.id, completedPartsCount, bytesTransferred)
                            }
                        } else {
                            repository.uploadFileWithProgress(
                                key = item.s3Key,
                                inputStream = encStream,
                                contentType = item.contentType,
                                totalSize = encryptedSize,
                                metadata = metadata,
                            ) { bytesTransferred ->
                                updateProgress(item.id, bytesTransferred)
                            }
                        }
                    } else {
                        if (item.totalBytes > MULTIPART_THRESHOLD) {
                            val totalParts = ((item.totalBytes + PART_SIZE - 1) / PART_SIZE).toInt()
                            _transfers.value = _transfers.value.map {
                                if (it.id == item.id) it.copy(totalParts = totalParts) else it
                            }
                            repository.uploadMultipart(
                                key = item.s3Key,
                                inputStream = stream,
                                contentType = item.contentType,
                                totalSize = item.totalBytes,
                                partSizeBytes = PART_SIZE,
                            ) { _, completedPartsCount, bytesTransferred ->
                                updateMultipartProgress(item.id, completedPartsCount, bytesTransferred)
                            }
                        } else {
                            repository.uploadFileWithProgress(
                                key = item.s3Key,
                                inputStream = stream,
                                contentType = item.contentType,
                                totalSize = item.totalBytes,
                            ) { bytesTransferred ->
                                updateProgress(item.id, bytesTransferred)
                            }
                        }
                    }
                }

                updateProgress(item.id, item.totalBytes)
                updateState(item.id, TransferState.COMPLETED)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (RetryPolicy.shouldRetry(e, currentRetry)) {
                    currentRetry++
                    _transfers.value = _transfers.value.map {
                        if (it.id == item.id) it.copy(
                            retryCount = currentRetry,
                            state = TransferState.RETRYING,
                            errorMessage = "Tentativa $currentRetry de ${item.maxRetries}",
                            transferredBytes = 0,
                            completedParts = 0,
                        ) else it
                    }
                    delay(RetryPolicy.delayMs(currentRetry))
                } else {
                    updateState(item.id, TransferState.FAILED, e.message ?: "Erro desconhecido")
                    return
                }
            }
        }
    }

    private suspend fun executeDownload(
        item: TransferItem,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        val repository = S3Repository(config)
        var currentRetry = 0

        while (true) {
            try {
                updateState(item.id, TransferState.DOWNLOADING)

                val dest = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "CloudDriveS3/${item.fileName}",
                )
                repository.downloadFile(item.s3Key, dest)

                // Check if file was encrypted and decrypt
                if (settingsManager != null && profileName != null) {
                    try {
                        val metadata = repository.headObject(item.s3Key)
                        if (metadata["encrypted"] == "true") {
                            val key = EncryptionManager.getOrCreateKey(settingsManager, profileName)
                            val encryptedBytes = dest.readBytes()
                            val decryptedBytes = EncryptionManager.decrypt(encryptedBytes, key)
                            dest.writeBytes(decryptedBytes)
                        }
                    } catch (_: Exception) {
                        // If decryption fails, keep the file as-is
                    }
                }

                updateProgress(item.id, item.totalBytes)
                updateState(item.id, TransferState.COMPLETED)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (RetryPolicy.shouldRetry(e, currentRetry)) {
                    currentRetry++
                    _transfers.value = _transfers.value.map {
                        if (it.id == item.id) it.copy(
                            retryCount = currentRetry,
                            state = TransferState.RETRYING,
                            errorMessage = "Tentativa $currentRetry de ${item.maxRetries}",
                        ) else it
                    }
                    delay(RetryPolicy.delayMs(currentRetry))
                } else {
                    updateState(item.id, TransferState.FAILED, e.message ?: "Erro desconhecido")
                    return
                }
            }
        }
    }

    private const val MULTIPART_THRESHOLD = 5L * 1024 * 1024 // 5MB
    private const val PART_SIZE = 5L * 1024 * 1024 // 5MB
}
