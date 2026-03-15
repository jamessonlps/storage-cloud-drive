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
        private const val KEY_PROFILE_LIST = "profile_list"
        private const val KEY_CURRENT_PROFILE = "current_profile"
        private const val KEY_PAGE_SIZE = "page_size"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val DEFAULT_PROFILE_NAME = "Padrao"
        const val DEFAULT_PAGE_SIZE = 100
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        // Legacy keys (pre-multi-profile)
        private const val LEGACY_KEY_ACCESS_KEY = "access_key_id"
        private const val LEGACY_KEY_SECRET_KEY = "secret_access_key"
        private const val LEGACY_KEY_REGION = "region"
        private const val LEGACY_KEY_BUCKET = "bucket_name"

        // Profile-prefixed key helpers
        private fun profileKey(profile: String, field: String) = "profile_${profile}_$field"
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

    init {
        migrateLegacyConfig()
    }

    private val _configFlow = MutableStateFlow(readCurrentProfileConfig())
    val configFlow: Flow<S3Config?> = _configFlow

    private val _currentProfileNameFlow = MutableStateFlow(getCurrentProfileName())
    val currentProfileNameFlow: Flow<String?> = _currentProfileNameFlow

    // --- Migration ---

    private fun migrateLegacyConfig() {
        // If profile list already exists, migration was done
        if (prefs.contains(KEY_PROFILE_LIST)) return

        // Check if legacy keys exist
        val accessKey = prefs.getString(LEGACY_KEY_ACCESS_KEY, null) ?: return
        val secretKey = prefs.getString(LEGACY_KEY_SECRET_KEY, null) ?: return
        val region = prefs.getString(LEGACY_KEY_REGION, null) ?: return
        val bucket = prefs.getString(LEGACY_KEY_BUCKET, null) ?: return

        if (accessKey.isBlank() || secretKey.isBlank() || region.isBlank() || bucket.isBlank()) return

        // Migrate to default profile
        val name = DEFAULT_PROFILE_NAME
        prefs.edit()
            .putString(profileKey(name, "access_key_id"), accessKey)
            .putString(profileKey(name, "secret_access_key"), secretKey)
            .putString(profileKey(name, "region"), region)
            .putString(profileKey(name, "bucket_name"), bucket)
            .putString(KEY_PROFILE_LIST, name)
            .putString(KEY_CURRENT_PROFILE, name)
            // Remove legacy keys
            .remove(LEGACY_KEY_ACCESS_KEY)
            .remove(LEGACY_KEY_SECRET_KEY)
            .remove(LEGACY_KEY_REGION)
            .remove(LEGACY_KEY_BUCKET)
            .apply()
    }

    // --- Profile Management ---

    fun getProfileNames(): List<String> {
        val list = prefs.getString(KEY_PROFILE_LIST, null) ?: return emptyList()
        return list.split(",").filter { it.isNotBlank() }
    }

    fun getCurrentProfileName(): String? {
        return prefs.getString(KEY_CURRENT_PROFILE, null)
    }

    fun setCurrentProfile(name: String) {
        prefs.edit().putString(KEY_CURRENT_PROFILE, name).apply()
        _currentProfileNameFlow.value = name
        _configFlow.value = readProfileConfig(name)
    }

    fun saveProfile(name: String, config: S3Config) {
        val profiles = getProfileNames().toMutableList()
        if (name !in profiles) {
            profiles.add(name)
        }
        prefs.edit()
            .putString(profileKey(name, "access_key_id"), config.accessKeyId)
            .putString(profileKey(name, "secret_access_key"), config.secretAccessKey)
            .putString(profileKey(name, "region"), config.region)
            .putString(profileKey(name, "bucket_name"), config.bucketName)
            .putString(KEY_PROFILE_LIST, profiles.joinToString(","))
            .putString(KEY_CURRENT_PROFILE, name)
            .apply()
        _currentProfileNameFlow.value = name
        _configFlow.value = readProfileConfig(name)
    }

    fun deleteProfile(name: String) {
        val profiles = getProfileNames().toMutableList()
        profiles.remove(name)

        val editor = prefs.edit()
            .remove(profileKey(name, "access_key_id"))
            .remove(profileKey(name, "secret_access_key"))
            .remove(profileKey(name, "region"))
            .remove(profileKey(name, "bucket_name"))

        if (profiles.isEmpty()) {
            editor.remove(KEY_PROFILE_LIST).remove(KEY_CURRENT_PROFILE).apply()
            _currentProfileNameFlow.value = null
            _configFlow.value = null
        } else {
            val newCurrent = profiles.first()
            editor
                .putString(KEY_PROFILE_LIST, profiles.joinToString(","))
                .putString(KEY_CURRENT_PROFILE, newCurrent)
                .apply()
            _currentProfileNameFlow.value = newCurrent
            _configFlow.value = readProfileConfig(newCurrent)
        }
    }

    fun clearAllProfiles() {
        val profiles = getProfileNames()
        val editor = prefs.edit()
        for (p in profiles) {
            editor.remove(profileKey(p, "access_key_id"))
            editor.remove(profileKey(p, "secret_access_key"))
            editor.remove(profileKey(p, "region"))
            editor.remove(profileKey(p, "bucket_name"))
        }
        editor.remove(KEY_PROFILE_LIST).remove(KEY_CURRENT_PROFILE).apply()
        _currentProfileNameFlow.value = null
        _configFlow.value = null
    }

    // --- Config Reading ---

    private fun readProfileConfig(name: String): S3Config? {
        val accessKey = prefs.getString(profileKey(name, "access_key_id"), null) ?: return null
        val secretKey = prefs.getString(profileKey(name, "secret_access_key"), null) ?: return null
        val region = prefs.getString(profileKey(name, "region"), null) ?: return null
        val bucket = prefs.getString(profileKey(name, "bucket_name"), null) ?: return null
        if (accessKey.isBlank() || secretKey.isBlank() || region.isBlank() || bucket.isBlank()) {
            return null
        }
        return S3Config(accessKey, secretKey, region, bucket)
    }

    private fun readCurrentProfileConfig(): S3Config? {
        val name = getCurrentProfileName() ?: return null
        return readProfileConfig(name)
    }

    fun getMaskedAccessKey(profileName: String? = null): String? {
        val name = profileName ?: getCurrentProfileName() ?: return null
        val key = prefs.getString(profileKey(name, "access_key_id"), null) ?: return null
        if (key.length <= 8) return "••••••••"
        return key.take(4) + "••••" + key.takeLast(4)
    }

    fun getProfileConfig(name: String): S3Config? = readProfileConfig(name)

    fun hasCredentials(): Boolean = readCurrentProfileConfig() != null

    // --- App Settings (global, not per-profile) ---

    fun getPageSize(): Int = prefs.getInt(KEY_PAGE_SIZE, DEFAULT_PAGE_SIZE)

    fun savePageSize(size: Int) {
        prefs.edit().putInt(KEY_PAGE_SIZE, size).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM

    fun saveThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }
}
