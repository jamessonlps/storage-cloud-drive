package com.clouddrive.s3

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "s3_encrypted_settings"
        private const val KEY_ACCESS_KEY = "access_key_id"
        private const val KEY_SECRET_KEY = "secret_access_key"
        private const val KEY_REGION = "region"
        private const val KEY_BUCKET = "bucket_name"
        private const val KEY_PAGE_SIZE = "page_size"
        const val DEFAULT_PAGE_SIZE = 100
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _configFlow = MutableStateFlow(readConfig())
    val configFlow: Flow<S3Config?> = _configFlow

    private fun readConfig(): S3Config? {
        val accessKey = prefs.getString(KEY_ACCESS_KEY, null) ?: return null
        val secretKey = prefs.getString(KEY_SECRET_KEY, null) ?: return null
        val region = prefs.getString(KEY_REGION, null) ?: return null
        val bucket = prefs.getString(KEY_BUCKET, null) ?: return null
        if (accessKey.isBlank() || secretKey.isBlank() || region.isBlank() || bucket.isBlank()) {
            return null
        }
        return S3Config(accessKey, secretKey, region, bucket)
    }

    fun saveConfig(config: S3Config) {
        prefs.edit()
            .putString(KEY_ACCESS_KEY, config.accessKeyId)
            .putString(KEY_SECRET_KEY, config.secretAccessKey)
            .putString(KEY_REGION, config.region)
            .putString(KEY_BUCKET, config.bucketName)
            .apply()
        _configFlow.value = readConfig()
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
        _configFlow.value = null
    }

    fun getMaskedAccessKey(): String? {
        val key = prefs.getString(KEY_ACCESS_KEY, null) ?: return null
        if (key.length <= 8) return "••••••••"
        return key.take(4) + "••••" + key.takeLast(4)
    }

    fun hasCredentials(): Boolean = readConfig() != null

    fun getPageSize(): Int = prefs.getInt(KEY_PAGE_SIZE, DEFAULT_PAGE_SIZE)

    fun savePageSize(size: Int) {
        prefs.edit().putInt(KEY_PAGE_SIZE, size).apply()
    }
}
