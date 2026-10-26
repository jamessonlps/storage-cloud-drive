package com.clouddrive.crypto

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Streaming authenticated encryption (format "v2"), so big files never need to fit in memory.
 *
 * Layout: `MAGIC(4) | chunkSize(4, big endian) | noncePrefix(7)` followed by chunks. Every chunk is
 * the AES-256-GCM encryption of up to `chunkSize` plaintext bytes (+16 byte tag). All chunks are
 * full except the last one, which is always present (an empty file is a single empty chunk).
 *
 * Nonce of a chunk: `noncePrefix(7) | index(4, big endian) | last(1)`. The header is bound to every
 * chunk as associated data. Together this makes reordering, dropping, duplicating or truncating
 * chunks fail the tag check, not only flipping bytes inside one.
 *
 * Objects written by [EncryptionManager.encrypt] (format v1, a single GCM block) keep working: the
 * object metadata tells which format to use, see [METADATA_KEY].
 */
object ChunkedEncryption {
    /** Object metadata key holding the format version. Absent means the legacy single-block format. */
    const val METADATA_KEY = "enc-version"
    const val METADATA_VALUE = "2"

    const val DEFAULT_CHUNK_SIZE = 64 * 1024
    private const val MAX_CHUNK_SIZE = 16 * 1024 * 1024

    private val MAGIC = byteArrayOf('C'.code.toByte(), 'D'.code.toByte(), 'E'.code.toByte(), 2)
    private const val PREFIX_SIZE = 7
    private const val TAG_BYTES = 16
    private const val TAG_BITS = TAG_BYTES * 8
    const val HEADER_SIZE = 4 + 4 + PREFIX_SIZE
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val secureRandom = SecureRandom()

    /** Size of the encrypted output for [plainSize] plaintext bytes (needed up front by multipart upload). */
    fun encryptedSize(plainSize: Long, chunkSize: Int = DEFAULT_CHUNK_SIZE): Long {
        require(plainSize >= 0) { "plainSize negativo" }
        require(chunkSize in 1..MAX_CHUNK_SIZE) { "chunkSize invalido" }
        val chunks = maxOf(1L, (plainSize + chunkSize - 1) / chunkSize)
        return HEADER_SIZE + plainSize + chunks * TAG_BYTES
    }

    fun encryptingStream(
        source: InputStream,
        key: SecretKey,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
    ): InputStream = EncryptingInputStream(source, key, chunkSize)

    /** Decrypts [input] into [output]. Throws if any chunk was altered, reordered or cut off. */
    fun decrypt(input: InputStream, output: OutputStream, key: SecretKey) {
        val header = ByteArray(HEADER_SIZE)
        readFully(input, header, 0, HEADER_SIZE).let {
            if (it != HEADER_SIZE) throw IllegalArgumentException(CORRUPT)
        }
        for (i in MAGIC.indices) {
            if (header[i] != MAGIC[i]) throw IllegalArgumentException(CORRUPT)
        }
        val chunkSize = readInt(header, 4)
        if (chunkSize < 1 || chunkSize > MAX_CHUNK_SIZE) throw IllegalArgumentException(CORRUPT)
        val prefix = header.copyOfRange(8, HEADER_SIZE)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val block = ByteArray(chunkSize + TAG_BYTES)
        var carry = -1
        var index = 0
        while (true) {
            var n = 0
            if (carry >= 0) {
                block[0] = carry.toByte()
                n = 1
                carry = -1
            }
            n += readFully(input, block, n, block.size - n)
            val last: Boolean
            if (n < block.size) {
                last = true
            } else {
                val next = input.read()
                last = next == -1
                carry = next
            }
            if (n < TAG_BYTES) throw IllegalArgumentException(CORRUPT)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, index, last)))
            cipher.updateAAD(header)
            output.write(cipher.doFinal(block, 0, n))
            if (last) return
            index++
            if (index < 0) throw IllegalArgumentException(CORRUPT)
        }
    }

    fun decryptFile(source: File, destination: File, key: SecretKey) {
        source.inputStream().buffered().use { input ->
            destination.outputStream().buffered().use { output ->
                decrypt(input, output, key)
            }
        }
    }

    fun decryptBytes(encrypted: ByteArray, key: SecretKey): ByteArray {
        val out = java.io.ByteArrayOutputStream(maxOf(0, encrypted.size - HEADER_SIZE))
        decrypt(encrypted.inputStream(), out, key)
        return out.toByteArray()
    }

    private const val CORRUPT = "Dados criptografados invalidos ou corrompidos"

    private fun nonce(prefix: ByteArray, index: Int, last: Boolean): ByteArray {
        val nonce = ByteArray(12)
        System.arraycopy(prefix, 0, nonce, 0, PREFIX_SIZE)
        nonce[7] = (index ushr 24).toByte()
        nonce[8] = (index ushr 16).toByte()
        nonce[9] = (index ushr 8).toByte()
        nonce[10] = index.toByte()
        nonce[11] = if (last) 1 else 0
        return nonce
    }

    private fun readInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

    /** Reads until [len] bytes or end of stream; returns how many were read. */
    private fun readFully(input: InputStream, buf: ByteArray, off: Int, len: Int): Int {
        var total = 0
        while (total < len) {
            val read = input.read(buf, off + total, len - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    private class EncryptingInputStream(
        source: InputStream,
        private val key: SecretKey,
        private val chunkSize: Int,
    ) : FilterInputStream(source) {
        private val cipher = Cipher.getInstance(TRANSFORMATION)
        private val header = ByteArray(HEADER_SIZE)
        private val prefix = ByteArray(PREFIX_SIZE)
        private val plain = ByteArray(chunkSize)

        private var pending: ByteArray
        private var pendingPos = 0
        private var carry = -1
        private var index = 0
        private var finished = false

        init {
            require(chunkSize in 1..MAX_CHUNK_SIZE) { "chunkSize invalido" }
            secureRandom.nextBytes(prefix)
            System.arraycopy(MAGIC, 0, header, 0, 4)
            header[4] = (chunkSize ushr 24).toByte()
            header[5] = (chunkSize ushr 16).toByte()
            header[6] = (chunkSize ushr 8).toByte()
            header[7] = chunkSize.toByte()
            System.arraycopy(prefix, 0, header, 8, PREFIX_SIZE)
            pending = header.copyOf()
        }

        /** Makes [pending] hold the next encrypted chunk. Returns false when everything was produced. */
        private fun fill(): Boolean {
            if (finished) return false
            var n = 0
            if (carry >= 0) {
                plain[0] = carry.toByte()
                n = 1
                carry = -1
            }
            n += readFully(`in`, plain, n, chunkSize - n)
            val last: Boolean
            if (n < chunkSize) {
                last = true
            } else {
                val next = `in`.read()
                last = next == -1
                carry = next
            }
            if (index < 0) throw IOException("Arquivo grande demais para o formato de criptografia")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, index, last)))
            cipher.updateAAD(header)
            pending = cipher.doFinal(plain, 0, n)
            pendingPos = 0
            index++
            if (last) finished = true
            return true
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pendingPos >= pending.size && !fill()) return -1
            val count = minOf(len, pending.size - pendingPos)
            System.arraycopy(pending, pendingPos, b, off, count)
            pendingPos += count
            return count
        }

        override fun skip(n: Long): Long = throw UnsupportedOperationException()

        override fun available(): Int = pending.size - pendingPos

        override fun markSupported(): Boolean = false

        override fun mark(readlimit: Int) {}

        override fun reset() = throw IOException("mark/reset nao suportado")
    }
}
