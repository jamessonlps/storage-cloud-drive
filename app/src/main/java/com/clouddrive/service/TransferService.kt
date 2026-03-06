package com.clouddrive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import com.clouddrive.MainActivity
import com.clouddrive.R
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class TransferService : Service() {

    companion object {
        const val CHANNEL_ID = "transfer_channel"
        const val ACTION_UPLOAD = "com.clouddrive.action.UPLOAD"
        const val ACTION_DOWNLOAD = "com.clouddrive.action.DOWNLOAD"
        const val EXTRA_FILE_URI = "extra_file_uri"
        const val EXTRA_FILE_KEY = "extra_file_key"
        const val EXTRA_PREFIX = "extra_prefix"
        const val EXTRA_ACCESS_KEY = "extra_access_key"
        const val EXTRA_SECRET_KEY = "extra_secret_key"
        const val EXTRA_REGION = "extra_region"
        const val EXTRA_BUCKET = "extra_bucket"
        const val EXTRA_FILE_NAME = "extra_file_name"

        private val nextNotificationId = AtomicInteger(1000)

        fun uploadIntent(
            context: Context,
            uri: Uri,
            prefix: String,
            fileName: String,
            config: S3Config,
        ): Intent {
            return Intent(context, TransferService::class.java).apply {
                action = ACTION_UPLOAD
                putExtra(EXTRA_FILE_URI, uri.toString())
                putExtra(EXTRA_PREFIX, prefix)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_ACCESS_KEY, config.accessKeyId)
                putExtra(EXTRA_SECRET_KEY, config.secretAccessKey)
                putExtra(EXTRA_REGION, config.region)
                putExtra(EXTRA_BUCKET, config.bucketName)
            }
        }

        fun downloadIntent(
            context: Context,
            fileKey: String,
            fileName: String,
            config: S3Config,
        ): Intent {
            return Intent(context, TransferService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_FILE_KEY, fileKey)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_ACCESS_KEY, config.accessKeyId)
                putExtra(EXTRA_SECRET_KEY, config.secretAccessKey)
                putExtra(EXTRA_REGION, config.region)
                putExtra(EXTRA_BUCKET, config.bucketName)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeTasks = AtomicInteger(0)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelfIfIdle()
            return START_NOT_STICKY
        }

        val config = S3Config(
            accessKeyId = intent.getStringExtra(EXTRA_ACCESS_KEY) ?: return stopAndReturn(),
            secretAccessKey = intent.getStringExtra(EXTRA_SECRET_KEY) ?: return stopAndReturn(),
            region = intent.getStringExtra(EXTRA_REGION) ?: return stopAndReturn(),
            bucketName = intent.getStringExtra(EXTRA_BUCKET) ?: return stopAndReturn(),
        )

        activeTasks.incrementAndGet()

        // Ensure foreground with a summary notification
        startForeground(1, buildSummaryNotification())

        when (intent.action) {
            ACTION_UPLOAD -> handleUpload(intent, config)
            ACTION_DOWNLOAD -> handleDownload(intent, config)
            else -> {
                activeTasks.decrementAndGet()
                stopSelfIfIdle()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleUpload(intent: Intent, config: S3Config) {
        val uriString = intent.getStringExtra(EXTRA_FILE_URI) ?: return taskDone()
        val uri = Uri.parse(uriString)
        val prefix = intent.getStringExtra(EXTRA_PREFIX) ?: ""
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "arquivo_${System.currentTimeMillis()}"
        val notifId = nextNotificationId.getAndIncrement()

        val repository = S3Repository(config)

        showProgressNotification(notifId, "Enviando", fileName)

        serviceScope.launch {
            try {
                val key = prefix + fileName
                val contentType = contentResolver.getType(uri)
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Nao foi possivel ler o arquivo")

                inputStream.use { stream ->
                    repository.uploadFile(key, stream, contentType)
                }

                showCompleteNotification(notifId, "Upload concluido", fileName)
                sendBroadcastUpdate()
            } catch (e: Exception) {
                showErrorNotification(notifId, "Erro no upload", "${fileName}: ${e.message}")
            } finally {
                taskDone()
            }
        }
    }

    private fun handleDownload(intent: Intent, config: S3Config) {
        val fileKey = intent.getStringExtra(EXTRA_FILE_KEY) ?: return taskDone()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: fileKey.substringAfterLast('/')
        val notifId = nextNotificationId.getAndIncrement()

        val repository = S3Repository(config)

        showProgressNotification(notifId, "Baixando", fileName)

        serviceScope.launch {
            try {
                val dest = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "CloudDriveS3/$fileName"
                )
                repository.downloadFile(fileKey, dest)

                showCompleteNotification(notifId, "Download concluido", dest.absolutePath)
                sendBroadcastUpdate()
            } catch (e: Exception) {
                showErrorNotification(notifId, "Erro no download", "${fileName}: ${e.message}")
            } finally {
                taskDone()
            }
        }
    }

    private fun taskDone() {
        val remaining = activeTasks.decrementAndGet()
        if (remaining <= 0) {
            stopSelfIfIdle()
        } else {
            notificationManager.notify(1, buildSummaryNotification())
        }
    }

    private fun stopSelfIfIdle() {
        if (activeTasks.get() <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopAndReturn(): Int {
        stopSelfIfIdle()
        return START_NOT_STICKY
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

    private fun buildSummaryNotification(): Notification {
        val count = activeTasks.get()
        val text = if (count == 1) "1 transferencia em andamento"
        else "$count transferencias em andamento"

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

    private fun showProgressNotification(id: Int, title: String, fileName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(fileName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        notificationManager.notify(id, notification)
    }

    private fun showCompleteNotification(id: Int, title: String, detail: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(detail)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }

    private fun showErrorNotification(id: Int, title: String, detail: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(detail)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }

    private fun sendBroadcastUpdate() {
        val intent = Intent(ACTION_TRANSFER_COMPLETE)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

const val ACTION_TRANSFER_COMPLETE = "com.clouddrive.TRANSFER_COMPLETE"
