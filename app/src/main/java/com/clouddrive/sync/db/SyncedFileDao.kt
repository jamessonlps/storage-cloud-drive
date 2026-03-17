package com.clouddrive.sync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncedFileDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(files: List<SyncedFileEntity>)

    @Query(
        """SELECT * FROM synced_files
        WHERE profileName = :profileName AND s3Bucket = :bucket AND syncStatus = 'PENDING'
        ORDER BY dateModified ASC LIMIT :limit"""
    )
    suspend fun getPendingFiles(profileName: String, bucket: String, limit: Int = 20): List<SyncedFileEntity>

    @Query(
        """SELECT * FROM synced_files
        WHERE profileName = :profileName AND s3Bucket = :bucket AND syncStatus = 'FAILED'
        ORDER BY dateModified ASC"""
    )
    suspend fun getFailedFiles(profileName: String, bucket: String): List<SyncedFileEntity>

    @Query(
        """SELECT mediaStoreId FROM synced_files
        WHERE profileName = :profileName AND s3Bucket = :bucket"""
    )
    suspend fun getTrackedMediaStoreIds(profileName: String, bucket: String): List<Long>

    @Query(
        """UPDATE synced_files
        SET syncStatus = :status, syncedAt = :syncedAt, errorMessage = :error, retryCount = retryCount + :retryIncrement
        WHERE id = :id"""
    )
    suspend fun updateStatus(
        id: Long,
        status: SyncStatus,
        syncedAt: Long? = null,
        error: String? = null,
        retryIncrement: Int = 0,
    )

    @Query(
        """UPDATE synced_files SET syncStatus = 'PENDING', errorMessage = NULL, retryCount = 0
        WHERE profileName = :profileName AND s3Bucket = :bucket AND syncStatus = 'FAILED'"""
    )
    suspend fun retryAllFailed(profileName: String, bucket: String)

    @Query(
        """SELECT
            COUNT(*) as total,
            SUM(CASE WHEN syncStatus = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
            SUM(CASE WHEN syncStatus = 'PENDING' THEN 1 ELSE 0 END) as pending,
            SUM(CASE WHEN syncStatus = 'FAILED' THEN 1 ELSE 0 END) as failed,
            SUM(CASE WHEN syncStatus = 'UPLOADING' THEN 1 ELSE 0 END) as uploading
        FROM synced_files
        WHERE profileName = :profileName AND s3Bucket = :bucket"""
    )
    fun getSyncStats(profileName: String, bucket: String): Flow<SyncStats>

    @Query("DELETE FROM synced_files WHERE profileName = :profileName")
    suspend fun deleteByProfile(profileName: String)

    @Query("DELETE FROM synced_files WHERE profileName = :profileName AND s3Bucket = :bucket")
    suspend fun deleteByBucket(profileName: String, bucket: String)

    @Query(
        """SELECT DISTINCT folderName FROM synced_files
        WHERE profileName = :profileName AND s3Bucket = :bucket"""
    )
    suspend fun getSyncedFolders(profileName: String, bucket: String): List<String>
}
