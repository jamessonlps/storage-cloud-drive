package com.clouddrive.ui

import androidx.biometric.BiometricManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clouddrive.crypto.EncryptionManager
import com.clouddrive.s3.BucketEntry
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.SettingsManager
import kotlinx.coroutines.launch

private const val MASKED_SECRET = "••••••••••••••••"

private val PAGE_SIZE_OPTIONS = listOf(50, 100, 200, 500)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    currentProfileName: String?,
    snackbarHostState: SnackbarHostState,
    onSaveSuccess: () -> Unit,
    onThemeModeChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles = remember { settingsManager.getProfileNames() }.toMutableList()
    var selectedProfile by remember { mutableStateOf(currentProfileName ?: "") }
    var profileExpanded by remember { mutableStateOf(false) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var showDeleteProfileDialog by remember { mutableStateOf(false) }

    // Load config for the selected profile
    val profileConfig = remember(selectedProfile) {
        if (selectedProfile.isNotBlank()) settingsManager.getProfileConfig(selectedProfile) else null
    }
    val hasExistingConfig = profileConfig != null
    val maskedAccessKey = remember(selectedProfile) {
        settingsManager.getMaskedAccessKey(selectedProfile) ?: ""
    }

    var accessKey by remember(selectedProfile) {
        mutableStateOf(if (hasExistingConfig) maskedAccessKey else "")
    }
    var secretKey by remember(selectedProfile) {
        mutableStateOf(if (hasExistingConfig) MASKED_SECRET else "")
    }
    var region by remember(selectedProfile) {
        mutableStateOf(profileConfig?.region ?: "us-east-1")
    }
    var buckets by remember(selectedProfile) {
        mutableStateOf(
            if (selectedProfile.isNotBlank()) settingsManager.getBuckets(selectedProfile) else emptyList(),
        )
    }
    var showAddBucketDialog by remember { mutableStateOf(false) }
    var showSecret by remember(selectedProfile) { mutableStateOf(false) }
    var accessKeyEdited by remember(selectedProfile) { mutableStateOf(false) }
    var secretKeyEdited by remember(selectedProfile) { mutableStateOf(false) }

    var selectedPageSize by remember { mutableIntStateOf(settingsManager.getPageSize()) }
    var pageSizeExpanded by remember { mutableStateOf(false) }
    var selectedThemeMode by remember { mutableStateOf(settingsManager.getThemeMode()) }
    var themeModeExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // --- Profile Selector ---
        Text(
            text = "Perfil",
            style = MaterialTheme.typography.titleMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposedDropdownMenuBox(
                expanded = profileExpanded,
                onExpandedChange = { profileExpanded = !profileExpanded },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = selectedProfile.ifBlank { "Nenhum perfil" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Perfil ativo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = profileExpanded,
                    onDismissRequest = { profileExpanded = false },
                ) {
                    profiles.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedProfile = name
                                settingsManager.setCurrentProfile(name)
                                profileExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { showNewProfileDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Novo perfil")
            }

            if (profiles.size > 1 && selectedProfile.isNotBlank()) {
                IconButton(onClick = { showDeleteProfileDialog = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Excluir perfil",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- Credentials ---
        Text(
            text = "Credenciais AWS",
            style = MaterialTheme.typography.titleMedium,
        )

        if (hasExistingConfig) {
            Text(
                text = "Credenciais salvas e criptografadas. Para alterar, insira novos valores.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = accessKey,
            onValueChange = {
                accessKey = it
                accessKeyEdited = true
            },
            label = { Text("Access Key ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            readOnly = hasExistingConfig && !accessKeyEdited,
        )

        OutlinedTextField(
            value = secretKey,
            onValueChange = {
                secretKey = it
                secretKeyEdited = true
            },
            label = { Text("Secret Access Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showSecret && secretKeyEdited) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            readOnly = hasExistingConfig && !secretKeyEdited,
            trailingIcon = {
                if (secretKeyEdited) {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            imageVector = if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showSecret) "Esconder" else "Mostrar",
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Região",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = region,
            onValueChange = { region = it },
            label = { Text("Região (ex: us-east-1)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Buckets",
            style = MaterialTheme.typography.titleMedium,
        )

        if (buckets.isEmpty()) {
            Text(
                text = "Nenhum bucket cadastrado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            buckets.forEach { bucket ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bucket.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = bucket.awsName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        if (selectedProfile.isNotBlank()) {
                            settingsManager.removeBucket(selectedProfile, bucket.awsName)
                            buckets = settingsManager.getBuckets(selectedProfile)
                        }
                    }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remover bucket",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { showAddBucketDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedProfile.isNotBlank(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Adicionar Bucket")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configurações de Listagem",
            style = MaterialTheme.typography.titleMedium,
        )

        ExposedDropdownMenuBox(
            expanded = pageSizeExpanded,
            onExpandedChange = { pageSizeExpanded = !pageSizeExpanded },
        ) {
            OutlinedTextField(
                value = "$selectedPageSize itens por página",
                onValueChange = {},
                readOnly = true,
                label = { Text("Itens por lote") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pageSizeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = pageSizeExpanded,
                onDismissRequest = { pageSizeExpanded = false },
            ) {
                PAGE_SIZE_OPTIONS.forEach { size ->
                    DropdownMenuItem(
                        text = { Text("$size itens") },
                        onClick = {
                            selectedPageSize = size
                            settingsManager.savePageSize(size)
                            pageSizeExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Aparencia",
            style = MaterialTheme.typography.titleMedium,
        )

        ExposedDropdownMenuBox(
            expanded = themeModeExpanded,
            onExpandedChange = { themeModeExpanded = !themeModeExpanded },
        ) {
            OutlinedTextField(
                value = when (selectedThemeMode) {
                    SettingsManager.THEME_LIGHT -> "Claro"
                    SettingsManager.THEME_DARK -> "Escuro"
                    else -> "Sistema"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Tema") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeModeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = themeModeExpanded,
                onDismissRequest = { themeModeExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Sistema") },
                    onClick = {
                        selectedThemeMode = SettingsManager.THEME_SYSTEM
                        settingsManager.saveThemeMode(SettingsManager.THEME_SYSTEM)
                        onThemeModeChanged(SettingsManager.THEME_SYSTEM)
                        themeModeExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Claro") },
                    onClick = {
                        selectedThemeMode = SettingsManager.THEME_LIGHT
                        settingsManager.saveThemeMode(SettingsManager.THEME_LIGHT)
                        onThemeModeChanged(SettingsManager.THEME_LIGHT)
                        themeModeExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Escuro") },
                    onClick = {
                        selectedThemeMode = SettingsManager.THEME_DARK
                        settingsManager.saveThemeMode(SettingsManager.THEME_DARK)
                        onThemeModeChanged(SettingsManager.THEME_DARK)
                        themeModeExpanded = false
                    },
                )
            }
        }

        // Biometric authentication toggle
        val context = LocalContext.current
        val biometricManager = remember { BiometricManager.from(context) }
        val canAuthenticate = remember {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

        if (canAuthenticate) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Segurança",
                style = MaterialTheme.typography.titleMedium,
            )

            var biometricEnabled by remember { mutableStateOf(settingsManager.isBiometricEnabled()) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = "Autenticação biométrica",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Exigir fingerprint ao abrir o app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = {
                        biometricEnabled = it
                        settingsManager.setBiometricEnabled(it)
                    },
                )
            }
        }

        // Encryption toggle
        if (selectedProfile.isNotBlank()) {
            var encryptionEnabled by remember(selectedProfile) {
                mutableStateOf(settingsManager.isEncryptionEnabled(selectedProfile))
            }

            if (!canAuthenticate) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Segurança",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.EnhancedEncryption,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = "Criptografia de arquivos",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Criptografa arquivos com AES-256 antes do upload",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = encryptionEnabled,
                    onCheckedChange = { enabled ->
                        encryptionEnabled = enabled
                        settingsManager.setEncryptionEnabled(selectedProfile, enabled)
                        if (enabled) {
                            EncryptionManager.getOrCreateKey(settingsManager, selectedProfile)
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (selectedProfile.isBlank()) {
                    scope.launch { snackbarHostState.showSnackbar("Crie um perfil primeiro") }
                    return@Button
                }

                val finalAccessKey = if (hasExistingConfig && !accessKeyEdited) {
                    profileConfig!!.accessKeyId
                } else {
                    accessKey.trim()
                }
                val finalSecretKey = if (hasExistingConfig && !secretKeyEdited) {
                    profileConfig!!.secretAccessKey
                } else {
                    secretKey.trim()
                }

                if (finalAccessKey.isBlank() || finalSecretKey.isBlank() || region.isBlank()) {
                    scope.launch { snackbarHostState.showSnackbar("Preencha todos os campos de credenciais") }
                    return@Button
                }

                settingsManager.saveProfile(
                    selectedProfile,
                    S3Config(finalAccessKey, finalSecretKey, region.trim(), bucketName = ""),
                )
                scope.launch {
                    snackbarHostState.showSnackbar("Perfil \"$selectedProfile\" salvo!")
                    onSaveSuccess()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar")
        }

        OutlinedButton(
            onClick = {
                settingsManager.clearAllProfiles()
                accessKey = ""
                secretKey = ""
                region = "us-east-1"
                buckets = emptyList()
                accessKeyEdited = false
                secretKeyEdited = false
                selectedProfile = ""
                profiles.clear()
                scope.launch {
                    snackbarHostState.showSnackbar("Todas as configurações removidas")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Limpar Tudo")
        }
    }

    // New profile dialog
    if (showNewProfileDialog) {
        var newProfileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            title = { Text("Novo Perfil") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Nome do perfil") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newProfileName.trim()
                        if (name.isNotBlank() && name !in profiles) {
                            profiles.add(name)
                            selectedProfile = name
                            settingsManager.setCurrentProfile(name)
                            showNewProfileDialog = false
                        }
                    },
                ) {
                    Text("Criar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProfileDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    // Add bucket dialog
    if (showAddBucketDialog) {
        var newBucketAwsName by remember { mutableStateOf("") }
        var newBucketDisplayName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddBucketDialog = false },
            title = { Text("Adicionar Bucket") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newBucketAwsName,
                        onValueChange = { newBucketAwsName = it },
                        label = { Text("Nome do Bucket (AWS)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newBucketDisplayName,
                        onValueChange = { newBucketDisplayName = it },
                        label = { Text("Nome de exibição") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val awsName = newBucketAwsName.trim()
                        val displayName = newBucketDisplayName.trim().ifBlank { awsName }
                        if (awsName.isNotBlank() && selectedProfile.isNotBlank()) {
                            settingsManager.addBucket(
                                selectedProfile,
                                BucketEntry(awsName = awsName, displayName = displayName),
                            )
                            buckets = settingsManager.getBuckets(selectedProfile)
                            showAddBucketDialog = false
                        }
                    },
                ) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBucketDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    // Delete profile confirmation dialog
    if (showDeleteProfileDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProfileDialog = false },
            title = { Text("Excluir perfil") },
            text = { Text("Deseja excluir o perfil \"$selectedProfile\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nameToDelete = selectedProfile
                        profiles.remove(nameToDelete)
                        settingsManager.deleteProfile(nameToDelete)
                        selectedProfile = settingsManager.getCurrentProfileName() ?: ""
                        showDeleteProfileDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Perfil \"$nameToDelete\" excluído")
                        }
                    },
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProfileDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
