package com.clouddrive.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.crypto.AEADBadTagException

class ChunkedEncryptionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val key = EncryptionManager.generateKey()
    private val chunk = 16

    private fun plain(size: Int) = ByteArray(size) { (it * 7 + 3).toByte() }

    private fun encrypt(data: ByteArray, k: javax.crypto.SecretKey = key): ByteArray =
        ChunkedEncryption.encryptingStream(data.inputStream(), k, chunk).readBytes()

    private fun decrypt(data: ByteArray, k: javax.crypto.SecretKey = key): ByteArray =
        ByteArrayOutputStream().also { ChunkedEncryption.decrypt(data.inputStream(), it, k) }.toByteArray()

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("expected the data to be rejected")
        } catch (_: AEADBadTagException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `roundtrip for sizes around the chunk boundary`() {
        for (size in listOf(0, 1, chunk - 1, chunk, chunk + 1, 3 * chunk, 3 * chunk + 5, 1000)) {
            val data = plain(size)
            assertArrayEquals("size $size", data, decrypt(encrypt(data)))
        }
    }

    @Test
    fun `encryptedSize matches the real output`() {
        for (size in listOf(0, 1, chunk - 1, chunk, chunk + 1, 3 * chunk, 1000)) {
            assertEquals(
                "size $size",
                encrypt(plain(size)).size.toLong(),
                ChunkedEncryption.encryptedSize(size.toLong(), chunk),
            )
        }
    }

    @Test
    fun `reading one byte at a time gives the same stream`() {
        val data = plain(100)
        val stream: InputStream = ChunkedEncryption.encryptingStream(data.inputStream(), key, chunk)
        val out = ByteArrayOutputStream()
        while (true) {
            val b = stream.read()
            if (b == -1) break
            out.write(b)
        }
        assertArrayEquals(data, decrypt(out.toByteArray()))
    }

    @Test
    fun `same plaintext encrypts differently each time`() {
        val data = plain(50)
        assertFalse(encrypt(data).contentEquals(encrypt(data)))
    }

    @Test
    fun `flipped byte is rejected`() {
        val enc = encrypt(plain(100))
        enc[enc.size / 2] = (enc[enc.size / 2].toInt() xor 1).toByte()
        assertRejected { decrypt(enc) }
    }

    @Test
    fun `wrong key is rejected`() {
        val enc = encrypt(plain(100))
        assertRejected { decrypt(enc, EncryptionManager.generateKey()) }
    }

    @Test
    fun `truncation at a chunk boundary is rejected`() {
        val enc = encrypt(plain(4 * chunk))
        val cut = ChunkedEncryption.HEADER_SIZE + 2 * (chunk + 16)
        assertRejected { decrypt(enc.copyOf(cut)) }
    }

    @Test
    fun `swapped chunks are rejected`() {
        val enc = encrypt(plain(4 * chunk))
        val size = chunk + 16
        val start = ChunkedEncryption.HEADER_SIZE
        val swapped = enc.copyOf()
        System.arraycopy(enc, start + size, swapped, start, size)
        System.arraycopy(enc, start, swapped, start + size, size)
        assertRejected { decrypt(swapped) }
    }

    @Test
    fun `tampered header is rejected`() {
        val enc = encrypt(plain(40))
        enc[7] = (enc[7].toInt() xor 1).toByte()
        assertRejected { decrypt(enc) }
    }

    @Test
    fun `garbage and short input are rejected`() {
        assertRejected { decrypt(ByteArray(5)) }
        assertRejected { decrypt(ByteArray(100)) }
    }

    @Test
    fun `decryptFile writes the plaintext`() {
        val data = plain(500)
        val src = tmp.newFile("enc")
        val dst = tmp.newFile("plain")
        src.writeBytes(encrypt(data))
        ChunkedEncryption.decryptFile(src, dst, key)
        assertArrayEquals(data, dst.readBytes())
    }
}
