package com.clouddrive.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileNamesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `plain names are kept`() {
        assertEquals("photo.jpg", FileNames.sanitize("photo.jpg"))
        assertEquals("my file (1).pdf", FileNames.sanitize("my file (1).pdf"))
    }

    @Test
    fun `directory parts are stripped`() {
        assertEquals("passwd", FileNames.sanitize("../../etc/passwd"))
        assertEquals("backup.db", FileNames.sanitize("important/backup.db"))
        assertEquals("win.txt", FileNames.sanitize("C:\\Users\\x\\win.txt"))
    }

    @Test
    fun `unusable names return null`() {
        assertNull(FileNames.sanitize(null))
        assertNull(FileNames.sanitize(""))
        assertNull(FileNames.sanitize("   "))
        assertNull(FileNames.sanitize("."))
        assertNull(FileNames.sanitize(".."))
        assertNull(FileNames.sanitize("folder/.."))
        assertNull(FileNames.sanitize("a/"))
    }

    @Test
    fun `control characters are removed`() {
        assertEquals("ab.txt", FileNames.sanitize("a\u0000b\n.txt"))
    }

    @Test
    fun `resolveInside stays in directory`() {
        val dir = tmp.newFolder("downloads")
        val file = FileNames.resolveInside(dir, "../evil.txt")
        assertEquals(dir.canonicalFile, file.canonicalFile.parentFile)
        assertEquals("evil.txt", file.name)
    }

    @Test
    fun `resolveInside rejects names with nothing left`() {
        val dir = tmp.newFolder("downloads")
        try {
            FileNames.resolveInside(dir, "..")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }
}
