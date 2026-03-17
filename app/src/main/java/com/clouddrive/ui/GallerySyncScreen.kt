package com.clouddrive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.clouddrive.CloudDriveApp
import com.clouddrive.s3.BucketEntry
import com.clouddrive.s3.SettingsManager
import com.clouddrive.sync.GalleryContentObserver
import com.clouddrive.sync.GalleryScanner
import com.clouddrive.sync.GallerySyncScheduler
import com.clouddrive.sync.db.SyncStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySyncScreen(
    settingsManager: SettingsManager,
    profileName: String,
    buckets: List<BucketEntry>,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var syncEnabled by remember { mutableStateOf(settingsManager.isGallerySyncEnabled(profileName)) }
    var selectedBucket by remember { mutableStateOf(settingsManager.getGallerySyncBucket(profileName) ?: "") }
    var bucketExpanded by remember { mutableStateOf(false) }
    var s3Prefix by remember { mutableStateOf(settingsManager.getGallerySyncPrefix(profileName)) }
    var wifiOnly by remember { mutableStateOf(settingsManager.isGallerySyncWifiOnly(profileName)) }
    var enabledFolders by remember { mutableStateOf(settingsManager.getGallerySyncFolders(profileName).toSet()) }
    val lastRun = remember { settingsManager.getGallerySyncLastRun(profileName) }

    // Permission handling for media access
    val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasMediaPermission(): Boolean {
        return mediaPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasPermission by remember { mutableStateOf(hasMediaPermission()) }

    // Scan gallery folders
    var galleryFolders by remember { mutableStateOf<List<com.clouddrive.sync.GalleryFolder>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasPermission = results.values.all { it }
        if (hasPermission) {
            scanning = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    galleryFolders = GalleryScanner.scanGalleryFolders(context)
                }
                scanning = false
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            withContext(Dispatchers.IO) {
                galleryFolders = GalleryScanner.scanGalleryFolders(context)
            }
            scanning = false
        } else {
            permissionLauncher.launch(mediaPermissions)
        }
    }

    // Sync stats from Room
    val app = context.applicationContext as? CloudDriveApp
    val syncStats by remember(selectedBucket) {
        if (selectedBucket.isNotEmpty() && app != null) {
            app.syncDatabase.syncedFileDao().getSyncStats(profileName, selectedBucket)
        } else {
            kotlinx.coroutines.flow.flowOf(SyncStats(0, 0, 0, 0, 0))
        }
    }.collectAsState(initial = SyncStats(0, 0, 0, 0, 0))

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sincronização de Galeria",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Text(
            text = "Sincronize automaticamente fotos e vídeos da galeria para o S3. Funciona como backup — deletar do celular não remove do S3.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Enable toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = "Ativar sincronização",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Sincroniza a cada 6 horas automaticamente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = syncEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && selectedBucket.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("Selecione um bucket primeiro") }
                        return@Switch
                    }
                    if (enabled && enabledFolders.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar("Selecione ao menos uma pasta") }
                        return@Switch
                    }
                    syncEnabled = enabled
                    settingsManager.setGallerySyncEnabled(profileName, enabled)
                    if (enabled) {
                        GallerySyncScheduler.schedulePeriodic(
                            context, profileName, selectedBucket, wifiOnly,
                        )
                    } else {
                        GallerySyncScheduler.cancelPeriodic(context, profileName)
                    }
                    GalleryContentObserver.updateRegistration(context, settingsManager)
                },
            )
        }

        // Bucket selector
        Text(
            text = "Bucket de destino",
            style = MaterialTheme.typography.titleMedium,
        )

        ExposedDropdownMenuBox(
            expanded = bucketExpanded,
            onExpandedChange = { bucketExpanded = !bucketExpanded },
        ) {
            val displayName = buckets.find { it.awsName == selectedBucket }?.displayName
                ?: selectedBucket.ifBlank { "Selecionar bucket" }
            OutlinedTextField(
                value = displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Bucket") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bucketExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = bucketExpanded,
                onDismissRequest = { bucketExpanded = false },
            ) {
                buckets.forEach { bucket ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(bucket.displayName)
                                Text(
                                    bucket.awsName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            selectedBucket = bucket.awsName
                            settingsManager.setGallerySyncBucket(profileName, bucket.awsName)
                            bucketExpanded = false
                            if (syncEnabled) {
                                GallerySyncScheduler.schedulePeriodic(
                                    context, profileName, bucket.awsName, wifiOnly,
                                )
                            }
                        },
                    )
                }
            }
        }

        // S3 Prefix
        OutlinedTextField(
            value = s3Prefix,
            onValueChange = { value ->
                s3Prefix = value
                settingsManager.setGallerySyncPrefix(profileName, value)
            },
            label = { Text("Prefixo S3") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text("Exemplo: gallery-sync/Camera/IMG_001.jpg")
            },
        )

        // Wi-Fi only toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = "Apenas Wi-Fi",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Sincronizar somente em redes Wi-Fi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = wifiOnly,
                onCheckedChange = {
                    wifiOnly = it
                    settingsManager.setGallerySyncWifiOnly(profileName, it)
                    if (syncEnabled && selectedBucket.isNotBlank()) {
                        GallerySyncScheduler.schedulePeriodic(
                            context, profileName, selectedBucket, it,
                        )
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Folder selection
        Text(
            text = "Pastas da galeria",
            style = MaterialTheme.typography.titleMedium,
        )

        if (!hasPermission) {
            Text(
                text = "Permissão de acesso à mídia necessária para listar as pastas da galeria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = { permissionLauncher.launch(mediaPermissions) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Conceder Permissão")
            }
        } else if (scanning) {
            Text(
                text = "Escaneando galeria...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (galleryFolders.isEmpty()) {
            Text(
                text = "Nenhuma pasta encontrada na galeria",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            galleryFolders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = folder.name in enabledFolders,
                        onCheckedChange = { checked ->
                            enabledFolders = if (checked) {
                                enabledFolders + folder.name
                            } else {
                                enabledFolders - folder.name
                            }
                            settingsManager.setGallerySyncFolders(profileName, enabledFolders.toList())
                        },
                    )
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "${folder.fileCount} arquivo${if (folder.fileCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sync status card
        if (selectedBucket.isNotBlank() && syncStats.total > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Status da sincronização",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val progress = if (syncStats.total > 0) {
                        syncStats.completed.toFloat() / syncStats.total
                    } else 0f

                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${syncStats.completed} de ${syncStats.total} sincronizados",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    if (syncStats.pending > 0) {
                        Text(
                            text = "${syncStats.pending} pendente${if (syncStats.pending != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (syncStats.uploading > 0) {
                        Text(
                            text = "${syncStats.uploading} enviando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (syncStats.failed > 0) {
                        Text(
                            text = "${syncStats.failed} falha${if (syncStats.failed != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (lastRun > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val relativeTime = DateUtils.getRelativeTimeSpanString(
                            lastRun,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        )
                        Text(
                            text = "Última sincronização: $relativeTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    if (selectedBucket.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("Selecione um bucket primeiro") }
                        return@Button
                    }
                    if (enabledFolders.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar("Selecione ao menos uma pasta") }
                        return@Button
                    }
                    GallerySyncScheduler.triggerImmediate(
                        context, profileName, selectedBucket, wifiOnly,
                    )
                    scope.launch { snackbarHostState.showSnackbar("Sincronização iniciada") }
                },
                modifier = Modifier.weight(1f),
                enabled = selectedBucket.isNotBlank() && enabledFolders.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sincronizar Agora")
            }

            if (syncStats.failed > 0) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                app?.syncDatabase?.syncedFileDao()
                                    ?.retryAllFailed(profileName, selectedBucket)
                            }
                            GallerySyncScheduler.triggerImmediate(
                                context, profileName, selectedBucket, wifiOnly,
                            )
                            snackbarHostState.showSnackbar("Retentando falhas...")
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Retentar Falhas")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
