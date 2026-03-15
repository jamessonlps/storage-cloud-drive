package com.clouddrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import com.clouddrive.s3.SettingsManager
import com.clouddrive.ui.FileListScreen
import com.clouddrive.ui.SettingsScreen
import com.clouddrive.ui.theme.CloudDriveTheme

enum class Screen { Home, Settings }

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsManager = SettingsManager(applicationContext)

        setContent {
            CloudDriveTheme {
                val config by settingsManager.configFlow.collectAsState(initial = null)
                var currentScreen by remember { mutableStateOf(Screen.Home) }

                // Folder navigation state (hoisted)
                var currentPrefix by remember { mutableStateOf("") }
                val pathStack = remember { mutableStateListOf<String>() }

                // Toolbar action triggers
                var refreshTrigger by remember { mutableStateOf(0) }
                var showNewFolderDialog by remember { mutableStateOf(false) }

                // Item count state for TopAppBar
                var itemCount by remember { mutableIntStateOf(0) }
                var itemsHasMore by remember { mutableStateOf(false) }

                // Shared snackbar
                val snackbarHostState = remember { SnackbarHostState() }

                // Reset folder state when config becomes null
                LaunchedEffect(config) {
                    if (config == null) {
                        currentPrefix = ""
                        pathStack.clear()
                    }
                }

                // System back button: navigate up folders, then switch to Home, then default
                BackHandler(enabled = currentScreen == Screen.Home && pathStack.isNotEmpty()) {
                    pathStack.removeLastOrNull()
                    currentPrefix = pathStack.lastOrNull() ?: ""
                }
                BackHandler(enabled = currentScreen == Screen.Settings) {
                    currentScreen = Screen.Home
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                when (currentScreen) {
                                    Screen.Home -> Column {
                                        if (config != null && itemCount > 0) {
                                            Text(
                                                text = if (itemsHasMore) "Exibindo $itemCount itens..." else "Exibindo todos os $itemCount itens",
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        } else {
                                            Text(
                                                "Cloud Drive S3",
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                        if (currentPrefix.isNotEmpty()) {
                                            Text(
                                                text = "/$currentPrefix",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Screen.Settings -> Text("Configuracoes AWS S3")
                                }
                            },
                            navigationIcon = {
                                if (currentScreen == Screen.Home && pathStack.isNotEmpty()) {
                                    IconButton(onClick = {
                                        pathStack.removeLastOrNull()
                                        currentPrefix = pathStack.lastOrNull() ?: ""
                                    }) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                                    }
                                }
                            },
                            actions = {
                                if (currentScreen == Screen.Home && config != null) {
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
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentScreen == Screen.Home,
                                onClick = { currentScreen = Screen.Home },
                                icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                label = { Text("Arquivos") },
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.Settings,
                                onClick = { currentScreen = Screen.Settings },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                label = { Text("Configuracoes") },
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
                            } else {
                                FileListScreen(
                                    config = config!!,
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
                                    modifier = Modifier.padding(padding),
                                )
                            }
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                settingsManager = settingsManager,
                                currentConfig = config,
                                snackbarHostState = snackbarHostState,
                                onSaveSuccess = { currentScreen = Screen.Home },
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                }
            }
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
            "Acesse as configuracoes para conectar ao seu bucket S3",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGoToSettings) {
            Text("Ir para Configuracoes")
        }
    }
}
