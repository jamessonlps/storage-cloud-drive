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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clouddrive.s3.S3Config
import com.clouddrive.transfer.TransferItem
import com.clouddrive.transfer.TransferManager
import com.clouddrive.transfer.TransferState
import com.clouddrive.transfer.TransferType

@Composable
fun TransferQueueScreen(
    config: S3Config?,
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
                    "Nenhuma transferencia",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val active = transfers.filter {
        it.state in setOf(TransferState.QUEUED, TransferState.UPLOADING, TransferState.DOWNLOADING, TransferState.RETRYING)
    }
    val completed = transfers.filter { it.state == TransferState.COMPLETED }
    val failed = transfers.filter { it.state in setOf(TransferState.FAILED, TransferState.CANCELLED) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (active.isNotEmpty()) {
            item {
                SectionHeader("Em andamento (${active.size})")
            }
            items(active, key = { it.id }) { item ->
                TransferItemCard(
                    item = item,
                    onCancel = { TransferManager.cancel(item.id) },
                    onRetry = null,
                    onRemove = null,
                )
            }
        }

        if (completed.isNotEmpty()) {
            item {
                SectionHeader("Concluidos (${completed.size})")
            }
            items(completed, key = { it.id }) { item ->
                TransferItemCard(
                    item = item,
                    onCancel = null,
                    onRetry = null,
                    onRemove = { TransferManager.remove(item.id) },
                )
            }
        }

        if (failed.isNotEmpty()) {
            item {
                SectionHeader("Falhas (${failed.size})")
            }
            items(failed, key = { it.id }) { item ->
                TransferItemCard(
                    item = item,
                    onCancel = null,
                    onRetry = if (item.state == TransferState.FAILED && config != null) {
                        { TransferManager.retry(item.id, config, context) }
                    } else null,
                    onRemove = { TransferManager.remove(item.id) },
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
private fun TransferItemCard(
    item: TransferItem,
    onCancel: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    val statusColor = when (item.state) {
        TransferState.COMPLETED -> Color(0xFF43A047)
        TransferState.FAILED -> MaterialTheme.colorScheme.error
        TransferState.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        TransferState.RETRYING -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // File type icon
                Icon(
                    imageVector = getTransferIcon(item),
                    contentDescription = null,
                    tint = getTransferIconColor(item),
                    modifier = Modifier.size(32.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                // File name and status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = getStatusText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                }

                // Action buttons
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
                if (onRetry != null) {
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Tentar novamente",
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

            // Progress bar for active transfers
            if (item.state in setOf(TransferState.UPLOADING, TransferState.DOWNLOADING, TransferState.RETRYING)) {
                Spacer(modifier = Modifier.height(8.dp))
                if (item.totalBytes > 0) {
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
            }

            // Queued state - indeterminate progress
            if (item.state == TransferState.QUEUED) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // Error message
            if (item.state == TransferState.FAILED && item.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun getStatusText(item: TransferItem): String {
    val typeLabel = if (item.type == TransferType.UPLOAD) "Upload" else "Download"
    return when (item.state) {
        TransferState.QUEUED -> "$typeLabel na fila..."
        TransferState.UPLOADING, TransferState.DOWNLOADING -> {
            val percent = (item.progress * 100).toInt()
            if (item.isMultipart) {
                "$typeLabel: $percent% (parte ${item.completedParts}/${item.totalParts})"
            } else {
                "$typeLabel: $percent%"
            }
        }
        TransferState.COMPLETED -> "$typeLabel concluido"
        TransferState.FAILED -> "$typeLabel falhou"
        TransferState.CANCELLED -> "$typeLabel cancelado"
        TransferState.RETRYING -> "Retentando (${item.retryCount}/${item.maxRetries})..."
    }
}

private fun getTransferIcon(item: TransferItem): ImageVector {
    if (item.state == TransferState.COMPLETED) return Icons.Filled.CheckCircle
    if (item.state == TransferState.FAILED) return Icons.Filled.Error

    val ext = item.fileName.substringAfterLast('.', "").lowercase()
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
        else -> {
            if (item.type == TransferType.UPLOAD) Icons.Filled.CloudUpload
            else Icons.Filled.CloudDownload
        }
    }
}

private fun getTransferIconColor(item: TransferItem): Color {
    if (item.state == TransferState.COMPLETED) return Color(0xFF43A047)
    if (item.state == TransferState.FAILED) return Color(0xFFE53935)

    val ext = item.fileName.substringAfterLast('.', "").lowercase()
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
