package com.clouddrive.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChunkedEncryptedSizeValidationTest {

    @Test
    fun `zero chunk is rejected before division`() {
        for (plainSize in listOf(0L, 1L, 32L)) {
            assertThrows(IllegalArgumentException::class.java) {
                ChunkedEncryption.encryptedSize(plainSize, 0)
            }
        }
    }

    @Test
    fun `negative chunks are rejected`() {
        for (chunkSize in listOf(-1, Int.MIN_VALUE)) {
            for (plainSize in listOf(0L, 32L)) {
                assertThrows(IllegalArgumentException::class.java) {
                    ChunkedEncryption.encryptedSize(plainSize, chunkSize)
                }
            }
        }
    }

    @Test
    fun `chunks above sixteen MiB are rejected`() {
        for (chunkSize in listOf(16_777_217, Int.MAX_VALUE)) {
            for (plainSize in listOf(0L, 32L)) {
                assertThrows(IllegalArgumentException::class.java) {
                    ChunkedEncryption.encryptedSize(plainSize, chunkSize)
                }
            }
        }
    }

    @Test
    fun `valid chunk sizes preserve empty and boundary calculations`() {
        val cases = listOf(
            Triple(0L, 1, 31L),
            Triple(1L, 1, 32L),
            Triple(2L, 1, 49L),
            Triple(0L, 16, 31L),
            Triple(15L, 16, 46L),
            Triple(16L, 16, 47L),
            Triple(17L, 16, 64L),
            Triple(48L, 16, 111L),
            Triple(0L, 16_777_216, 31L),
            Triple(1L, 16_777_216, 32L),
            Triple(16_777_215L, 16_777_216, 16_777_246L),
            Triple(16_777_216L, 16_777_216, 16_777_247L),
            Triple(16_777_217L, 16_777_216, 16_777_264L),
            Triple(33_554_432L, 16_777_216, 33_554_479L),
        )
        for ((plainSize, chunkSize, expected) in cases) {
            assertEquals(
                "plainSize=$plainSize, chunkSize=$chunkSize",
                expected,
                ChunkedEncryption.encryptedSize(plainSize, chunkSize),
            )
        }
        assertEquals(31L, ChunkedEncryption.encryptedSize(0L))
        assertEquals(65_567L, ChunkedEncryption.encryptedSize(65_536L))
        assertEquals(65_584L, ChunkedEncryption.encryptedSize(65_537L))
    }

    @Test
    fun `negative plaintext size remains rejected for valid chunks`() {
        for (plainSize in listOf(-1L, Long.MIN_VALUE)) {
            for (chunkSize in listOf(1, ChunkedEncryption.DEFAULT_CHUNK_SIZE, 16_777_216)) {
                assertThrows(IllegalArgumentException::class.java) {
                    ChunkedEncryption.encryptedSize(plainSize, chunkSize)
                }
            }
        }
    }
}
