package com.clouddrive.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

/**
 * Publishes a finished download to the public Downloads folder (`Download/CloudDriveS3`).
 *
 * Since Android 10 (scoped storage) an app cannot create files there with plain `File` APIs, so
 * it goes through MediaStore, which needs no storage permission for files the app creates. Older
 * versions keep using the file system (WRITE_EXTERNAL_STORAGE up to API 28).
 */
object DownloadsStore {
    const val FOLDER = "CloudDriveS3"

    /** Copies [source] into Downloads, then deletes it. Never leaves a half-written visible file. */
    fun publish(context: Context, source: File, displayName: String) {
        val name = FileNames.sanitize(displayName) ?: throw IllegalArgumentException("Nome de arquivo invalido")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishScoped(context, source, name)
        } else {
            publishLegacy(source, name)
        }
        source.delete()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishScoped(context: Context, source: File, name: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            // Hidden from other apps until the content is complete.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Nao foi possivel criar o arquivo em Downloads")
        try {
            val out = resolver.openOutputStream(uri)
                ?: throw IOException("Nao foi possivel gravar em Downloads")
            out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(source: File, name: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Nao foi possivel criar a pasta de downloads")
        val dest = FileNames.resolveInside(dir, name)
        val partial = File(dir, "$name.${System.nanoTime()}${CacheCleaner.PARTIAL_SUFFIX}")
        try {
            source.copyTo(partial, overwrite = true)
            if (dest.exists() && !dest.delete()) throw IOException("Nao foi possivel substituir o arquivo existente")
            if (!partial.renameTo(dest)) throw IOException("Nao foi possivel finalizar o arquivo baixado")
        } finally {
            partial.delete()
        }
    }

    private fun mimeTypeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}
