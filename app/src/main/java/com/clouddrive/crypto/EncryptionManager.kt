package com.clouddrive.crypto

import android.util.Base64
import com.clouddrive.s3.SettingsManager
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128
    private const val KEY_SIZE = 256
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(KEY_SIZE)
        return keyGen.generateKey()
    }

    private val secureRandom = SecureRandom()

    /**
     * Synchronized on purpose: uploads run in parallel, and two of them racing to create the
     * first key would each persist a different one. Files encrypted with the overwritten key
     * could never be decrypted again.
     */
    @Synchronized
    fun getOrCreateKey(settingsManager: SettingsManager, profileName: String): SecretKey {
        val existing = settingsManager.getEncryptionKey(profileName)
        if (existing != null) {
            return keyFromBase64(existing)
        }
        val key = generateKey()
        settingsManager.saveEncryptionKey(profileName, keyToBase64(key))
        return key
    }

    fun encrypt(plainBytes: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(IV_SIZE)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        val ciphertext = cipher.doFinal(plainBytes)

        return iv + ciphertext
    }

    fun decrypt(encryptedBytes: ByteArray, key: SecretKey): ByteArray {
        require(encryptedBytes.size >= IV_SIZE + TAG_SIZE / 8) {
            "Dados criptografados invalidos ou corrompidos"
        }
        val iv = encryptedBytes.copyOfRange(0, IV_SIZE)
        val ciphertext = encryptedBytes.copyOfRange(IV_SIZE, encryptedBytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Lazily looks up the profile's key. Unlike [getOrCreateKey] it never creates one: reading
     * must not invent a key that cannot possibly decrypt what is already stored.
     */
    fun keyProviderFor(settingsManager: SettingsManager, profileName: String?): () -> SecretKey? = {
        profileName
            ?.let { settingsManager.getEncryptionKey(it) }
            ?.let { keyFromBase64(it) }
    }

    fun isEnabled(settingsManager: SettingsManager, profileName: String): Boolean {
        return settingsManager.isEncryptionEnabled(profileName)
    }

    fun keyFromBase64(base64: String): SecretKey {
        val decoded = Base64.decode(base64, Base64.DEFAULT)
        return SecretKeySpec(decoded, ALGORITHM)
    }

    fun keyToBase64(key: SecretKey): String {
        return Base64.encodeToString(key.encoded, Base64.DEFAULT)
    }
}
