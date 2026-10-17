package com.clouddrive.util

import java.io.File

/**
 * Housekeeping for the app's cache directory. Meant to run once at process start, off the main
 * thread.
 *
 * - Preview temp files (audio/pdf/video) are normally deleted when their dialog closes, but a
 *   crash or a process kill leaves them behind: full plaintext copies of the user's files.
 * - The thumbnail cache had no size limit and grew forever.
 * - Download staging and upload spool files from interrupted transfers.
 */
object CacheCleaner {

    const val THUMBNAIL_DIR = "s3_thumbnails"
    const val PARTIAL_SUFFIX = ".part"

    /** Downloads are staged here (decrypted) before being published to the public Downloads folder. */
    const val DOWNLOAD_DIR = "downloads"

    /** Uploads from sources of unknown size are copied here to learn their length. */
    const val UPLOAD_SPOOL_DIR = "upload_spool"
    const val THUMBNAIL_MAX_BYTES = 200L * 1024 * 1024

    private val PREVIEW_PREFIXES = listOf("audio_preview_", "pdf_preview_", "video_preview_")
    private const val STALE_PARTIAL_AGE_MS = 60L * 60 * 1000

    fun run(cacheDir: File) {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && PREVIEW_PREFIXES.any { file.name.startsWith(it) }) {
                file.delete()
            }
        }

        val thumbnails = File(cacheDir, THUMBNAIL_DIR)
        val now = System.currentTimeMillis()
        thumbnails.listFiles()?.forEach { file ->
            // Old enough that no download can still be writing to it.
            if (file.name.endsWith(PARTIAL_SUFFIX) && now - file.lastModified() > STALE_PARTIAL_AGE_MS) {
                file.delete()
            }
        }
        trimToSize(thumbnails, THUMBNAIL_MAX_BYTES)

        // Plaintext copies left by a transfer that was killed half way.
        for (name in listOf(DOWNLOAD_DIR, UPLOAD_SPOOL_DIR)) {
            File(cacheDir, name).listFiles()?.forEach { file ->
                if (file.isFile && now - file.lastModified() > STALE_PARTIAL_AGE_MS) file.delete()
            }
        }
    }

    /** Deletes the least recently modified files until the directory fits in [maxBytes]. */
    internal fun trimToSize(dir: File, maxBytes: Long) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return

        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }
}
