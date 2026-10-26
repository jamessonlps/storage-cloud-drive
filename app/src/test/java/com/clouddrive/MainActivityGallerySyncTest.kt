package com.clouddrive

import android.app.Application
import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.clouddrive.s3.S3ClientProvider
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.SettingsManager
import com.clouddrive.sync.GalleryContentObserver
import com.clouddrive.sync.GalleryScanner
import com.clouddrive.sync.GallerySyncScheduler
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Launches the production MainActivity and navigates using its real Compose tree.
 * Only settings and external side-effect boundaries are replaced. Application is
 * deliberately plain: this test does not claim to exercise Keystore, Room startup,
 * CloudDriveApp's observer registration, actual media permissions, or AWS.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityGallerySyncTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val config = MutableStateFlow<S3Config?>(null)
    private val profile = MutableStateFlow<String?>(null)
    private lateinit var settings: SettingsManager
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        settings = mockk(relaxed = true)
        every { settings.configFlow } returns config
        every { settings.currentProfileNameFlow } returns profile
        every { settings.getThemeMode() } returns SettingsManager.THEME_LIGHT
        every { settings.isBiometricEnabled() } returns false
        every { settings.getProfileNames() } returns emptyList()
        mockkObject(SettingsManager.Companion)
        every { SettingsManager.getInstance(any()) } returns settings
        mockkObject(S3ClientProvider, GalleryScanner, GallerySyncScheduler, GalleryContentObserver)
    }

    @After
    fun tearDown() {
        try {
            scenario?.close()
        } finally {
            unmockkObject(SettingsManager.Companion, S3ClientProvider, GalleryScanner,
                GallerySyncScheduler, GalleryContentObserver)
        }
    }

    private fun launchGallerySync() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Configurações").performClick()
        compose.onNodeWithText("Sincronização").performClick()
        compose.onNodeWithText("Sincronização de galeria", ignoreCase = true).assertIsDisplayed()
    }

    private fun verifyNoExternalWork() {
        verify { S3ClientProvider wasNot Called }
        verify { GalleryScanner wasNot Called }
        verify { GallerySyncScheduler wasNot Called }
        verify { GalleryContentObserver wasNot Called }
        verify(exactly = 0) { settings.setGallerySyncEnabled(any(), any()) }
    }

    @Test
    fun homeWithoutCredentialsStillOpensSettingsMenu() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Configure suas credenciais AWS").assertIsDisplayed()
        compose.onNodeWithText("Ir para Configurações").performClick()
        compose.onNodeWithText("Conta AWS").assertIsDisplayed()
        compose.onNodeWithText("Sincronização").assertIsDisplayed()
        verifyNoExternalWork()
    }

    @Test
    fun galleryWithoutProfileExplainsMissingCredentials() {
        launchGallerySync()
        compose.onNodeWithText("Configure suas credenciais AWS").assertIsDisplayed()
        compose.onNodeWithText("Configurar conta AWS").assertIsDisplayed()
        compose.onNodeWithText("Arquivos").assertIsDisplayed()
        compose.onNodeWithText("Transferências").assertIsDisplayed()
        compose.onNodeWithText("Configurações").assertIsDisplayed()
        verifyNoExternalWork()
    }

    @Test
    fun gallerySetupActionOpensAwsAccountFormDirectly() {
        launchGallerySync()
        compose.onNodeWithText("Configurar conta AWS").performClick()
        compose.onNodeWithText("Perfil ativo").assertIsDisplayed()
        compose.onNodeWithText("Access Key ID").assertIsDisplayed()
        verifyNoExternalWork()
    }

    @Test
    fun profileWithoutConfigStillShowsSetupInsteadOfStartingGallery() {
        profile.value = "perfil-incompleto"
        launchGallerySync()
        compose.onNodeWithText("Configurar conta AWS").assertIsDisplayed()
        compose.onNodeWithText("Ativar sincronização").assertDoesNotExist()
        verifyNoExternalWork()
    }

    @Test
    fun receivingConfiguredProfileReplacesSetupWithRealGalleryScreen() {
        launchGallerySync()
        compose.onNodeWithText("Configurar conta AWS").assertIsDisplayed()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
        )
        every { settings.getGallerySyncPrefix("piloto") } returns "gallery-sync/"
        every { GalleryScanner.scanGalleryFolders(any()) } returns emptyList()

        // Update the same flows consumed by production MainActivity while it is on
        // GallerySync; do not select a bucket or create an AWS client.
        compose.runOnIdle {
            profile.value = "piloto"
            config.value = S3Config("fake-access", "fake-secret", "us-east-1", "fake-bucket")
        }

        compose.onNodeWithText("Ativar sincronização").assertIsDisplayed()
        compose.onNodeWithText("Configurar conta AWS").assertDoesNotExist()
        verify(timeout = 5000, exactly = 1) { GalleryScanner.scanGalleryFolders(any()) }
        verify { S3ClientProvider wasNot Called }
        verify { GallerySyncScheduler wasNot Called }
        verify { GalleryContentObserver wasNot Called }
        verify(exactly = 0) { settings.setGallerySyncEnabled(any(), any()) }
    }
}
