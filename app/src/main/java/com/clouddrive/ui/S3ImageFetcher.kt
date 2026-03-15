package com.clouddrive.ui

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.clouddrive.s3.S3Config
import com.clouddrive.s3.S3Repository
import okio.Buffer
import java.io.File
import java.security.MessageDigest

data class S3ImageRequest(
    val key: String,
    val config: S3Config,
)

class S3ImageFetcher(
    private val data: S3ImageRequest,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val cacheDir = File(options.context.cacheDir, "s3_thumbnails")
        cacheDir.mkdirs()
        val cacheKey = MessageDigest.getInstance("SHA-256")
            .digest(data.key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cachedFile = File(cacheDir, cacheKey)

        val bytes = if (cachedFile.exists()) {
            cachedFile.readBytes()
        } else {
            val repository = S3Repository(data.config)
            val downloaded = repository.downloadFileBytes(data.key)
            cachedFile.writeBytes(downloaded)
            downloaded
        }

        val buffer = Buffer().apply { write(bytes) }
        return SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = null,
            dataSource = if (cachedFile.exists()) DataSource.DISK else DataSource.NETWORK,
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
