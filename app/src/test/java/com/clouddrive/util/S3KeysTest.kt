package com.clouddrive.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S3KeysTest {

    @Test
    fun `free key is returned unchanged and recorded`() {
        val taken = mutableSetOf<String>()
        assertEquals("galeria/Camera/IMG_1.jpg", S3Keys.uniqueKey("galeria/Camera/IMG_1.jpg", 7, taken))
        assertTrue("galeria/Camera/IMG_1.jpg" in taken)
    }

    @Test
    fun `taken key gets the discriminator before the extension`() {
        val taken = mutableSetOf("galeria/Camera/IMG_1.jpg")
        assertEquals("galeria/Camera/IMG_1_42.jpg", S3Keys.uniqueKey("galeria/Camera/IMG_1.jpg", 42, taken))
    }

    @Test
    fun `name without extension gets the suffix at the end`() {
        val taken = mutableSetOf("a/b/README")
        assertEquals("a/b/README_5", S3Keys.uniqueKey("a/b/README", 5, taken))
    }

    @Test
    fun `leading dot is not treated as an extension`() {
        val taken = mutableSetOf("a/.nomedia")
        assertEquals("a/.nomedia_9", S3Keys.uniqueKey("a/.nomedia", 9, taken))
    }

    @Test
    fun `dot in a folder name is not treated as an extension`() {
        val taken = mutableSetOf("my.photos/IMG")
        assertEquals("my.photos/IMG_3", S3Keys.uniqueKey("my.photos/IMG", 3, taken))
    }

    @Test
    fun `same discriminator twice still yields distinct keys`() {
        val taken = mutableSetOf("a/x.jpg")
        val first = S3Keys.uniqueKey("a/x.jpg", 1, taken)
        val second = S3Keys.uniqueKey("a/x.jpg", 1, taken)
        assertEquals("a/x_1.jpg", first)
        assertEquals("a/x_1_2.jpg", second)
    }

    @Test
    fun `several colliding files never share a key`() {
        val taken = mutableSetOf<String>()
        val keys = (1L..50L).map { S3Keys.uniqueKey("p/Camera/IMG_0001.jpg", it, taken) }
        assertEquals(50, keys.toSet().size)
        assertEquals("p/Camera/IMG_0001.jpg", keys.first())
    }
}
