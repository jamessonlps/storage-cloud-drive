package com.clouddrive.ui

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3Repository
import com.clouddrive.util.CacheCleaner
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class S3ImageRequest(
    val key: String,
    val config: S3Config,
) {
    // Keeps the secret key out of Coil's request logging and error messages.
    override fun toString(): String = "S3ImageRequest(key=$key)"

    val cacheId: String
        get() = "${config.bucketName}/$key"
}

/**
 * Without a Keyer Coil cannot build a cache key for [S3ImageRequest], so thumbnails were never
 * stored in its in-memory cache and were re-decoded from disk on every scroll.
 */
class S3ImageKeyer : Keyer<S3ImageRequest> {
    override fun key(data: S3ImageRequest, options: Options): String = "s3:${data.cacheId}"
}

class S3ImageFetcher(
    private val data: S3ImageRequest,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val cacheDir = File(options.context.cacheDir, CacheCleaner.THUMBNAIL_DIR)
        cacheDir.mkdirs()
        // The bucket is part of the cache key: the same object key in two buckets (or profiles)
        // used to share, and overwrite, a single cached thumbnail.
        val cacheKey = MessageDigest.getInstance("SHA-256")
            .digest(data.cacheId.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cachedFile = File(cacheDir, cacheKey)

        val fromDisk = cachedFile.length() > 0
        if (fromDisk) {
            // Lets CacheCleaner evict the least recently used thumbnails first.
            cachedFile.setLastModified(System.currentTimeMillis())
        } else {
            // Download to a temporary name and rename afterwards: a cancelled or failed download
            // must never leave a truncated image that would then be served from cache forever.
            val partial = File(cacheDir, cacheKey + CacheCleaner.PARTIAL_SUFFIX)
            try {
                S3Repository.forCurrentProfile(options.context, data.config).downloadFile(data.key, partial)
                if (!partial.renameTo(cachedFile)) {
                    throw IOException("Nao foi possivel gravar a miniatura no cache")
                }
            } finally {
                partial.delete()
            }
        }

        // Decoded straight from the cached file: no full-size copy of the image held in memory.
        return SourceResult(
            source = ImageSource(file = cachedFile.toOkioPath(), fileSystem = FileSystem.SYSTEM),
            mimeType = null,
            dataSource = if (fromDisk) DataSource.DISK else DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<S3ImageRequest> {
        override fun create(
            data: S3ImageRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return S3ImageFetcher(data, options)
        }
    }
}
