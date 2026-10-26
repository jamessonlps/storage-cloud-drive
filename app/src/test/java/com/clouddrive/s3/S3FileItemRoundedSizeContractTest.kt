package com.clouddrive.s3

import java.text.DecimalFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class S3FileItemRoundedSizeContractTest {

    @Test
    fun promotesRoundedKilobytesToMegabytes() {
        withFormatLocale(Locale.US) {
            val item = S3FileItem(key = "boundary.bin", size = 1_048_575L, lastModified = "")
            assertEquals("1 MB", item.formattedSize)
        }
    }

    @Test
    fun promotesRoundedMegabytesToGigabytes() {
        withFormatLocale(Locale.US) {
            val item = S3FileItem(key = "boundary.bin", size = 1_073_741_823L, lastModified = "")
            assertEquals("1 GB", item.formattedSize)
        }
    }

    @Test
    fun preservesKilobytesImmediatelyBelowRoundingThreshold() {
        withFormatLocale(Locale.US) {
            val size = 1_048_524L
            val expected = DecimalFormat("#,##0.#").format(size / 1024.0) + " KB"
            val item = S3FileItem(key = "control.bin", size = size, lastModified = "")
            assertEquals(expected, item.formattedSize)
        }
    }

    @Test
    fun preservesLocalizedMegabytesImmediatelyBelowRoundingThreshold() {
        withFormatLocale(Locale("pt", "BR")) {
            val size = 1_073_689_395L
            val expected = DecimalFormat("#,##0.#").format(size / (1024.0 * 1024.0)) + " MB"
            val item = S3FileItem(key = "control.bin", size = size, lastModified = "")
            assertEquals(expected, item.formattedSize)
        }
    }

    @Test
    fun preservesOrdinaryFractionalKilobytes() {
        withFormatLocale(Locale.US) {
            val size = 1536L
            val expected = DecimalFormat("#,##0.#").format(size / 1024.0) + " KB"
            val item = S3FileItem(key = "normal.bin", size = size, lastModified = "")
            assertEquals(expected, item.formattedSize)
        }
    }

    private fun withFormatLocale(locale: Locale, action: () -> Unit) {
        val previous = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.Category.FORMAT, locale)
            action()
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous)
        }
    }
}
