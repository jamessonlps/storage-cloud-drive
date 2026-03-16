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
import com.clouddrive.transfer.TransferManager
import com.clouddrive.transfer.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TransferService : Service() {

    companion object {
        const val CHANNEL_ID = "transfer_channel"

        fun ensureRunning(context: Context) {
            val intent = Intent(context, TransferService::class.java)
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationManager: NotificationManager
    private val activeNotificationIds = mutableSetOf<Int>()

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        observeTransfers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildSummaryNotification(0))
        return START_NOT_STICKY
    }

    private fun observeTransfers() {
        serviceScope.launch {
            TransferManager.transfers.collect { items ->
                val active = items.filter {
                    it.state in setOf(
                        TransferState.QUEUED,
                        TransferState.UPLOADING,
                        TransferState.DOWNLOADING,
                        TransferState.RETRYING,
                    )
                }

                if (active.isEmpty() && items.any { it.state == TransferState.COMPLETED || it.state == TransferState.FAILED }) {
                    // All done, clean up
                    activeNotificationIds.forEach { notificationManager.cancel(it) }
                    activeNotificationIds.clear()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }

                if (active.isEmpty()) {
                    // Nothing to show
                    return@collect
                }

                // Update summary notification
                notificationManager.notify(1, buildSummaryNotification(active.size))

                // Update individual progress notifications
                val currentIds = mutableSetOf<Int>()
                active.forEach { item ->
                    val notifId = item.id.hashCode().let { if (it == 1) 2 else it } // avoid collision with summary (id=1)
                    currentIds.add(notifId)
                    notificationManager.notify(notifId, buildProgressNotification(item))
                }

                // Remove notifications for items no longer active
                (activeNotificationIds - currentIds).forEach { notificationManager.cancel(it) }
                activeNotificationIds.clear()
                activeNotificationIds.addAll(currentIds)
            }
        }
    }

    private fun buildSummaryNotification(activeCount: Int): Notification {
        val text = when (activeCount) {
            0 -> "Iniciando..."
            1 -> "1 transferencia em andamento"
            else -> "$activeCount transferencias em andamento"
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
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

    private fun buildProgressNotification(item: com.clouddrive.transfer.TransferItem): Notification {
        val title = when (item.state) {
            TransferState.UPLOADING -> "Enviando"
            TransferState.DOWNLOADING -> "Baixando"
            TransferState.RETRYING -> "Retentando (${item.retryCount}/${item.maxRetries})"
            TransferState.QUEUED -> "Na fila"
            else -> "Transferindo"
        }

        val progress = (item.progress * 100).toInt()
        val isIndeterminate = item.state == TransferState.QUEUED || item.totalBytes == 0L

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (item.type == com.clouddrive.transfer.TransferType.UPLOAD)
                    android.R.drawable.stat_sys_upload
                else
                    android.R.drawable.stat_sys_download,
            )
            .setContentTitle(title)
            .setContentText(item.fileName)
            .setOngoing(true)

        if (isIndeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progress, false)
            builder.setSubText("$progress%")
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transferencias",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notificacoes de upload e download de arquivos"
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

const val ACTION_TRANSFER_COMPLETE = "com.clouddrive.TRANSFER_COMPLETE"
