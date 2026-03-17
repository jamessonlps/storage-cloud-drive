package com.clouddrive.sync.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncStatus {
    PENDING, UPLOADING, COMPLETED, FAILED
}

@Entity(
    tableName = "synced_files",
    indices = [
        Index(value = ["profileName", "s3Bucket", "syncStatus"]),
        Index(value = ["profileName", "s3Bucket", "mediaStoreId"], unique = true),
    ],
)
data class SyncedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val mediaStoreUri: String,
    val folderName: String,
    val fileName: String,
    val fileSize: Long,
    val dateModified: Long,
    val mimeType: String,
    val s3Key: String,
    val s3Bucket: String,
    val profileName: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val syncedAt: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
)

data class SyncStats(
    val total: Int,
    val completed: Int,
    val pending: Int,
    val failed: Int,
    val uploading: Int,
)
