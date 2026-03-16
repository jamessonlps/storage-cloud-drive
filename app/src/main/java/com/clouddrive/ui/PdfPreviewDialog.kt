package com.clouddrive.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPreviewDialog(
    pdfKey: String,
    fileName: String,
    config: S3Config,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    val bitmapCache = remember { mutableMapOf<Int, Bitmap>() }

    val repository = remember(config) { S3Repository(config) }

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }

    LaunchedEffect(pdfKey) {
        isLoading = true
        error = null
        try {
            val bytes = withContext(Dispatchers.IO) {
                repository.downloadFileAsBytes(pdfKey)
            }
            val file = File(context.cacheDir, "pdf_preview_${pdfKey.hashCode()}.pdf")
            withContext(Dispatchers.IO) {
                file.writeBytes(bytes)
            }
            tempFile = file
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fd)
            renderer = pdfRenderer
            pageCount = pdfRenderer.pageCount
        } catch (e: Exception) {
            error = e.message ?: "Erro ao carregar PDF"
        } finally {
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.close()
            tempFile?.delete()
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.clear()
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
                                renderer?.close()
                                renderer = null
                                bitmapCache.values.forEach { it.recycle() }
                                bitmapCache.clear()
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = 40.dp),
                        ) {
                            Text("Tentar novamente", color = Color.White)
                        }
                    }
                }
                renderer != null && pageCount > 0 -> {
                    val pagerState = rememberPagerState(pageCount = { pageCount })

                    LaunchedEffect(pagerState.currentPage) {
                        val currentRenderer = renderer ?: return@LaunchedEffect
                        val pagesToRender = listOf(
                            pagerState.currentPage - 1,
                            pagerState.currentPage,
                            pagerState.currentPage + 1,
                        ).filter { it in 0 until pageCount }

                        withContext(Dispatchers.IO) {
                            for (pageIndex in pagesToRender) {
                                if (!bitmapCache.containsKey(pageIndex)) {
                                    val page = currentRenderer.openPage(pageIndex)
                                    val aspectRatio = page.width.toFloat() / page.height.toFloat()
                                    val bitmapWidth = screenWidthPx
                                    val bitmapHeight = (bitmapWidth / aspectRatio).toInt()
                                    val bitmap = Bitmap.createBitmap(
                                        bitmapWidth,
                                        bitmapHeight,
                                        Bitmap.Config.ARGB_8888,
                                    )
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(
                                        bitmap,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                    )
                                    page.close()
                                    bitmapCache[pageIndex] = bitmap
                                }
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val bitmap = bitmapCache[pageIndex]
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Pagina ${pageIndex + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    }

                    // Page indicator
                    Text(
                        text = "Pagina ${pagerState.currentPage + 1} de $pageCount",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    )
                }
            }

            // Close button
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
