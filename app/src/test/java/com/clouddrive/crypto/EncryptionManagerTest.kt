package com.clouddrive.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

class EncryptionManagerTest {

    @Test
    fun `encrypt then decrypt returns original bytes`() {
        val key = EncryptionManager.generateKey()
        val plain = ByteArray(10_000) { (it % 251).toByte() }

        val decrypted = EncryptionManager.decrypt(EncryptionManager.encrypt(plain, key), key)

        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun `same plaintext encrypts differently each time`() {
        val key = EncryptionManager.generateKey()
        val plain = "hello".toByteArray()

        val a = EncryptionManager.encrypt(plain, key)
        val b = EncryptionManager.encrypt(plain, key)

        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val key = EncryptionManager.generateKey()
        val encrypted = EncryptionManager.encrypt("secret".toByteArray(), key)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0x01).toByte()

        try {
            EncryptionManager.decrypt(encrypted, key)
            fail("expected AEADBadTagException")
        } catch (_: AEADBadTagException) {
        }
    }

    @Test
    fun `wrong key is rejected`() {
        val encrypted = EncryptionManager.encrypt("secret".toByteArray(), EncryptionManager.generateKey())

        try {
            EncryptionManager.decrypt(encrypted, EncryptionManager.generateKey())
            fail("expected AEADBadTagException")
        } catch (_: AEADBadTagException) {
        }
    }

    @Test
    fun `truncated input fails with a clear error`() {
        val key = EncryptionManager.generateKey()
        try {
            EncryptionManager.decrypt(ByteArray(5), key)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Dados criptografados invalidos ou corrompidos", e.message)
        }
    }
}
