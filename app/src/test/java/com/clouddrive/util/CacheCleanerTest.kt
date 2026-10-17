package com.clouddrive.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheCleanerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, size: Int, modified: Long): File {
        val f = File(dir, name)
        f.writeBytes(ByteArray(size))
        f.setLastModified(modified)
        return f
    }

    @Test
    fun `trimToSize removes the oldest files first`() {
        val dir = tmp.newFolder("thumbs")
        val oldest = file(dir, "a", 100, 1_000)
        val middle = file(dir, "b", 100, 2_000)
        val newest = file(dir, "c", 100, 3_000)

        CacheCleaner.trimToSize(dir, 200)

        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
    }

    @Test
    fun `trimToSize keeps everything when under the limit`() {
        val dir = tmp.newFolder("thumbs")
        file(dir, "a", 100, 1_000)
        file(dir, "b", 100, 2_000)

        CacheCleaner.trimToSize(dir, 1_000)

        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test
    fun `trimToSize ignores a missing directory`() {
        CacheCleaner.trimToSize(File(tmp.root, "does-not-exist"), 10)
    }

    @Test
    fun `run deletes leftover preview files but not other cache entries`() {
        val cache = tmp.newFolder("cache")
        val audio = file(cache, "audio_preview_123.tmp", 10, 1_000)
        val pdf = file(cache, "pdf_preview_456.pdf", 10, 1_000)
        val video = file(cache, "video_preview_789.tmp", 10, 1_000)
        val other = file(cache, "something_else.bin", 10, 1_000)

        CacheCleaner.run(cache)

        assertFalse(audio.exists())
        assertFalse(pdf.exists())
        assertFalse(video.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `run only removes stale partial downloads`() {
        val cache = tmp.newFolder("cache")
        val thumbs = File(cache, CacheCleaner.THUMBNAIL_DIR).also { it.mkdirs() }
        val stale = file(thumbs, "x" + CacheCleaner.PARTIAL_SUFFIX, 10, 1_000)
        val active = file(thumbs, "y" + CacheCleaner.PARTIAL_SUFFIX, 10, System.currentTimeMillis())

        CacheCleaner.run(cache)

        assertFalse(stale.exists())
        assertTrue(active.exists())
    }
}
