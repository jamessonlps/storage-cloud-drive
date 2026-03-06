package com.clouddrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.clouddrive.s3.SettingsManager
import com.clouddrive.ui.FileListScreen
import com.clouddrive.ui.SettingsScreen
import com.clouddrive.ui.theme.CloudDriveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsManager = SettingsManager(applicationContext)

        setContent {
            CloudDriveTheme {
                val config by settingsManager.configFlow.collectAsState(initial = null)
                var showSettings by remember { mutableStateOf(false) }

                if (showSettings || config == null) {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        currentConfig = config,
                        onBack = { showSettings = false },
                    )
                } else {
                    FileListScreen(
                        config = config!!,
                        onNavigateToSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}
