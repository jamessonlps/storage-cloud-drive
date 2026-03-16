package com.clouddrive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.clouddrive.s3.BucketEntry
import com.clouddrive.s3.SettingsManager
import com.clouddrive.transfer.TransferItem
import com.clouddrive.transfer.TransferManager
import com.clouddrive.transfer.TransferState
import com.clouddrive.transfer.TransferType
import com.clouddrive.service.TransferService
import com.clouddrive.ui.BucketListScreen
import com.clouddrive.ui.FileListScreen
import com.clouddrive.ui.SettingsScreen
import com.clouddrive.ui.TransferQueueScreen
import com.clouddrive.ui.theme.CloudDriveTheme

enum class Screen { Home, Settings, Transfers }

class MainActivity : FragmentActivity() {

    private val pendingTransfersScreen = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(TransferService.EXTRA_OPEN_TRANSFERS, false)) {
            pendingTransfersScreen.value = true
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent.getBooleanExtra(TransferService.EXTRA_OPEN_TRANSFERS, false)) {
            pendingTransfersScreen.value = true
        }

        val settingsManager = SettingsManager(applicationContext)

        setContent {
            var themeMode by remember { mutableStateOf(settingsManager.getThemeMode()) }

            CloudDriveTheme(themeMode = themeMode) {
                var isAuthenticated by remember { mutableStateOf(false) }

                // Check biometric on launch
                LaunchedEffect(Unit) {
                    if (!settingsManager.isBiometricEnabled()) {
                        isAuthenticated = true
                    } else {
                        showBiometricPrompt(
                            onSuccess = { isAuthenticated = true },
                            onCancel = { finish() },
                        )
                    }
                }

                if (!isAuthenticated) {
                    LockScreen(onRetry = {
                        showBiometricPrompt(
                            onSuccess = { isAuthenticated = true },
                            onCancel = { finish() },
                        )
                    })
                    return@CloudDriveTheme
                }

                val config by settingsManager.configFlow.collectAsState(initial = null)
                val currentProfileName by settingsManager.currentProfileNameFlow.collectAsState(initial = null)
                var currentScreen by remember { mutableStateOf(Screen.Home) }

                // Handle notification tap to open Transfers tab
                LaunchedEffect(pendingTransfersScreen.value) {
                    if (pendingTransfersScreen.value) {
                        currentScreen = Screen.Transfers
                        pendingTransfersScreen.value = false
                    }
                }

                // Handle share intent
                LaunchedEffect(config) {
                    val cfg = config ?: return@LaunchedEffect
                    handleShareIntent(intent, cfg, settingsManager, currentProfileName)
                }

                // Bucket selection state
                var selectedBucket by remember { mutableStateOf<BucketEntry?>(null) }

                // Folder navigation state (hoisted)
                var currentPrefix by remember { mutableStateOf("") }
                val pathStack = remember { mutableStateListOf<String>() }

                // View mode state
                var isGridView by remember { mutableStateOf(false) }

                // Toolbar action triggers
                var refreshTrigger by remember { mutableStateOf(0) }
                var showNewFolderDialog by remember { mutableStateOf(false) }

                // Item count state for TopAppBar
                var itemCount by remember { mutableIntStateOf(0) }
                var itemsHasMore by remember { mutableStateOf(false) }

                // Selection mode state
                var isSelectionMode by remember { mutableStateOf(false) }
                var selectedCount by remember { mutableIntStateOf(0) }
                var hasSelectedFolders by remember { mutableStateOf(false) }
                var selectAllTrigger by remember { mutableIntStateOf(0) }
                var clearSelectionTrigger by remember { mutableIntStateOf(0) }
                var deleteSelectedTrigger by remember { mutableIntStateOf(0) }
                var downloadSelectedTrigger by remember { mutableIntStateOf(0) }

                // Shared snackbar
                val snackbarHostState = remember { SnackbarHostState() }

                // Reset folder and bucket state when config/profile changes
                LaunchedEffect(config) {
                    currentPrefix = ""
                    pathStack.clear()
                    selectedBucket = null
                }

                // System back button: exit selection mode first
                BackHandler(enabled = currentScreen == Screen.Home && isSelectionMode) {
                    clearSelectionTrigger++
                }

                // System back button: navigate up folders, then back to bucket list, then default
                BackHandler(enabled = currentScreen == Screen.Home && !isSelectionMode && pathStack.isNotEmpty()) {
                    pathStack.removeLastOrNull()
                    currentPrefix = pathStack.lastOrNull() ?: ""
                }
                BackHandler(enabled = currentScreen == Screen.Home && !isSelectionMode && pathStack.isEmpty() && selectedBucket != null) {
                    selectedBucket = null
                }
                BackHandler(enabled = currentScreen == Screen.Settings || currentScreen == Screen.Transfers) {
                    currentScreen = Screen.Home
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                when {
                                    currentScreen == Screen.Home && isSelectionMode -> {
                                        Text(
                                            "$selectedCount selecionado${if (selectedCount != 1) "s" else ""}",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                    currentScreen == Screen.Home && selectedBucket != null -> Column {
                                        if (itemCount > 0) {
                                            Text(
                                                text = if (itemsHasMore) "Exibindo $itemCount itens..." else "Exibindo todos os $itemCount itens",
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        } else {
                                            Text(
                                                selectedBucket!!.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                        val subtitle = buildString {
                                            if (!currentProfileName.isNullOrBlank()) append(currentProfileName)
                                            append(" / ${selectedBucket!!.displayName}")
                                            if (currentPrefix.isNotEmpty()) {
                                                append(" / $currentPrefix")
                                            }
                                        }
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    currentScreen == Screen.Home -> Column {
                                        Text(
                                            "Cloud Drive S3",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        if (!currentProfileName.isNullOrBlank()) {
                                            Text(
                                                text = currentProfileName!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    currentScreen == Screen.Transfers -> Text(
                                        "Transferências",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    else -> Text("Configurações AWS S3")
                                }
                            },
                            navigationIcon = {
                                if (currentScreen == Screen.Home && isSelectionMode) {
                                    IconButton(onClick = { clearSelectionTrigger++ }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Cancelar seleção")
                                    }
                                } else if (currentScreen == Screen.Home && pathStack.isNotEmpty()) {
                                    IconButton(onClick = {
                                        pathStack.removeLastOrNull()
                                        currentPrefix = pathStack.lastOrNull() ?: ""
                                    }) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                                    }
                                } else if (currentScreen == Screen.Home && selectedBucket != null) {
                                    IconButton(onClick = { selectedBucket = null }) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar aos buckets")
                                    }
                                }
                            },
                            actions = {
                                if (currentScreen == Screen.Home && isSelectionMode) {
                                    IconButton(onClick = { selectAllTrigger++ }) {
                                        Icon(Icons.Filled.SelectAll, contentDescription = "Selecionar tudo")
                                    }
                                    IconButton(
                                        onClick = { downloadSelectedTrigger++ },
                                        enabled = !hasSelectedFolders && selectedCount > 0,
                                    ) {
                                        Icon(
                                            Icons.Filled.CloudDownload,
                                            contentDescription = "Baixar selecionados",
                                            tint = if (!hasSelectedFolders && selectedCount > 0)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                        )
                                    }
                                    IconButton(
                                        onClick = { deleteSelectedTrigger++ },
                                        enabled = selectedCount > 0,
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Excluir selecionados",
                                            tint = if (selectedCount > 0)
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                        )
                                    }
                                } else if (currentScreen == Screen.Transfers) {
                                    val transfers by TransferManager.transfers.collectAsState()
                                    val hasCompleted = transfers.any {
                                        it.state == TransferState.COMPLETED || it.state == TransferState.CANCELLED
                                    }
                                    if (hasCompleted) {
                                        IconButton(onClick = { TransferManager.clearCompleted() }) {
                                            Icon(Icons.Filled.ClearAll, contentDescription = "Limpar concluídos")
                                        }
                                    }
                                } else if (currentScreen == Screen.Home && config != null && selectedBucket != null) {
                                    IconButton(onClick = { isGridView = !isGridView }) {
                                        Icon(
                                            imageVector = if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                                            contentDescription = if (isGridView) "Modo lista" else "Modo grade",
                                        )
                                    }
                                    IconButton(onClick = { showNewFolderDialog = true }) {
                                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Nova pasta")
                                    }
                                    IconButton(onClick = { refreshTrigger++ }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        val activeTransferCount by TransferManager.activeCount.collectAsState()

                        NavigationBar {
                            NavigationBarItem(
                                selected = currentScreen == Screen.Home,
                                onClick = { currentScreen = Screen.Home },
                                icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                label = { Text("Arquivos") },
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.Transfers,
                                onClick = { currentScreen = Screen.Transfers },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (activeTransferCount > 0) {
                                                Badge { Text("$activeTransferCount") }
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Filled.SwapVert, contentDescription = null)
                                    }
                                },
                                label = { Text("Transferências") },
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.Settings,
                                onClick = { currentScreen = Screen.Settings },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                label = { Text("Configurações") },
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    when (currentScreen) {
                        Screen.Home -> {
                            if (config == null) {
                                NoCredentialsScreen(
                                    onGoToSettings = { currentScreen = Screen.Settings },
                                    modifier = Modifier.padding(padding),
                                )
                            } else if (selectedBucket == null) {
                                val buckets = remember(config, currentProfileName) {
                                    currentProfileName?.let { settingsManager.getBuckets(it) } ?: emptyList()
                                }
                                BucketListScreen(
                                    buckets = buckets,
                                    onBucketSelected = { bucket ->
                                        selectedBucket = bucket
                                        currentPrefix = ""
                                        pathStack.clear()
                                    },
                                    modifier = Modifier.padding(padding),
                                )
                            } else {
                                FileListScreen(
                                    config = config!!.copy(bucketName = selectedBucket!!.awsName),
                                    isGridView = isGridView,
                                    currentPrefix = currentPrefix,
                                    onNavigateToFolder = { folderKey ->
                                        pathStack.add(folderKey)
                                        currentPrefix = folderKey
                                    },
                                    refreshTrigger = refreshTrigger,
                                    showNewFolderDialog = showNewFolderDialog,
                                    onDismissNewFolderDialog = { showNewFolderDialog = false },
                                    pageSize = settingsManager.getPageSize(),
                                    onItemCountChanged = { count, hasMore ->
                                        itemCount = count
                                        itemsHasMore = hasMore
                                    },
                                    onSelectionChanged = { selMode, selCount, hasFolders ->
                                        isSelectionMode = selMode
                                        selectedCount = selCount
                                        hasSelectedFolders = hasFolders
                                    },
                                    selectAllTrigger = selectAllTrigger,
                                    clearSelectionTrigger = clearSelectionTrigger,
                                    deleteSelectedTrigger = deleteSelectedTrigger,
                                    downloadSelectedTrigger = downloadSelectedTrigger,
                                    settingsManager = settingsManager,
                                    profileName = currentProfileName,
                                    modifier = Modifier.padding(padding),
                                )
                            }
                        }
                        Screen.Transfers -> {
                            TransferQueueScreen(
                                config = config,
                                settingsManager = settingsManager,
                                profileName = currentProfileName,
                                modifier = Modifier.padding(padding),
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                settingsManager = settingsManager,
                                currentProfileName = currentProfileName,
                                snackbarHostState = snackbarHostState,
                                onSaveSuccess = { currentScreen = Screen.Home },
                                onThemeModeChanged = { themeMode = it },
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                }
            }
        }
    }

    private var shareIntentHandled = false

    private fun handleShareIntent(
        intent: Intent,
        config: com.clouddrive.s3.S3Config,
        settingsManager: SettingsManager,
        profileName: String?,
    ) {
        if (shareIntentHandled) return

        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            }
        }

        if (uris.isEmpty()) return
        shareIntentHandled = true

        val batchId = java.util.UUID.randomUUID().toString()
        val items = uris.map { uri ->
            val fileName = getShareFileName(uri) ?: "arquivo_${System.currentTimeMillis()}"
            val fileSize = getShareFileSize(uri)
            val contentType = contentResolver.getType(uri)
            TransferItem(
                batchId = batchId,
                fileName = fileName,
                s3Key = fileName,
                type = TransferType.UPLOAD,
                totalBytes = fileSize,
                sourceUri = uri.toString(),
                contentType = contentType,
                bucketName = config.bucketName,
            )
        }
        TransferManager.enqueueBatch(items, config, this, settingsManager, profileName)

        Toast.makeText(this, "Upload iniciado: ${uris.size} arquivo(s)", Toast.LENGTH_SHORT).show()
    }

    private fun getShareFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    private fun getShareFileSize(uri: Uri): Long {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(sizeIndex)
            }
        }
        return 0L
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit, onCancel: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)

        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            onSuccess()
            return
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    onCancel()
                }
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Cloud Drive S3")
            .setSubtitle("Autentique-se para acessar seus arquivos")
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        BiometricPrompt(this, executor, callback).authenticate(promptInfo)
    }
}

@Composable
private fun LockScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Cloud Drive S3",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Autenticação biométrica necessária",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(
                Icons.Filled.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Autenticar")
        }
    }
}

@Composable
private fun NoCredentialsScreen(
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Configure suas credenciais AWS",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Acesse as configurações para conectar ao seu bucket S3",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGoToSettings) {
            Text("Ir para Configurações")
        }
    }
}
