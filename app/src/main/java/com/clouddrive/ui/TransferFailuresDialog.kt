package com.clouddrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clouddrive.transfer.TransferItem

// Read-only: closing it never retries, removes, pauses or cancels a transfer.
@Composable
fun TransferFailuresDialog(
    failedItems: TransferFailuresSnapshot,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Falhas do lote (${failedItems.size})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(failedItems.size) { index ->
                    val item = failedItems[index]
                    Column {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = item.errorMessage?.takeIf { it.isNotBlank() } ?: "Motivo não informado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
    )
}

// Immutable snapshot: a private copy, so the caller's mutable list is never aliased here.
@Immutable
class TransferFailuresSnapshot(failures: List<TransferItem>) {
    private val items: List<TransferItem> = failures.toList()

    val size: Int
        get() = items.size

    operator fun get(index: Int): TransferItem = items[index]
}
