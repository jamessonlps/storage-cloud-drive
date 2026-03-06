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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    currentConfig: S3Config?,
    onBack: () -> Unit,
) {
    var accessKey by remember { mutableStateOf(currentConfig?.accessKeyId ?: "") }
    var secretKey by remember { mutableStateOf(currentConfig?.secretAccessKey ?: "") }
    var region by remember { mutableStateOf(currentConfig?.region ?: "us-east-1") }
    var bucket by remember { mutableStateOf(currentConfig?.bucketName ?: "") }
    var showSecret by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuracoes AWS S3") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Credenciais AWS",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = accessKey,
                onValueChange = { accessKey = it },
                label = { Text("Access Key ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = secretKey,
                onValueChange = { secretKey = it },
                label = { Text("Secret Access Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            imageVector = if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showSecret) "Esconder" else "Mostrar",
                        )
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
                    if (accessKey.isBlank() || secretKey.isBlank() || region.isBlank() || bucket.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("Preencha todos os campos") }
                        return@Button
                    }
                    scope.launch {
                        settingsManager.saveConfig(
                            S3Config(accessKey.trim(), secretKey.trim(), region.trim(), bucket.trim())
                        )
                        snackbarHostState.showSnackbar("Configuracoes salvas!")
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar")
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        settingsManager.clearConfig()
                        accessKey = ""
                        secretKey = ""
                        region = "us-east-1"
                        bucket = ""
                        snackbarHostState.showSnackbar("Configuracoes removidas")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Limpar Configuracoes")
            }
        }
    }
}
