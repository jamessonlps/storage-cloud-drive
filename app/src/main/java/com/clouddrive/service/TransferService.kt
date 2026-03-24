package com.clouddrive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clouddrive.MainActivity
import com.clouddrive.transfer.TransferItem
import com.clouddrive.transfer.TransferManager
import com.clouddrive.transfer.TransferState
import com.clouddrive.transfer.TransferType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TransferService : Service() {

    companion object {
        const val CHANNEL_ID = "transfer_channel"
        const val COMPLETE_CHANNEL_ID = "transfer_complete_channel"
        const val EXTRA_OPEN_TRANSFERS = "open_transfers"

        fun ensureRunning(context: Context) {
            val intent = Intent(context, TransferService::class.java)
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationManager: NotificationManager
    private val activeNotificationIds = mutableSetOf<Int>()
    private val completedBatches = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        observeTransfers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildSummaryNotification(0))
        return START_NOT_STICKY
    }

    private fun observeTransfers() {
        serviceScope.launch {
            TransferManager.transfers.collect { items ->
                if (items.isEmpty()) return@collect

                // Group by batchId
                val batches = items.groupBy { it.batchId }

                val activeBatchIds = mutableListOf<String>()
                val currentNotifIds = mutableSetOf<Int>()

                for ((batchId, batchItems) in batches) {
                    val hasActive = batchItems.any { it.isActive }
                    val hasPaused = batchItems.any { it.state == TransferState.PAUSED }

                    if (hasActive || hasPaused) {
                        activeBatchIds.add(batchId)
                    } else if (batchId !in completedBatches) {
                        // Batch just finished
                        completedBatches.add(batchId)
                        showCompletionNotification(batchItems)
                    }
                }

                if (activeBatchIds.isEmpty()) {
                    // All done
                    activeNotificationIds.forEach { notificationManager.cancel(it) }
                    activeNotificationIds.clear()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }

                if (activeBatchIds.size == 1) {
                    // Single batch: use batch notification as the foreground notification (ID=1)
                    // Cancel any separate batch notification if it existed before
                    val oldBatchNotifId = activeBatchIds.first().hashCode().let { if (it == 1) 2 else it }
                    notificationManager.cancel(oldBatchNotifId)
                    val batchItems = batches[activeBatchIds.first()]!!
                    notificationManager.notify(1, buildBatchNotification(batchItems))
                    // Remove stale notifications, keeping only ID=1
                    (activeNotificationIds - setOf(1)).forEach { notificationManager.cancel(it) }
                    activeNotificationIds.clear()
                    activeNotificationIds.add(1)
                } else {
                    // Multiple batches: show summary + individual batch notifications
                    notificationManager.notify(1, buildSummaryNotification(activeBatchIds.size))
                    for (batchId in activeBatchIds) {
                        val notifId = batchId.hashCode().let { if (it == 1) 2 else it }
                        currentNotifIds.add(notifId)
                        notificationManager.notify(notifId, buildBatchNotification(batches[batchId]!!))
                    }
                    // Remove stale notifications
                    (activeNotificationIds - currentNotifIds - setOf(1)).forEach { notificationManager.cancel(it) }
                    activeNotificationIds.clear()
                    activeNotificationIds.add(1)
                    activeNotificationIds.addAll(currentNotifIds)
                }
            }
        }
    }

    private fun buildSummaryNotification(activeBatchCount: Int): Notification {
        val text = when (activeBatchCount) {
            0 -> "Iniciando..."
            1 -> "1 transferência em andamento"
            else -> "$activeBatchCount transferências em andamento"
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TRANSFERS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Cloud Drive S3")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun buildBatchNotification(batchItems: List<TransferItem>): Notification {
        val total = batchItems.size
        val completed = batchItems.count { it.state == TransferState.COMPLETED }
        val batchType = batchItems.first().type
        val typeLabel = when (batchType) {
            TransferType.UPLOAD -> "Enviando"
            TransferType.DOWNLOAD -> "Baixando"
            TransferType.DELETE -> "Excluindo"
        }
        val isPaused = batchItems.all { it.state in setOf(TransferState.PAUSED, TransferState.COMPLETED, TransferState.FAILED) }
            && batchItems.any { it.state == TransferState.PAUSED }

        val title = if (isPaused) {
            "Pausado"
        } else if (total == 1) {
            "$typeLabel ${batchItems.first().fileName}"
        } else {
            "$typeLabel $completed de $total arquivos"
        }

        val currentFile = batchItems.find { it.state in setOf(TransferState.UPLOADING, TransferState.DOWNLOADING, TransferState.DELETING) }
        val subtitle = currentFile?.fileName

        val icon = when (batchType) {
            TransferType.UPLOAD -> android.R.drawable.stat_sys_upload
            TransferType.DOWNLOAD -> android.R.drawable.stat_sys_download
            TransferType.DELETE -> android.R.drawable.ic_menu_delete
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TRANSFERS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        if (subtitle != null) {
            builder.setContentText(subtitle)
        }

        if (total > 1) {
            builder.setProgress(total, completed, false)
            builder.setSubText("$completed/$total")
        } else if (currentFile != null && currentFile.totalBytes > 0) {
            val percent = (currentFile.progress * 100).toInt()
            builder.setProgress(100, percent, false)
            builder.setSubText("$percent%")
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun showCompletionNotification(batchItems: List<TransferItem>) {
        val total = batchItems.size
        val succeeded = batchItems.count { it.state == TransferState.COMPLETED }
        val failed = batchItems.count { it.state == TransferState.FAILED }
        val batchType = batchItems.first().type

        val title: String
        val text: String

        if (failed == 0) {
            title = when (batchType) {
                TransferType.UPLOAD -> "Upload concluído"
                TransferType.DOWNLOAD -> "Download concluído"
                TransferType.DELETE -> "Exclusão concluída"
            }
            text = if (total == 1) {
                batchItems.first().fileName
            } else {
                when (batchType) {
                    TransferType.UPLOAD -> "$total arquivos enviados com sucesso"
                    TransferType.DOWNLOAD -> "$total arquivos baixados com sucesso"
                    TransferType.DELETE -> "$total arquivos excluídos com sucesso"
                }
            }
        } else {
            title = when (batchType) {
                TransferType.UPLOAD -> "Upload parcial"
                TransferType.DOWNLOAD -> "Download parcial"
                TransferType.DELETE -> "Exclusão parcial"
            }
            text = "$succeeded de $total concluídos ($failed falharam)"
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TRANSFERS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
            .setSmallIcon(
                if (failed == 0) android.R.drawable.stat_sys_upload_done
                else android.R.drawable.stat_notify_error,
            )
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notifId = (batchItems.first().batchId + "_complete").hashCode()
        notificationManager.notify(notifId, notif)
    }

    private fun createNotificationChannels() {
        val progressChannel = NotificationChannel(
            CHANNEL_ID,
            "Transferências",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notificações de progresso de upload e download"
        }
        notificationManager.createNotificationChannel(progressChannel)

        val completeChannel = NotificationChannel(
            COMPLETE_CHANNEL_ID,
            "Transferências concluídas",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notificações de conclusão de transferências"
        }
        notificationManager.createNotificationChannel(completeChannel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

const val ACTION_TRANSFER_COMPLETE = "com.clouddrive.TRANSFER_COMPLETE"
