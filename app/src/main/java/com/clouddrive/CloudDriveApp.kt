package com.clouddrive

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.clouddrive.s3.SettingsManager
import com.clouddrive.sync.GalleryContentObserver
import com.clouddrive.sync.db.SyncDatabase
import com.clouddrive.ui.S3ImageFetcher
import com.clouddrive.ui.S3ImageKeyer
import com.clouddrive.util.CacheCleaner
import kotlin.concurrent.thread

class CloudDriveApp : Application(), ImageLoaderFactory {

    lateinit var syncDatabase: SyncDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        syncDatabase = SyncDatabase.getInstance(this)

        // Register ContentObserver if any profile has gallery sync enabled
        val settingsManager = SettingsManager.getInstance(this)
        GalleryContentObserver.updateRegistration(this, settingsManager)

        // Leftover preview copies from a killed process, and an unbounded thumbnail cache.
        // Disk I/O, so it stays off the main thread.
        thread(name = "cache-cleaner") { CacheCleaner.run(cacheDir) }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(S3ImageKeyer())
                add(S3ImageFetcher.Factory())
            }
            .crossfade(true)
            .build()
    }
}
