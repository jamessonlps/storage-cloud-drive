package com.clouddrive.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S3FileItemTest {

    @Test
    fun `fileName extracts name from simple key`() {
        val item = S3FileItem(key = "photo.jpg", size = 100, lastModified = "")
        assertEquals("photo.jpg", item.fileName)
    }

    @Test
    fun `fileName extracts name from nested key`() {
        val item = S3FileItem(key = "documents/reports/annual.pdf", size = 100, lastModified = "")
        assertEquals("annual.pdf", item.fileName)
    }

    @Test
    fun `fileName extracts folder name from key ending with slash`() {
        val item = S3FileItem(key = "documents/reports/", size = 0, lastModified = "", isFolder = true)
        assertEquals("reports", item.fileName)
    }

    @Test
    fun `fileName handles single segment key`() {
        val item = S3FileItem(key = "file.txt", size = 50, lastModified = "")
        assertEquals("file.txt", item.fileName)
    }

    @Test
    fun `formattedSize returns double dash for folders`() {
        val item = S3FileItem(key = "folder/", size = 0, lastModified = "", isFolder = true)
        assertEquals("--", item.formattedSize)
    }

    @Test
    fun `formattedSize formats bytes correctly`() {
        val item = S3FileItem(key = "small.txt", size = 500, lastModified = "")
        assertEquals("500 B", item.formattedSize)
    }

    @Test
    fun `formattedSize formats zero bytes`() {
        val item = S3FileItem(key = "empty.txt", size = 0, lastModified = "")
        assertEquals("0 B", item.formattedSize)
    }

    @Test
    fun `formattedSize formats kilobytes correctly`() {
        val item = S3FileItem(key = "doc.txt", size = 1024, lastModified = "")
        assertEquals("1 KB", item.formattedSize)
    }

    @Test
    fun `formattedSize formats megabytes correctly`() {
        val item = S3FileItem(key = "image.png", size = 1024 * 1024, lastModified = "")
        assertEquals("1 MB", item.formattedSize)
    }

    @Test
    fun `formattedSize formats gigabytes correctly`() {
        val item = S3FileItem(key = "video.mp4", size = 1024L * 1024 * 1024, lastModified = "")
        assertEquals("1 GB", item.formattedSize)
    }

    @Test
    fun `formattedSize formats fractional kilobytes`() {
        val item = S3FileItem(key = "file.txt", size = 1536, lastModified = "")
        assertEquals("1.5 KB", item.formattedSize)
    }

    @Test
    fun `formattedSize formats large gigabyte values without overflow`() {
        val item = S3FileItem(key = "huge.iso", size = 5L * 1024 * 1024 * 1024, lastModified = "")
        assertEquals("5 GB", item.formattedSize)
    }

    @Test
    fun `formattedSize caps at GB unit`() {
        // 1 TB = 1024 GB, should show as 1,024 GB (not TB since units stop at GB)
        val item = S3FileItem(key = "massive.dat", size = 1024L * 1024 * 1024 * 1024, lastModified = "")
        assertEquals("1,024 GB", item.formattedSize)
    }

    @Test
    fun `isFolder defaults to false`() {
        val item = S3FileItem(key = "file.txt", size = 100, lastModified = "")
        assertFalse(item.isFolder)
    }

    @Test
    fun `isFolder can be set to true`() {
        val item = S3FileItem(key = "folder/", size = 0, lastModified = "", isFolder = true)
        assertTrue(item.isFolder)
    }

    @Test
    fun `data class equality works correctly`() {
        val item1 = S3FileItem(key = "file.txt", size = 100, lastModified = "2024-01-01")
        val item2 = S3FileItem(key = "file.txt", size = 100, lastModified = "2024-01-01")
        assertEquals(item1, item2)
    }

    @Test
    fun `data class copy works correctly`() {
        val original = S3FileItem(key = "file.txt", size = 100, lastModified = "2024-01-01")
        val copy = original.copy(size = 200)
        assertEquals(200L, copy.size)
        assertEquals("file.txt", copy.key)
    }
}
