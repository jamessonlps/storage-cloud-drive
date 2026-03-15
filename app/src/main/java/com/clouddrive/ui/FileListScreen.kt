package com.clouddrive.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3FileItem
import com.clouddrive.s3.S3Repository
import com.clouddrive.service.ACTION_TRANSFER_COMPLETE
import com.clouddrive.service.TransferService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileListScreen(
    config: S3Config,
    currentPrefix: String,
    onNavigateToFolder: (String) -> Unit,
    refreshTrigger: Int,
    showNewFolderDialog: Boolean,
    onDismissNewFolderDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(config) { S3Repository(config) }

    var files by remember { mutableStateOf<List<S3FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<S3FileItem?>(null) }

    fun loadFiles() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                files = withContext(Dispatchers.IO) {
                    repository.listFiles(currentPrefix)
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Erro desconhecido"
            } finally {
                isLoading = false
            }
        }
    }

    // Listen for transfer completion broadcasts to refresh the file list
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                loadFiles()
            }
        }
        val filter = IntentFilter(ACTION_TRANSFER_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Reload when prefix changes or refresh is triggered
    LaunchedEffect(currentPrefix, refreshTrigger) {
        loadFiles()
    }

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val fileName = getFileName(context, uri) ?: "arquivo_${System.currentTimeMillis()}"
        val intent = TransferService.uploadIntent(context, uri, currentPrefix, fileName, config)
        context.startForegroundService(intent)
        Toast.makeText(context, "Upload iniciado: $fileName", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            errorMessage != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Erro: $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { loadFiles() }) {
                        Text("Tentar novamente")
                    }
                }
            }
            files.isEmpty() && currentPrefix.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Bucket vazio",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Toque no botao + para enviar arquivos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files, key = { it.key }) { file ->
                        FileItemCard(
                            file = file,
                            onFolderClick = { onNavigateToFolder(file.key) },
                            onDownload = {
                                val intent = TransferService.downloadIntent(
                                    context, file.key, file.fileName, config
                                )
                                context.startForegroundService(intent)
                                Toast.makeText(
                                    context,
                                    "Download iniciado: ${file.fileName}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onDelete = { showDeleteDialog = file },
                        )
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // FAB overlay
        FloatingActionButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = "Upload")
        }
    }

    // New folder dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismissNewFolderDialog,
            title = { Text("Nova Pasta") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Nome da pasta") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        repository.createFolder(currentPrefix + folderName.trim())
                                    }
                                    onDismissNewFolderDialog()
                                    loadFiles()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                ) {
                    Text("Criar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissNewFolderDialog) {
                    Text("Cancelar")
                }
            },
        )
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Excluir arquivo") },
            text = { Text("Deseja excluir \"${file.fileName}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    repository.deleteFile(file.key)
                                }
                                showDeleteDialog = null
                                loadFiles()
                                Toast.makeText(context, "Arquivo excluido", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun FileItemCard(
    file: S3FileItem,
    onFolderClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .then(if (file.isFolder) Modifier.clickable { onFolderClick() } else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = getFileIcon(file),
                contentDescription = null,
                tint = if (file.isFolder) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!file.isFolder) {
                    Text(
                        text = file.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!file.isFolder) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onDownload) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = "Baixar",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Excluir",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

private fun getFileIcon(file: S3FileItem): ImageVector {
    if (file.isFolder) return Icons.Filled.Folder
    val ext = file.fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Icons.Filled.Image
        "mp4", "avi", "mkv", "mov", "wmv", "flv" -> Icons.Filled.VideoFile
        "mp3", "wav", "aac", "ogg", "flac", "m4a" -> Icons.Filled.AudioFile
        else -> Icons.Filled.InsertDriveFile
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
    }
    return uri.lastPathSegment
}
