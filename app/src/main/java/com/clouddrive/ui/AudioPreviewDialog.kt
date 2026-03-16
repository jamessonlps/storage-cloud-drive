package com.clouddrive.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AudioPreviewDialog(
    audioKey: String,
    fileName: String,
    config: S3Config,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val repository = remember(config) { S3Repository(config) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    // Add player listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Poll current position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500L)
        }
    }

    // Download audio
    LaunchedEffect(audioKey) {
        isLoading = true
        error = null
        try {
            val bytes = withContext(Dispatchers.IO) {
                repository.downloadFileAsBytes(audioKey)
            }
            val file = File(context.cacheDir, "audio_preview_${audioKey.hashCode()}.tmp")
            withContext(Dispatchers.IO) {
                file.writeBytes(bytes)
            }
            tempFile = file
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exoPlayer.prepare()
        } catch (e: Exception) {
            error = e.message ?: "Erro ao carregar áudio"
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
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Carregando áudio...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                error = null
                                isLoading = true
                                tempFile?.delete()
                                tempFile = null
                            },
                        ) {
                            Text("Tentar novamente")
                        }
                    }
                }
                tempFile != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Close button row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(
                                onClick = {
                                    exoPlayer.stop()
                                    onDismiss()
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Fechar",
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Audio icon
                        val iconColor = getAudioIconColor(fileName)
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = "Arquivo de áudio",
                            modifier = Modifier.size(80.dp),
                            tint = iconColor,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // File name
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Seek slider
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { fraction ->
                                val seekPosition = (fraction * duration).toLong()
                                exoPlayer.seekTo(seekPosition)
                                currentPosition = seekPosition
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Time display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Playback controls
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Skip back 10s
                            IconButton(
                                onClick = {
                                    val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                    currentPosition = newPos
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Replay10,
                                    contentDescription = "Voltar 10 segundos",
                                    modifier = Modifier.size(36.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Play/Pause
                            IconButton(
                                onClick = {
                                    exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                                },
                                modifier = Modifier.size(64.dp),
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                                    modifier = Modifier.size(48.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Skip forward 10s
                            IconButton(
                                onClick = {
                                    val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(duration)
                                    exoPlayer.seekTo(newPos)
                                    currentPosition = newPos
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Forward10,
                                    contentDescription = "Avançar 10 segundos",
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

private fun getAudioIconColor(fileName: String): Color {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "mp3" -> Color(0xFF1DB954)
        "wav" -> Color(0xFF2196F3)
        "flac" -> Color(0xFFFF9800)
        "aac", "m4a" -> Color(0xFF9C27B0)
        "ogg" -> Color(0xFFE91E63)
        "wma" -> Color(0xFF00BCD4)
        else -> Color(0xFF607D8B)
    }
}
