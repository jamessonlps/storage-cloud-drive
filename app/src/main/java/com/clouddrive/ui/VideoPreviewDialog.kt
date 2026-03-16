package com.clouddrive.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VideoPreviewDialog(
    videoKey: String,
    fileName: String,
    config: S3Config,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    val repository = remember(config) { S3Repository(config) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(videoKey) {
        isLoading = true
        error = null
        try {
            val bytes = withContext(Dispatchers.IO) {
                repository.downloadFileAsBytes(videoKey)
            }
            val file = File(context.cacheDir, "video_preview_${videoKey.hashCode()}.tmp")
            withContext(Dispatchers.IO) {
                file.writeBytes(bytes)
            }
            tempFile = file
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            error = e.message ?: "Erro ao carregar vídeo"
        } finally {
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            tempFile?.delete()
        }
    }

    Dialog(
        onDismissRequest = {
            exoPlayer.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                }
                error != null -> {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        Text(
                            text = error!!,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(bottom = 40.dp),
                        )
                        TextButton(
                            onClick = {
                                error = null
                                isLoading = true
                                tempFile?.delete()
                                tempFile = null
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = 40.dp),
                        ) {
                            Text("Tentar novamente", color = Color.White)
                        }
                    }
                }
                tempFile != null -> {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Close button
            IconButton(
                onClick = {
                    exoPlayer.stop()
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Fechar",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            // File name
            Text(
                text = fileName,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 24.dp),
            )
        }
    }
}
