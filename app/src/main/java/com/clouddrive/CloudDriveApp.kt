package com.clouddrive

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.clouddrive.s3.SettingsManager
import com.clouddrive.sync.GalleryContentObserver
import com.clouddrive.sync.db.SyncDatabase
import com.clouddrive.ui.S3ImageFetcher

class CloudDriveApp : Application(), ImageLoaderFactory {

    lateinit var syncDatabase: SyncDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        syncDatabase = SyncDatabase.getInstance(this)

        // Register ContentObserver if any profile has gallery sync enabled
        val settingsManager = SettingsManager(this)
        GalleryContentObserver.updateRegistration(this, settingsManager)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(S3ImageFetcher.Factory())
            }
            .crossfade(true)
            .build()
    }
}
