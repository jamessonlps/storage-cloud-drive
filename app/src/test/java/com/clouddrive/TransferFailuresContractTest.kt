package com.clouddrive

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.clouddrive.transfer.TransferItem
import com.clouddrive.transfer.TransferManager
import com.clouddrive.transfer.TransferState
import com.clouddrive.transfer.TransferType
import com.clouddrive.ui.TransferQueueScreen
import com.clouddrive.ui.theme.CloudDriveTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class TransferFailuresContractTest {
    @get:Rule val compose = createComposeRule()
    private val items = MutableStateFlow<List<TransferItem>>(emptyList())
    private fun item(name: String, state: TransferState, error: String? = null) = TransferItem(
        id = name, batchId = "batch-test", fileName = name, s3Key = name,
        type = TransferType.UPLOAD, state = state, errorMessage = error, createdAt = 1000L)
    @Before fun setup() {
        mockkObject(TransferManager)
        every { TransferManager.transfers } returns items
    }
    @After fun cleanup() { unmockkObject(TransferManager) }
    private fun show(values: List<TransferItem>) {
        items.value = values
        compose.setContent { CloudDriveTheme(themeMode = "light") { TransferQueueScreen(config = null) } }
    }
    private fun readOnlyQueue() {
        verify(atLeast = 1) { TransferManager.transfers }
        confirmVerified(TransferManager)
    }
    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test fun emptyQueueHasNoFailuresAction() {
        show(emptyList())
        compose.onNodeWithText("Nenhuma transferência").assertIsDisplayed()
        compose.onNodeWithText("Ver falhas").assertDoesNotExist()
        readOnlyQueue()
    }
    @Test fun failuresDialogShowsAllReasonsAndExcludesOtherStates() {
        val message = "Falha detalhada: " + "motivo longo para conferência ".repeat(12)
        show(listOf(item("erro-a.txt", TransferState.FAILED, "Acesso negado"),
            item("erro-b.txt", TransferState.FAILED, null),
            item("erro-c.txt", TransferState.FAILED, message),
            item("concluido.txt", TransferState.COMPLETED), item("cancelado.txt", TransferState.CANCELLED)))
        compose.onNodeWithText("Ver falhas").performClick()
        inDialog("erro-a.txt").assertIsDisplayed()
        inDialog("Acesso negado").assertIsDisplayed()
        inDialog("erro-b.txt").performScrollTo().assertIsDisplayed()
        inDialog("Motivo não informado").performScrollTo().assertIsDisplayed()
        inDialog("erro-c.txt").performScrollTo().assertIsDisplayed()
        inDialog(message).performScrollTo().assertIsDisplayed()
        inDialog("concluido.txt").assertDoesNotExist()
        inDialog("cancelado.txt").assertDoesNotExist()
        readOnlyQueue()
    }
    @Test fun closingDialogPreservesEveryTransferAndDoesNotRetry() {
        val original = listOf(item("falhou.txt", TransferState.FAILED, "Falha"))
        show(original)
        compose.onNodeWithText("Ver falhas").performClick()
        inDialog("falhou.txt").assertIsDisplayed()
        compose.onNodeWithText("Fechar").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(original, items.value)
        compose.onNodeWithText("Ver falhas").assertIsDisplayed()
        readOnlyQueue()
    }
    @Test fun partialFailuresCanBeInspectedWhileOtherFilesAreActive() {
        val original = listOf(item("falha-parcial.txt", TransferState.FAILED, "Conexão interrompida"),
            item("ainda-enviando.txt", TransferState.UPLOADING))
        show(original)
        compose.onNodeWithText("Ver falhas").performClick()
        inDialog("falha-parcial.txt").assertIsDisplayed()
        inDialog("ainda-enviando.txt").assertDoesNotExist()
        compose.onNodeWithText("Fechar").performClick()
        assertEquals(original, items.value)
        readOnlyQueue()
    }
}
