package com.clouddrive.util

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Protected offline contract: trimming completed thumbnails must not delete download staging files. */
class CacheCleanerActivePartTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, size: Int, modified: Long): File {
        val result = File(dir, name)
        result.writeBytes(ByteArray(size) { it.toByte() })
        assertTrue("Fixture modification time must be set", result.setLastModified(modified))
        return result
    }

    @Test
    fun `trim preserves a recent partial older than an oversized completed thumbnail`() {
        val dir = tmp.newFolder("active-and-completed")
        val now = System.currentTimeMillis()
        val partial = file(dir, "active.part", 150, now - 60_000L)
        val completed = file(dir, "completed-thumbnail", 120, now - 30_000L)
        val original = partial.readBytes()

        CacheCleaner.trimToSize(dir, 100L)

        assertTrue("A recent staging file must survive size trimming", partial.isFile)
        assertArrayEquals("Trimming must preserve all staging bytes", original, partial.readBytes())
        assertFalse("Completed thumbnails remain eligible for eviction", completed.exists())
    }

    @Test
    fun `zero quota evicts completed thumbnails while preserving a larger partial`() {
        val dir = tmp.newFolder("zero-quota")
        val now = System.currentTimeMillis()
        val partial = file(dir, "active.part", 11, now - 60_000L)
        val completed = file(dir, "completed-thumbnail", 7, now - 30_000L)
        val original = partial.readBytes()

        CacheCleaner.trimToSize(dir, 0L)

        assertTrue("A quota of zero must not cancel an active download", partial.isFile)
        assertArrayEquals(original, partial.readBytes())
        assertFalse("A quota of zero evicts all completed thumbnails", completed.exists())
    }

    @Test
    fun `trim preserves partials while evicting the oldest completed thumbnail first`() {
        val dir = tmp.newFolder("completed-lru")
        // Stale partial removal belongs to run(), which has the one-hour rule.
        // trimToSize() must never independently remove a staging file.
        val partial = file(dir, "stale.part", 20, 1_000L)
        val oldest = file(dir, "oldest-completed", 60, 2_000L)
        val newest = file(dir, "newest-completed", 60, 3_000L)
        val original = partial.readBytes()

        CacheCleaner.trimToSize(dir, 100L)

        assertTrue("Only age-based cleanup may remove a stale partial", partial.isFile)
        assertArrayEquals(original, partial.readBytes())
        assertFalse("The oldest completed thumbnail must be evicted", oldest.exists())
        assertTrue("The remaining completed thumbnail fits the quota", newest.isFile)
    }
}
