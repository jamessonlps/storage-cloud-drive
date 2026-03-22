package com.clouddrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.SettingsManager
import com.clouddrive.transfer.TransferItem
import com.clouddrive.transfer.TransferManager
import com.clouddrive.transfer.TransferState
import com.clouddrive.transfer.TransferType

private enum class BatchState {
    ACTIVE, PAUSED, COMPLETED, HAS_FAILURES, CANCELLED
}

private data class BatchGroup(
    val batchId: String,
    val items: List<TransferItem>,
) {
    val total: Int get() = items.size
    val completedCount: Int get() = items.count { it.state == TransferState.COMPLETED }
    val failedCount: Int get() = items.count { it.state == TransferState.FAILED }
    val type: TransferType get() = items.first().type
    val isSingleFile: Boolean get() = total == 1

    val state: BatchState
        get() = when {
            items.any { it.isActive } -> BatchState.ACTIVE
            items.all { it.state in setOf(TransferState.PAUSED, TransferState.COMPLETED, TransferState.FAILED) }
                && items.any { it.state == TransferState.PAUSED } -> BatchState.PAUSED
            items.all { it.state == TransferState.COMPLETED } -> BatchState.COMPLETED
            items.all { it.state == TransferState.CANCELLED } -> BatchState.CANCELLED
            failedCount > 0 -> BatchState.HAS_FAILURES
            else -> BatchState.CANCELLED
        }

    val currentFile: TransferItem?
        get() = items.find { it.state in setOf(TransferState.UPLOADING, TransferState.DOWNLOADING, TransferState.DELETING) }
}

