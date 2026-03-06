package com.clouddrive.s3

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "s3_settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val KEY_ACCESS_KEY = stringPreferencesKey("access_key_id")
        private val KEY_SECRET_KEY = stringPreferencesKey("secret_access_key")
        private val KEY_REGION = stringPreferencesKey("region")
        private val KEY_BUCKET = stringPreferencesKey("bucket_name")
    }

    val configFlow: Flow<S3Config?> = context.dataStore.data.map { prefs ->
        val accessKey = prefs[KEY_ACCESS_KEY] ?: return@map null
        val secretKey = prefs[KEY_SECRET_KEY] ?: return@map null
        val region = prefs[KEY_REGION] ?: return@map null
        val bucket = prefs[KEY_BUCKET] ?: return@map null
        if (accessKey.isBlank() || secretKey.isBlank() || region.isBlank() || bucket.isBlank()) {
            null
        } else {
            S3Config(accessKey, secretKey, region, bucket)
        }
    }

    suspend fun saveConfig(config: S3Config) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_KEY] = config.accessKeyId
            prefs[KEY_SECRET_KEY] = config.secretAccessKey
            prefs[KEY_REGION] = config.region
            prefs[KEY_BUCKET] = config.bucketName
        }
    }

    suspend fun clearConfig() {
        context.dataStore.edit { it.clear() }
    }
}
