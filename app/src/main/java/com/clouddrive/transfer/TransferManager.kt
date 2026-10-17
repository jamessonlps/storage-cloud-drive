package com.clouddrive.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.clouddrive.crypto.ChunkedEncryption
import com.clouddrive.crypto.EncryptionManager
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3Repository
import com.clouddrive.s3.SettingsManager
import com.clouddrive.service.ACTION_TRANSFER_COMPLETE
import com.clouddrive.service.TransferService
import com.clouddrive.util.CacheCleaner
import com.clouddrive.util.DownloadsStore
import com.clouddrive.util.FileNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

object TransferManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transferSemaphore = Semaphore(3)
    private val deleteSemaphore = Semaphore(15)
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

    // --- Single item enqueue (backwards compatible) ---

    fun enqueue(
        item: TransferItem,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        _transfers.update { it + item }
        persistUri(item, context)
        TransferService.ensureRunning(context)
        launchItem(item, config, context, settingsManager, profileName)
    }

    // --- Batch operations ---

    fun enqueueBatch(
        items: List<TransferItem>,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        _transfers.update { it + items }
        items.forEach { persistUri(it, context) }
        TransferService.ensureRunning(context)
        items.forEach { launchItem(it, config, context, settingsManager, profileName) }
    }

    fun pauseBatch(batchId: String) {
        // Update state FIRST to prevent race with CancellationException handler
        val activeIds = _transfers.value
            .filter { it.batchId == batchId && it.isActive }
            .map { it.id }
            .toSet()
        _transfers.update { list -> list.map {
            if (it.id in activeIds) {
                it.copy(state = TransferState.PAUSED)
            } else it
        } }
        // Then cancel jobs - CancellationException handler will see PAUSED state
        for (id in activeIds) {
            jobs[id]?.cancel()
            jobs.remove(id)
        }
    }

    fun resumeBatch(
        batchId: String,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        _transfers.update { list -> list.map {
            if (it.batchId == batchId && it.state == TransferState.PAUSED) {
                it.copy(state = TransferState.QUEUED, transferredBytes = 0, completedParts = 0)
            } else it
        } }
        TransferService.ensureRunning(context)
        val resumedItems = _transfers.value.filter { it.batchId == batchId && it.state == TransferState.QUEUED }
        resumedItems.forEach { launchItem(it, config, context, settingsManager, profileName) }
    }

    fun cancelBatch(batchId: String) {
        val batchItems = _transfers.value.filter { it.batchId == batchId }
        for (item in batchItems) {
            if (item.isActive || item.state == TransferState.PAUSED) {
                jobs[item.id]?.cancel()
                jobs.remove(item.id)
            }
        }
        _transfers.update { list -> list.map {
            if (it.batchId == batchId && (it.isActive || it.state == TransferState.PAUSED)) {
                it.copy(state = TransferState.CANCELLED)
            } else it
        } }
    }

    fun retryFailedInBatch(
        batchId: String,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        _transfers.update { list -> list.map {
            if (it.batchId == batchId && it.state == TransferState.FAILED) {
                it.copy(
                    state = TransferState.QUEUED,
                    transferredBytes = 0,
                    completedParts = 0,
                    errorMessage = null,
                    retryCount = 0,
                )
            } else it
        } }
        TransferService.ensureRunning(context)
        val retryItems = _transfers.value.filter { it.batchId == batchId && it.state == TransferState.QUEUED }
        retryItems.forEach { launchItem(it, config, context, settingsManager, profileName) }
    }

    fun removeBatch(batchId: String) {
        val batchItems = _transfers.value.filter { it.batchId == batchId }
        for (item in batchItems) {
            jobs[item.id]?.cancel()
            jobs.remove(item.id)
        }
        _transfers.update { list -> list.filter { it.batchId != batchId } }
    }

    // --- Existing single-item operations ---

    fun cancel(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        updateState(id, TransferState.CANCELLED)
    }

    fun retry(
        id: String,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        val item = _transfers.value.find { it.id == id } ?: return
        val retryItem = item.copy(
            state = TransferState.QUEUED,
            transferredBytes = 0,
            completedParts = 0,
            errorMessage = null,
            retryCount = 0,
        )
        _transfers.update { list -> list.map { if (it.id == id) retryItem else it } }
        TransferService.ensureRunning(context)
        // Without the settings the upload would silently go out unencrypted, so they must be
        // passed along on every retry path.
        launchItem(retryItem, config, context, settingsManager, profileName)
    }

    fun remove(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        _transfers.update { list -> list.filter { it.id != id } }
    }

    fun clearCompleted() {
        _transfers.update { list -> list.filter {
            it.state != TransferState.COMPLETED && it.state != TransferState.CANCELLED
        } }
    }

    fun cancelGallerySyncTransfers() {
        val gallerySyncItems = _transfers.value.filter { it.source == TransferSource.GALLERY_SYNC }
        for (item in gallerySyncItems) {
            jobs[item.id]?.cancel()
            jobs.remove(item.id)
        }
        _transfers.update { list -> list.filter { it.source != TransferSource.GALLERY_SYNC } }
    }

    // --- Helpers ---

    private fun persistUri(item: TransferItem, context: Context) {
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
    }

    private fun launchItem(
        item: TransferItem,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        val sem = if (item.type == TransferType.DELETE) deleteSemaphore else transferSemaphore
        // LAZY: the job must be in `jobs` before it can run and remove itself in its finally block,
        // otherwise a fast transfer left a finished job in the map forever.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            sem.acquire()
            try {
                // Check if item was paused/cancelled while waiting for semaphore
                val current = _transfers.value.find { it.id == item.id }
                if (current == null || current.state == TransferState.PAUSED || current.state == TransferState.CANCELLED) {
                    return@launch
                }
                val effectiveConfig = if (item.bucketName.isNotEmpty()) {
                    config.copy(bucketName = item.bucketName)
                } else config
                when (item.type) {
                    TransferType.UPLOAD -> executeUpload(item, effectiveConfig, context, settingsManager, profileName)
                    TransferType.DOWNLOAD -> executeDownload(item, effectiveConfig, context, settingsManager, profileName)
                    TransferType.DELETE -> executeDelete(item, effectiveConfig)
                }
            } catch (e: CancellationException) {
                val current = _transfers.value.find { it.id == item.id }
                if (current != null && current.state != TransferState.PAUSED) {
                    updateState(item.id, TransferState.CANCELLED)
                }
            } finally {
                sem.release()
                jobs.remove(item.id)
                // Restricted to this app: the receiver is not exported.
                context.sendBroadcast(Intent(ACTION_TRANSFER_COMPLETE).setPackage(context.packageName))
            }
        }
        jobs[item.id] = job
        job.start()
    }

    private fun updateState(id: String, state: TransferState, error: String? = null) {
        _transfers.update { list -> list.map {
            if (it.id == id) {
                it.copy(
                    state = state,
                    errorMessage = error,
                    completedAt = if (state == TransferState.COMPLETED) System.currentTimeMillis() else it.completedAt,
                )
            } else it
        } }
    }

    private fun updateProgress(id: String, transferredBytes: Long) {
        _transfers.update { list -> list.map {
            if (it.id == id) it.copy(transferredBytes = transferredBytes) else it
        } }
    }

    private fun updateMultipartProgress(id: String, completedParts: Int, transferredBytes: Long) {
        _transfers.update { list -> list.map {
            if (it.id == id) it.copy(
                completedParts = completedParts,
                transferredBytes = transferredBytes,
            ) else it
        } }
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

        val encryptionEnabled = settingsManager != null && profileName != null &&
            EncryptionManager.isEnabled(settingsManager, profileName)

        // Only created when the source cannot tell its size; reused by retries, always removed.
        var spool: File? = null
        try {
            while (true) {
                try {
                    updateState(item.id, TransferState.UPLOADING)

                    val uri = Uri.parse(item.sourceUri)
                    var plainSize = item.totalBytes
                    if (plainSize <= 0L) {
                        // Some providers report 0/unknown. The size decides single vs multipart and
                        // the number of parts, so find the real one instead of reading it all in RAM.
                        plainSize = spool?.length() ?: querySize(context, uri)
                        if (plainSize <= 0L) {
                            val copy = spool ?: spoolToCache(context, uri, item.id).also { spool = it }
                            plainSize = copy.length()
                        }
                        updateTotalBytes(item.id, plainSize)
                    }

                    val rawStream = spool?.inputStream()
                        ?: context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Nao foi possivel ler o arquivo")

                    rawStream.use { stream ->
                        val body: InputStream
                        val size: Long
                        val metadata: Map<String, String>?
                        if (encryptionEnabled) {
                            val key = EncryptionManager.getOrCreateKey(settingsManager!!, profileName!!)
                            // Encrypted on the fly, chunk by chunk: memory use does not depend on the file size.
                            body = ChunkedEncryption.encryptingStream(stream, key)
                            size = ChunkedEncryption.encryptedSize(plainSize)
                            metadata = mapOf(
                                "encrypted" to "true",
                                ChunkedEncryption.METADATA_KEY to ChunkedEncryption.METADATA_VALUE,
                            )
                        } else {
                            body = stream
                            size = plainSize
                            metadata = null
                        }

                        if (size > MULTIPART_THRESHOLD) {
                            val totalParts = ((size + PART_SIZE - 1) / PART_SIZE).toInt()
                            _transfers.update { list -> list.map {
                                if (it.id == item.id) it.copy(totalParts = totalParts) else it
                            } }
                            repository.uploadMultipart(
                                key = item.s3Key,
                                inputStream = body,
                                contentType = item.contentType,
                                totalSize = size,
                                partSizeBytes = PART_SIZE,
                                metadata = metadata,
                            ) { _, completedPartsCount, bytesTransferred ->
                                updateMultipartProgress(item.id, completedPartsCount, bytesTransferred)
                            }
                        } else {
                            repository.uploadFileWithProgress(
                                key = item.s3Key,
                                inputStream = body,
                                contentType = item.contentType,
                                totalSize = size,
                                metadata = metadata,
                            ) { bytesTransferred ->
                                updateProgress(item.id, bytesTransferred)
                            }
                        }
                    }

                    updateProgress(item.id, _transfers.value.find { it.id == item.id }?.totalBytes ?: plainSize)
                    updateState(item.id, TransferState.COMPLETED)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (RetryPolicy.shouldRetry(e, currentRetry)) {
                        currentRetry++
                        _transfers.update { list -> list.map {
                            if (it.id == item.id) it.copy(
                                retryCount = currentRetry,
                                state = TransferState.RETRYING,
                                errorMessage = "Tentativa $currentRetry de ${item.maxRetries}",
                                transferredBytes = 0,
                                completedParts = 0,
                            ) else it
                        } }
                        delay(RetryPolicy.delayMs(currentRetry))
                    } else {
                        updateState(item.id, TransferState.FAILED, e.message ?: "Erro desconhecido")
                        return
                    }
                }
            }
        } finally {
            spool?.delete()
        }
    }

    private fun updateTotalBytes(id: String, totalBytes: Long) {
        _transfers.update { list -> list.map {
            if (it.id == id) it.copy(totalBytes = totalBytes) else it
        } }
    }

    /** Length reported by the provider's file descriptor, or -1 when it does not know. */
    private fun querySize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                if (fd.length >= 0) fd.length else -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    /** Copies the source to a temporary file so its real length is known (streamed, not buffered). */
    private fun spoolToCache(context: Context, uri: Uri, id: String): File {
        val dir = File(context.cacheDir, CacheCleaner.UPLOAD_SPOOL_DIR).also { it.mkdirs() }
        val file = File(dir, "$id.spool")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("Nao foi possivel ler o arquivo")
            input.use { src -> file.outputStream().use { out -> src.copyTo(out) } }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return file
    }

    private suspend fun executeDownload(
        item: TransferItem,
        config: S3Config,
        context: Context,
        settingsManager: SettingsManager? = null,
        profileName: String? = null,
    ) {
        val repository = S3Repository(
            config,
            if (settingsManager != null) EncryptionManager.keyProviderFor(settingsManager, profileName) else null,
        )
        var currentRetry = 0

        while (true) {
            try {
                updateState(item.id, TransferState.DOWNLOADING)

                // Staged in app-private storage, then published to Downloads (MediaStore on
                // Android 10+). The name comes from an S3 key: sanitized, never used as a path.
                val displayName = FileNames.sanitize(item.fileName)
                    ?: throw IllegalArgumentException("Nome de arquivo invalido")
                val stagingDir = File(context.cacheDir, CacheCleaner.DOWNLOAD_DIR).also { it.mkdirs() }
                // Unique per transfer: two keys with the same file name can download in parallel.
                val partial = File(stagingDir, "${item.id}${CacheCleaner.PARTIAL_SUFFIX}")

                try {
                    // Decrypted by the repository when the object was uploaded encrypted. A missing
                    // key or a bad tag (wrong key / tampered data) throws and fails the transfer
                    // instead of silently delivering ciphertext as if it were the file.
                    repository.downloadFile(item.s3Key, partial)
                    DownloadsStore.publish(context, partial, displayName)
                } finally {
                    partial.delete()
                }

                updateProgress(item.id, item.totalBytes)
                updateState(item.id, TransferState.COMPLETED)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (RetryPolicy.shouldRetry(e, currentRetry)) {
                    currentRetry++
                    _transfers.update { list -> list.map {
                        if (it.id == item.id) it.copy(
                            retryCount = currentRetry,
                            state = TransferState.RETRYING,
                            errorMessage = "Tentativa $currentRetry de ${item.maxRetries}",
                        ) else it
                    } }
                    delay(RetryPolicy.delayMs(currentRetry))
                } else {
                    updateState(item.id, TransferState.FAILED, e.message ?: "Erro desconhecido")
                    return
                }
            }
        }
    }

    private suspend fun executeDelete(item: TransferItem, config: S3Config) {
        val repository = S3Repository(config)
        var currentRetry = 0

        while (true) {
            try {
                updateState(item.id, TransferState.DELETING)
                repository.deleteFile(item.s3Key)
                updateState(item.id, TransferState.COMPLETED)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (RetryPolicy.shouldRetry(e, currentRetry)) {
                    currentRetry++
                    _transfers.update { list -> list.map {
                        if (it.id == item.id) it.copy(
                            retryCount = currentRetry,
                            state = TransferState.RETRYING,
                            errorMessage = "Tentativa $currentRetry de ${item.maxRetries}",
                        ) else it
                    } }
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
