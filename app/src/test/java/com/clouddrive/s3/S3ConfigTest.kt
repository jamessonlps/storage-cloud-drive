package com.clouddrive.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class S3ConfigTest {

    @Test
    fun `config stores all fields correctly`() {
        val config = S3Config(
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            bucketName = "my-bucket"
        )
        assertEquals("AKIAIOSFODNN7EXAMPLE", config.accessKeyId)
        assertEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", config.secretAccessKey)
        assertEquals("us-east-1", config.region)
        assertEquals("my-bucket", config.bucketName)
    }

    @Test
    fun `configs with same values are equal`() {
        val config1 = S3Config("key", "secret", "us-east-1", "bucket")
        val config2 = S3Config("key", "secret", "us-east-1", "bucket")
        assertEquals(config1, config2)
    }

    @Test
    fun `configs with different values are not equal`() {
        val config1 = S3Config("key1", "secret", "us-east-1", "bucket")
        val config2 = S3Config("key2", "secret", "us-east-1", "bucket")
        assertNotEquals(config1, config2)
    }

    @Test
    fun `config copy changes only specified fields`() {
        val original = S3Config("key", "secret", "us-east-1", "bucket")
        val modified = original.copy(region = "eu-west-1")
        assertEquals("eu-west-1", modified.region)
        assertEquals("key", modified.accessKeyId)
        assertEquals("secret", modified.secretAccessKey)
        assertEquals("bucket", modified.bucketName)
    }

    @Test
    fun `configs with same values have same hash code`() {
        val config1 = S3Config("key", "secret", "us-east-1", "bucket")
        val config2 = S3Config("key", "secret", "us-east-1", "bucket")
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun `config with different region is not equal`() {
        val config1 = S3Config("key", "secret", "us-east-1", "bucket")
        val config2 = S3Config("key", "secret", "sa-east-1", "bucket")
        assertNotEquals(config1, config2)
    }

    @Test
    fun `config with different bucket is not equal`() {
        val config1 = S3Config("key", "secret", "us-east-1", "bucket-a")
        val config2 = S3Config("key", "secret", "us-east-1", "bucket-b")
        assertNotEquals(config1, config2)
    }
}
