package com.clouddrive.sync

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.clouddrive.s3.SettingsManager

class GalleryContentObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper()),
) : ContentObserver(handler) {

    private var pendingTrigger: Runnable? = null
    private val debounceHandler = Handler(Looper.getMainLooper())

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        // Debounce: wait 30 seconds after last change before triggering sync
        pendingTrigger?.let { debounceHandler.removeCallbacks(it) }
        pendingTrigger = Runnable { triggerSync() }
        debounceHandler.postDelayed(pendingTrigger!!, DEBOUNCE_MS)
    }

    private fun triggerSync() {
        val settingsManager = SettingsManager(context)
        val profiles = settingsManager.getProfileNames()

        for (profile in profiles) {
            if (!settingsManager.isGallerySyncEnabled(profile)) continue
            val bucket = settingsManager.getGallerySyncBucket(profile) ?: continue
            val wifiOnly = settingsManager.isGallerySyncWifiOnly(profile)
            GallerySyncScheduler.triggerImmediate(context, profile, bucket, wifiOnly)
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 30_000L

        private var instance: GalleryContentObserver? = null

        fun register(context: Context) {
            if (instance != null) return
            val observer = GalleryContentObserver(context.applicationContext)
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
            instance = observer
        }

        fun unregister(context: Context) {
            instance?.let {
                it.pendingTrigger?.let { runnable ->
                    it.debounceHandler.removeCallbacks(runnable)
                }
                context.contentResolver.unregisterContentObserver(it)
            }
            instance = null
        }

        fun updateRegistration(context: Context, settingsManager: SettingsManager) {
            if (settingsManager.isAnySyncEnabled()) {
                register(context)
            } else {
                unregister(context)
            }
        }
    }
}
