package com.clouddrive.sync

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.clouddrive.sync.db.SyncDatabase
import com.clouddrive.sync.db.SyncStatus
import com.clouddrive.sync.db.SyncedFileEntity

data class GalleryFolder(
    val name: String,
    val relativePath: String,
    val fileCount: Int,
)

data class MediaFileInfo(
    val mediaStoreId: Long,
    val uri: String,
    val folderName: String,
    val fileName: String,
    val fileSize: Long,
    val dateModified: Long,
    val mimeType: String,
)

object GalleryScanner {

    fun scanGalleryFolders(context: Context): List<GalleryFolder> {
        val folders = mutableMapOf<String, MutableList<String>>()

        queryMedia(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI) { folderName, relativePath, _ ->
            folders.getOrPut(folderName) { mutableListOf() }
            folders[folderName]!!.add(relativePath)
        }
        queryMedia(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI) { folderName, relativePath, _ ->
            folders.getOrPut(folderName) { mutableListOf() }
            folders[folderName]!!.add(relativePath)
        }

        return folders.map { (name, paths) ->
            GalleryFolder(
                name = name,
                relativePath = paths.firstOrNull() ?: name,
                fileCount = paths.size,
            )
        }.sortedBy { it.name }
    }

    suspend fun findNewFiles(
        context: Context,
        profileName: String,
        bucket: String,
        s3Prefix: String,
        enabledFolders: List<String>,
        db: SyncDatabase,
    ): List<SyncedFileEntity> {
        val dao = db.syncedFileDao()
        val tracked = dao.getTrackedMediaStoreIds(profileName, bucket).toSet()
        val newFiles = mutableListOf<SyncedFileEntity>()

        val collector = { info: MediaFileInfo ->
            if (info.mediaStoreId !in tracked && info.folderName in enabledFolders) {
                newFiles.add(
                    SyncedFileEntity(
                        mediaStoreId = info.mediaStoreId,
                        mediaStoreUri = info.uri,
                        folderName = info.folderName,
                        fileName = info.fileName,
                        fileSize = info.fileSize,
                        dateModified = info.dateModified,
                        mimeType = info.mimeType,
                        s3Key = "${s3Prefix}${info.folderName}/${info.fileName}",
                        s3Bucket = bucket,
                        profileName = profileName,
                        syncStatus = SyncStatus.PENDING,
                    ),
                )
            }
        }

        scanMediaFiles(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, collector)
        scanMediaFiles(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, collector)

        return newFiles
    }

    private fun scanMediaFiles(
        context: Context,
        collectionUri: android.net.Uri,
        onFile: (MediaFileInfo) -> Unit,
    ) {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.MIME_TYPE,
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.MIME_TYPE,
            )
        }

        context.contentResolver.query(
            collectionUri,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val date = cursor.getLong(dateCol)
                val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                val path = cursor.getString(pathCol) ?: ""

                val folderName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    path.trimEnd('/').split("/").lastOrNull() ?: "Unknown"
                } else {
                    val file = java.io.File(path)
                    file.parentFile?.name ?: "Unknown"
                }

                val uri = ContentUris.withAppendedId(collectionUri, id)

                onFile(
                    MediaFileInfo(
                        mediaStoreId = id,
                        uri = uri.toString(),
                        folderName = folderName,
                        fileName = name,
                        fileSize = size,
                        dateModified = date,
                        mimeType = mime,
                    ),
                )
            }
        }
    }

    private fun queryMedia(
        context: Context,
        collectionUri: android.net.Uri,
        onEntry: (folderName: String, relativePath: String, count: Int) -> Unit,
    ) {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            arrayOf(MediaStore.MediaColumns.DATA)
        }

        context.contentResolver.query(
            collectionUri,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val pathCol = cursor.getColumnIndexOrThrow(projection[0])

            while (cursor.moveToNext()) {
                val path = cursor.getString(pathCol) ?: continue
                val folderName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    path.trimEnd('/').split("/").lastOrNull() ?: "Unknown"
                } else {
                    val file = java.io.File(path)
                    file.parentFile?.name ?: "Unknown"
                }
                val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    path.trimEnd('/')
                } else {
                    val file = java.io.File(path)
                    file.parentFile?.absolutePath ?: path
                }
                onEntry(folderName, relativePath, 1)
            }
        }
    }
}
