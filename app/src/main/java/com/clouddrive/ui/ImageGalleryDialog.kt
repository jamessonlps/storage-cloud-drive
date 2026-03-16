package com.clouddrive.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3FileItem
import com.clouddrive.s3.S3Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGalleryDialog(
    imageFiles: List<S3FileItem>,
    initialIndex: Int,
    config: S3Config,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imageFiles.size },
    )

    val imageCache = remember { mutableStateMapOf<String, ByteArray>() }
    val errorPages = remember { mutableStateMapOf<Int, String>() }
    val loadingPages = remember { mutableStateMapOf<Int, Boolean>() }

    var currentScale by remember { mutableFloatStateOf(1f) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val repository = remember(config) { S3Repository(config) }

    // Pre-load current page and adjacent pages
    LaunchedEffect(pagerState.settledPage, retryTrigger) {
        val currentPage = pagerState.settledPage
        val pagesToLoad = listOf(currentPage, currentPage - 1, currentPage + 1)
            .filter { it in imageFiles.indices }

        for (pageIndex in pagesToLoad) {
            val fileItem = imageFiles[pageIndex]
            if (imageCache.containsKey(fileItem.key) || loadingPages[pageIndex] == true) {
                continue
            }
            loadingPages[pageIndex] = true
            errorPages.remove(pageIndex)
            try {
                val bytes = withContext(Dispatchers.IO) {
                    repository.downloadFileAsBytes(fileItem.key)
                }
                imageCache[fileItem.key] = bytes
            } catch (e: Exception) {
                errorPages[pageIndex] = e.message ?: "Erro ao carregar imagem"
            } finally {
                loadingPages[pageIndex] = false
            }
        }
    }

    // Clean up cache on dismiss
    DisposableEffect(Unit) {
        onDispose {
            imageCache.clear()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = currentScale <= 1f,
            ) { page ->
                val fileItem = imageFiles[page]
                val bytes = imageCache[fileItem.key]
                val isLoading = loadingPages[page] == true
                val error = errorPages[page]

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        bytes != null -> {
                            ZoomableImage(
                                imageBytes = bytes,
                                contentDescription = fileItem.fileName,
                                modifier = Modifier.fillMaxSize(),
                                onScaleChanged = { scale ->
                                    if (page == pagerState.currentPage) {
                                        currentScale = scale
                                    }
                                },
                            )
                        }
                        isLoading -> {
                            CircularProgressIndicator(
                                color = Color.White,
                            )
                        }
                        error != null -> {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = error,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 40.dp),
                                )
                                TextButton(
                                    onClick = {
                                        errorPages.remove(page)
                                        loadingPages.remove(page)
                                        imageCache.remove(fileItem.key)
                                        retryTrigger++
                                    },
                                    modifier = Modifier.padding(top = 40.dp),
                                ) {
                                    Text("Tentar novamente", color = Color.White)
                                }
                            }
                        }
                        else -> {
                            CircularProgressIndicator(
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            // Filename at top-left
            Text(
                text = imageFiles[pagerState.currentPage].fileName,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 24.dp),
            )

            // Close button at top-right
            IconButton(
                onClick = onDismiss,
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

            // Page indicator at bottom center
            Text(
                text = "${pagerState.currentPage + 1} / ${imageFiles.size}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
    }
}
