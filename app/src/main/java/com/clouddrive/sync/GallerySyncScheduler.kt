package com.clouddrive.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

object GallerySyncScheduler {

    private const val PERIODIC_WORK_PREFIX = "gallery-sync-periodic-"
    private const val IMMEDIATE_WORK_PREFIX = "gallery-sync-immediate-"

    fun schedulePeriodic(context: Context, profileName: String, bucketName: String, wifiOnly: Boolean = true) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(GallerySyncWorker.KEY_PROFILE_NAME, profileName)
            .putString(GallerySyncWorker.KEY_BUCKET_NAME, bucketName)
            .build()

        val request = PeriodicWorkRequestBuilder<GallerySyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "$PERIODIC_WORK_PREFIX$profileName",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelPeriodic(context: Context, profileName: String) {
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork(
            "$PERIODIC_WORK_PREFIX$profileName",
        )
    }

    fun triggerImmediate(context: Context, profileName: String, bucketName: String, wifiOnly: Boolean = true) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString(GallerySyncWorker.KEY_PROFILE_NAME, profileName)
            .putString(GallerySyncWorker.KEY_BUCKET_NAME, bucketName)
            .build()

        val request = OneTimeWorkRequestBuilder<GallerySyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "$IMMEDIATE_WORK_PREFIX$profileName",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