@Composable
fun TransferQueueScreen(
    config: S3Config?,
    settingsManager: SettingsManager? = null,
    profileName: String? = null,
    modifier: Modifier = Modifier,
) {
    val transfers by TransferManager.transfers.collectAsState()
    val context = LocalContext.current

    if (transfers.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.SwapVert,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Nenhuma transferência",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val batches = transfers.groupBy { it.batchId }
        .map { (batchId, items) -> BatchGroup(batchId, items) }
        .sortedByDescending { it.items.first().createdAt }

    val activeBatches = batches.filter { it.state == BatchState.ACTIVE || it.state == BatchState.PAUSED }
    val completedBatches = batches.filter { it.state == BatchState.COMPLETED }
    val failedBatches = batches.filter { it.state == BatchState.HAS_FAILURES || it.state == BatchState.CANCELLED }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (activeBatches.isNotEmpty()) {
            item { SectionHeader("Em andamento (${activeBatches.size})") }
            items(activeBatches, key = { it.batchId }) { batch ->
                BatchCard(
                    batch = batch,
                    onPause = { TransferManager.pauseBatch(batch.batchId) },
                    onResume = if (config != null) {
                        { TransferManager.resumeBatch(batch.batchId, config, context, settingsManager, profileName) }
                    } else null,
                    onCancel = { TransferManager.cancelBatch(batch.batchId) },
                    onRetryFailed = null,
                    onRemove = null,
                )
            }
        }

        if (completedBatches.isNotEmpty()) {
            item { SectionHeader("Concluídos (${completedBatches.size})") }
            items(completedBatches, key = { it.batchId }) { batch ->
                BatchCard(
                    batch = batch,
                    onPause = null,
                    onResume = null,
                    onCancel = null,
                    onRetryFailed = null,
                    onRemove = { TransferManager.removeBatch(batch.batchId) },
                )
            }
        }

        if (failedBatches.isNotEmpty()) {
            item { SectionHeader("Falhas (${failedBatches.size})") }
            items(failedBatches, key = { it.batchId }) { batch ->
                BatchCard(
                    batch = batch,
                    onPause = null,
                    onResume = null,
                    onCancel = null,
                    onRetryFailed = if (batch.state == BatchState.HAS_FAILURES && config != null) {
                        { TransferManager.retryFailedInBatch(batch.batchId, config, context, settingsManager, profileName) }
                    } else null,
                    onRemove = { TransferManager.removeBatch(batch.batchId) },
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun BatchCard(
    batch: BatchGroup,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onRetryFailed: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    val statusColor = when (batch.state) {
        BatchState.COMPLETED -> Color(0xFF43A047)
        BatchState.HAS_FAILURES -> MaterialTheme.colorScheme.error
        BatchState.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        BatchState.PAUSED -> Color(0xFFFFA000)
        BatchState.ACTIVE -> MaterialTheme.colorScheme.primary
    }

    val icon = getBatchIcon(batch)
    val iconColor = getBatchIconColor(batch)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = getBatchTitle(batch),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = getBatchSubtitle(batch),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                }

                // Actions
                if (batch.state == BatchState.ACTIVE && onPause != null) {
                    IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Pause,
                            contentDescription = "Pausar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (batch.state == BatchState.PAUSED && onResume != null) {
                    IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Retomar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (onCancel != null) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = "Cancelar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (onRetryFailed != null) {
                    IconButton(onClick = onRetryFailed, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Retentar falhas",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remover",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Progress bar
            if (batch.state == BatchState.ACTIVE || batch.state == BatchState.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))
                if (batch.isSingleFile) {
                    val item = batch.items.first()
                    if (item.totalBytes > 0 && item.state in setOf(TransferState.UPLOADING, TransferState.DOWNLOADING, TransferState.DELETING)) {
                        LinearProgressIndicator(
                            progress = item.progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                } else {
                    val progress = batch.completedCount.toFloat() / batch.total
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            // Current file being transferred (for multi-file batches)
            if (!batch.isSingleFile && batch.currentFile != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = batch.currentFile!!.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Error info for failed batches
            if (batch.state == BatchState.HAS_FAILURES && batch.failedCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val firstError = batch.items.find { it.state == TransferState.FAILED }?.errorMessage
                if (firstError != null) {
                    Text(
                        text = firstError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun getBatchTitle(batch: BatchGroup): String {
    val typeLabel = when (batch.type) {
        TransferType.UPLOAD -> "Upload"
        TransferType.DOWNLOAD -> "Download"
        TransferType.DELETE -> "Exclusão"
    }
    return if (batch.isSingleFile) {
        batch.items.first().fileName
    } else {
        "$typeLabel de ${batch.total} arquivos"
    }
}

private fun getBatchSubtitle(batch: BatchGroup): String {
    val typeLabel = when (batch.type) {
        TransferType.UPLOAD -> "Upload"
        TransferType.DOWNLOAD -> "Download"
        TransferType.DELETE -> "Exclusão"
    }
    return when (batch.state) {
        BatchState.ACTIVE -> {
            if (batch.isSingleFile) {
                val item = batch.items.first()
                when (item.state) {
                    TransferState.QUEUED -> "$typeLabel na fila..."
                    TransferState.UPLOADING, TransferState.DOWNLOADING -> {
                        val percent = (item.progress * 100).toInt()
                        "$typeLabel: $percent%"
                    }
                    TransferState.DELETING -> "Excluindo..."
                    TransferState.RETRYING -> "Retentando (${item.retryCount}/${item.maxRetries})..."
                    else -> "$typeLabel em andamento"
                }
            } else {
                "${batch.completedCount} de ${batch.total} concluídos"
            }
        }
        BatchState.PAUSED -> {
            if (batch.isSingleFile) "Pausado"
            else "Pausado - ${batch.completedCount} de ${batch.total} concluídos"
        }
        BatchState.COMPLETED -> {
            if (batch.isSingleFile) "$typeLabel concluído"
            else "${batch.total} de ${batch.total} concluídos"
        }
        BatchState.HAS_FAILURES -> {
            if (batch.isSingleFile) "$typeLabel falhou"
            else "${batch.completedCount} de ${batch.total} concluídos, ${batch.failedCount} falharam"
        }
        BatchState.CANCELLED -> "$typeLabel cancelado"
    }
}

private fun getBatchIcon(batch: BatchGroup): ImageVector {
    if (batch.state == BatchState.COMPLETED) return Icons.Filled.CheckCircle
    if (batch.state == BatchState.HAS_FAILURES) return Icons.Filled.Error

    if (batch.isSingleFile) {
        return getFileExtIcon(batch.items.first().fileName, batch.type)
    }
    return when (batch.type) {
        TransferType.UPLOAD -> Icons.Filled.CloudUpload
        TransferType.DOWNLOAD -> Icons.Filled.CloudDownload
        TransferType.DELETE -> Icons.Filled.Delete
    }
}

private fun getBatchIconColor(batch: BatchGroup): Color {
    if (batch.state == BatchState.COMPLETED) return Color(0xFF43A047)
    if (batch.state == BatchState.HAS_FAILURES) return Color(0xFFE53935)

    if (batch.isSingleFile) {
        return getFileExtColor(batch.items.first().fileName)
    }
    return if (batch.type == TransferType.DELETE) Color(0xFFE53935) else Color(0xFF1E88E5)
}

private fun getFileExtIcon(fileName: String, type: TransferType): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
        "ico", "tiff", "tif", "heic", "heif", "raw" -> Icons.Filled.Image
        "mp4", "avi", "mkv", "mov", "wmv", "flv",
        "webm", "m4v", "3gp", "mpeg", "mpg" -> Icons.Filled.VideoFile
        "mp3", "wav", "aac", "ogg", "flac", "m4a",
        "wma", "opus", "alac", "aiff" -> Icons.Filled.AudioFile
        "pdf" -> Icons.Filled.PictureAsPdf
        "xlsx", "xls", "csv", "tsv", "ods", "numbers" -> Icons.Filled.TableChart
        "doc", "docx", "odt", "rtf", "txt", "pages",
        "tex", "md" -> Icons.Filled.Description
        "ppt", "pptx", "odp", "key" -> Icons.Filled.Slideshow
        "json", "xml", "html", "css", "js", "ts", "py",
        "java", "kt", "c", "cpp", "h", "go", "rs", "rb",
        "php", "sh", "yaml", "yml", "toml", "sql",
        "swift", "dart" -> Icons.Filled.Code
        "zip", "rar", "7z", "tar", "gz", "bz2",
        "xz", "tgz", "zst" -> Icons.Filled.FolderZip
        "apk" -> Icons.Filled.Android
        else -> when (type) {
            TransferType.UPLOAD -> Icons.Filled.CloudUpload
            TransferType.DOWNLOAD -> Icons.Filled.CloudDownload
            TransferType.DELETE -> Icons.Filled.Delete
        }
    }
}

private fun getFileExtColor(fileName: String): Color {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
        "ico", "tiff", "tif", "heic", "heif", "raw" -> Color(0xFF7E57C2)
        "mp4", "avi", "mkv", "mov", "wmv", "flv",
        "webm", "m4v", "3gp", "mpeg", "mpg" -> Color(0xFFEC407A)
        "mp3", "wav", "aac", "ogg", "flac", "m4a",
        "wma", "opus", "alac", "aiff" -> Color(0xFF00ACC1)
        "pdf" -> Color(0xFFE53935)
        "xlsx", "xls", "csv", "tsv", "ods", "numbers" -> Color(0xFF43A047)
        "doc", "docx", "odt", "rtf", "txt", "pages",
        "tex", "md" -> Color(0xFF1E88E5)
        "ppt", "pptx", "odp", "key" -> Color(0xFFFB8C00)
        "json", "xml", "html", "css", "js", "ts", "py",
        "java", "kt", "c", "cpp", "h", "go", "rs", "rb",
        "php", "sh", "yaml", "yml", "toml", "sql",
        "swift", "dart" -> Color(0xFF546E7A)
        "zip", "rar", "7z", "tar", "gz", "bz2",
        "xz", "tgz", "zst" -> Color(0xFFFDD835)
        "apk" -> Color(0xFF66BB6A)
        else -> Color(0xFF9E9E9E)
    }
}
