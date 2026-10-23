package com.clouddrive

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.clouddrive.s3.PagedResult
import com.clouddrive.s3.S3ClientProvider
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3FileItem
import com.clouddrive.s3.S3Repository
import com.clouddrive.ui.FileListScreen
import com.clouddrive.ui.theme.CloudDriveTheme
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class FileDetailsContractTest {
    @get:Rule val compose = createComposeRule()
    private val file = S3FileItem("documentos/relatorio-com-nome-completo-para-conferencia.txt", 1536, "2026-10-17T12:30:00Z")
    private val folder = S3FileItem("Camera/", 0, "", true)
    private var navigated: String? = null
    private var selecting = false

    @Before fun setup() {
        mockkConstructor(S3Repository::class)
        coEvery { anyConstructed<S3Repository>().listFilesPaged(any(), any(), any()) } returns PagedResult(listOf(folder, file), null)
        mockkObject(S3ClientProvider)
        every { S3ClientProvider.getClient(any()) } throws AssertionError("Unexpected AWS client")
    }
    @After fun cleanup() {
        unmockkConstructor(S3Repository::class)
        unmockkObject(S3ClientProvider)
    }
    private fun show(grid: Boolean = false) {
        compose.setContent {
            CloudDriveTheme(themeMode = "light") {
                FileListScreen(S3Config("fake", "fake", "us-east-1", "fake"), grid, "",
                    { navigated = it }, 0, false, {}, 100, { _, _ -> },
                    { value, _, _ -> selecting = value }, 0, 0, 0, 0)
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText(file.fileName).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun onlyOriginalListing() {
        coVerify(exactly = 1) { anyConstructed<S3Repository>().listFilesPaged(any(), any(), any()) }
        verify { S3ClientProvider wasNot Called }
    }
    private fun dialogText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test fun existingListStillLoadsAndFolderClickNavigates() {
        show()
        compose.onNodeWithText(folder.fileName).performClick()
        assertEquals("Camera/", navigated)
        onlyOriginalListing()
    }
    @Test fun listDetailsShowFullValuesAndClosingPreservesNavigation() {
        show()
        compose.onNodeWithContentDescription("Detalhes de ${file.fileName}").performClick()
        dialogText(file.key).assertIsDisplayed()
        dialogText(file.fileName).assertIsDisplayed()
        dialogText(file.formattedSize).assertIsDisplayed()
        dialogText(file.lastModified).assertIsDisplayed()
        compose.onNodeWithText("Fechar").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertNull(navigated)
        assertFalse(selecting)
        onlyOriginalListing()
    }
    @Test fun gridFolderDetailsDoNotInventSizeOrDate() {
        show(grid = true)
        compose.onNodeWithContentDescription("Detalhes de Camera").performClick()
        dialogText("Camera/").assertIsDisplayed()
        dialogText("--").assertIsDisplayed()
        dialogText("Não disponível").assertIsDisplayed()
        assertNull(navigated)
        onlyOriginalListing()
    }
    @Test fun gridFileDetailsDoNotReplaceLongPressSelection() {
        show(grid = true)
        compose.onNodeWithContentDescription("Detalhes de ${file.fileName}").performClick()
        dialogText(file.key).assertIsDisplayed()
        compose.onNodeWithText("Fechar").performClick()
        compose.onNodeWithText(file.fileName).performTouchInput { longClick() }
        assertTrue(selecting)
        assertNull(navigated)
        onlyOriginalListing()
    }
}
