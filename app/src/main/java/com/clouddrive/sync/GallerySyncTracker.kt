package com.clouddrive.sync

import com.clouddrive.sync.db.SyncStatus
import com.clouddrive.sync.db.SyncedFileDao
import com.clouddrive.transfer.TransferManager
import com.clouddrive.transfer.TransferSource
import com.clouddrive.transfer.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-level observer that watches TransferManager for gallery sync transfer completions
 * and updates the Room database accordingly. This survives WorkManager worker cancellations,
 * ensuring Room stays in sync with actual transfer status.
 */
object GallerySyncTracker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trackedBatches = ConcurrentHashMap<String, BatchInfo>()
    private var observerJob: Job? = null

    data class BatchInfo(
        val transferToRoom: Map<String, Long>,
        val dao: SyncedFileDao,
        val completed: MutableSet<String> = ConcurrentHashMap.newKeySet(),
    )

    fun trackBatch(batchId: String, transferToRoom: Map<String, Long>, dao: SyncedFileDao) {
        trackedBatches[batchId] = BatchInfo(transferToRoom, dao)
        ensureObserving()
    }

    private fun ensureObserving() {
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            TransferManager.transfers.collect { transfers ->
                for ((batchId, info) in trackedBatches) {
                    val batchTransfers = transfers.filter { it.batchId == batchId }

                    for (transfer in batchTransfers) {
                        if (transfer.id in info.completed) continue
                        val roomId = info.transferToRoom[transfer.id] ?: continue

                        when (transfer.state) {
                            TransferState.COMPLETED -> {
                                try {
                                    info.dao.updateStatus(
                                        id = roomId,
                                        status = SyncStatus.COMPLETED,
                                        syncedAt = System.currentTimeMillis(),
                                    )
                                } catch (_: Exception) { }
                                info.completed.add(transfer.id)
                            }
                            TransferState.FAILED,
                            TransferState.CANCELLED -> {
                                try {
                                    info.dao.updateStatus(
                                        id = roomId,
                                        status = SyncStatus.FAILED,
                                        error = transfer.errorMessage,
                                        retryIncrement = 1,
                                    )
                                } catch (_: Exception) { }
                                info.completed.add(transfer.id)
                            }
                            else -> { /* still in progress */ }
                        }
                    }

                    // Clean up finished batches
                    if (info.completed.size >= info.transferToRoom.size) {
                        trackedBatches.remove(batchId)
                    }
                }

                // Stop observing if no more tracked batches
                if (trackedBatches.isEmpty()) {
                    observerJob?.cancel()
                    observerJob = null
                }
            }
        }
    }
}
