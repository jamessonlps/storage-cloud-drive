package com.clouddrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.SettingsManager
import kotlinx.coroutines.launch

private const val MASKED_SECRET = "••••••••••••••••"

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    currentConfig: S3Config?,
    snackbarHostState: SnackbarHostState,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasExistingConfig = currentConfig != null
    val maskedAccessKey = remember { settingsManager.getMaskedAccessKey() ?: "" }

    // For credentials: show masked placeholder if already saved, empty if not
    var accessKey by remember { mutableStateOf(if (hasExistingConfig) maskedAccessKey else "") }
    var secretKey by remember { mutableStateOf(if (hasExistingConfig) MASKED_SECRET else "") }
    var region by remember { mutableStateOf(currentConfig?.region ?: "us-east-1") }
    var bucket by remember { mutableStateOf(currentConfig?.bucketName ?: "") }
    var showSecret by remember { mutableStateOf(false) }

    // Track if user has modified credential fields
    var accessKeyEdited by remember { mutableStateOf(false) }
    var secretKeyEdited by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
            text = "Configuracao do Bucket",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = region,
            onValueChange = { region = it },
            label = { Text("Regiao (ex: us-east-1)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = bucket,
            onValueChange = { bucket = it },
            label = { Text("Nome do Bucket") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // If existing config and credentials not edited, only save region/bucket changes
                val finalAccessKey = if (hasExistingConfig && !accessKeyEdited) {
                    currentConfig!!.accessKeyId
                } else {
                    accessKey.trim()
                }
                val finalSecretKey = if (hasExistingConfig && !secretKeyEdited) {
                    currentConfig!!.secretAccessKey
                } else {
                    secretKey.trim()
                }

                if (finalAccessKey.isBlank() || finalSecretKey.isBlank() || region.isBlank() || bucket.isBlank()) {
                    scope.launch { snackbarHostState.showSnackbar("Preencha todos os campos") }
                    return@Button
                }

                settingsManager.saveConfig(
                    S3Config(finalAccessKey, finalSecretKey, region.trim(), bucket.trim())
                )
                scope.launch {
                    snackbarHostState.showSnackbar("Configuracoes salvas com criptografia!")
                    onSaveSuccess()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar")
        }

        OutlinedButton(
            onClick = {
                settingsManager.clearConfig()
                accessKey = ""
                secretKey = ""
                region = "us-east-1"
                bucket = ""
                accessKeyEdited = false
                secretKeyEdited = false
                scope.launch {
                    snackbarHostState.showSnackbar("Configuracoes removidas")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Limpar Configuracoes")
        }
    }
}
