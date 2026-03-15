package com.clouddrive

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.clouddrive.ui.S3ImageFetcher

class CloudDriveApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(S3ImageFetcher.Factory())
            }
            .crossfade(true)
            .build()
    }
}
