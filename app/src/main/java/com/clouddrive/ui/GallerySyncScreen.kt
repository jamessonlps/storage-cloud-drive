package com.clouddrive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.clouddrive.s3.SettingsManager.Companion.normalizeS3Prefix
import com.clouddrive.sync.GalleryContentObserver
import com.clouddrive.sync.GalleryScanner
import com.clouddrive.sync.GallerySyncScheduler
import com.clouddrive.sync.db.SyncStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun InlineHelpIcon(
    title: String,
    helpText: String,
) {
    var showHelp by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showHelp = true },
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            Icons.Filled.HelpOutline,
            contentDescription = "Ajuda sobre $title",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(title) },
            text = { Text(helpText) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text("Entendi")
                }
            },
        )
    }
}

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ativar sincronização",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    InlineHelpIcon(
                        title = "Sincronização automática",
                        helpText = "Quando ativada, a sincronização roda automaticamente a cada 6 horas (respeitando a opção de Wi-Fi). Novas fotos são detectadas em tempo real e enviadas em até 30 segundos. A sincronização continua mesmo com o app fechado.",
                    )
                }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Bucket de destino",
                style = MaterialTheme.typography.titleMedium,
            )
            InlineHelpIcon(
                title = "Bucket de destino",
                helpText = "Selecione o bucket S3 onde as fotos e vídeos serão enviados. Pode ser o mesmo bucket usado para arquivos manuais ou um bucket separado dedicado ao backup da galeria. Os buckets disponíveis são os cadastrados no perfil atual.",
            )
        }

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Prefixo S3",
                style = MaterialTheme.typography.titleMedium,
            )
            InlineHelpIcon(
                title = "Prefixo S3",
                helpText = "O prefixo é a \"pasta raiz\" dentro do bucket onde os arquivos serão salvos. A estrutura final será: prefixo/NomeDaPasta/arquivo.jpg. Por exemplo, com prefixo \"galeria\": galeria/Camera/IMG_001.jpg, galeria/Screenshots/Screenshot_01.png. Não use barra no início.",
            )
        }

        OutlinedTextField(
            value = s3Prefix,
            onValueChange = { value ->
                // Allow Unicode letters/digits, slash, dash, underscore, dot; strip control chars
                val cleaned = value.replace(Regex("[^\\p{L}\\p{N}/_\\-.]"), "")
                s3Prefix = cleaned
                settingsManager.setGallerySyncPrefix(profileName, cleaned)
            },
            label = { Text("Prefixo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                val normalized = normalizeS3Prefix(s3Prefix)
                val preview = if (normalized.isNotEmpty()) {
                    "${normalized}Camera/IMG_001.jpg"
                } else {
                    "Camera/IMG_001.jpg"
                }
                Text("Resultado: $preview")
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Apenas Wi-Fi",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    InlineHelpIcon(
                        title = "Apenas Wi-Fi",
                        helpText = "Quando ativado, a sincronização só acontece em redes Wi-Fi, evitando consumo do plano de dados móveis. Recomendado manter ativado, especialmente para galerias com muitos vídeos.",
                    )
                }
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

        // Folder selection — summary row + BottomSheet
        var showFolderSheet by remember { mutableStateOf(false) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Pastas da galeria",
                style = MaterialTheme.typography.titleMedium,
            )
            InlineHelpIcon(
                title = "Pastas da galeria",
                helpText = "Selecione quais pastas da galeria do celular serão sincronizadas com o S3. Cada pasta selecionada será replicada como um diretório dentro do prefixo configurado. Apenas fotos e vídeos são incluídos.",
            )
        }

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
        } else {
            // Summary row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !scanning && galleryFolders.isNotEmpty()) {
                        showFolderSheet = true
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (scanning) {
                            Text(
                                text = "Escaneando galeria...",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else if (galleryFolders.isEmpty()) {
                            Text(
                                text = "Nenhuma pasta encontrada",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            Text(
                                text = "Selecionar pastas",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val selectedCount = enabledFolders.size
                            val totalCount = galleryFolders.size
                            Text(
                                text = if (selectedCount == 0) {
                                    "Nenhuma selecionada"
                                } else {
                                    "$selectedCount de $totalCount selecionada${if (selectedCount != 1) "s" else ""}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedCount == 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    if (scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (galleryFolders.isNotEmpty()) {
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = "Abrir seleção de pastas",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Folder picker BottomSheet
        if (showFolderSheet && galleryFolders.isNotEmpty()) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { showFolderSheet = false },
                sheetState = sheetState,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Header
                    Text(
                        text = "Selecionar pastas",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${enabledFolders.size} de ${galleryFolders.size} selecionada${if (enabledFolders.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Quick actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            enabledFolders = galleryFolders.map { it.name }.toSet()
                            settingsManager.setGallerySyncFolders(profileName, enabledFolders.toList())
                        }) {
                            Text("Todas")
                        }
                        TextButton(onClick = {
                            enabledFolders = emptySet()
                            settingsManager.setGallerySyncFolders(profileName, emptyList())
                        }) {
                            Text("Nenhuma")
                        }
                    }

                    @Suppress("DEPRECATION")
                    Divider()

                    // Folder list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                    ) {
                        items(galleryFolders, key = { it.name }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        enabledFolders = if (folder.name in enabledFolders) {
                                            enabledFolders - folder.name
                                        } else {
                                            enabledFolders + folder.name
                                        }
                                        settingsManager.setGallerySyncFolders(
                                            profileName,
                                            enabledFolders.toList(),
                                        )
                                    }
                                    .padding(vertical = 8.dp),
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
                                        settingsManager.setGallerySyncFolders(
                                            profileName,
                                            enabledFolders.toList(),
                                        )
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

                    Spacer(modifier = Modifier.height(16.dp))
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
