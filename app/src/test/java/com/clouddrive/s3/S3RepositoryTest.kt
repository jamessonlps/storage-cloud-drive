package com.clouddrive.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CommonPrefix
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectResponse
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Response
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectResponse
import aws.sdk.kotlin.services.s3.model.S3Object
import aws.smithy.kotlin.runtime.time.Instant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class S3RepositoryTest {

    private val config = S3Config("key", "secret", "us-east-1", "test-bucket")
    private lateinit var mockClient: S3Client
    private lateinit var repository: S3Repository

    @Before
    fun setUp() {
        mockClient = mockk(relaxed = true)
        mockkObject(S3ClientProvider)
        every { S3ClientProvider.getClient(config) } returns mockClient
        repository = S3Repository(config)
    }

    @After
    fun tearDown() {
        unmockkObject(S3ClientProvider)
    }

    @Test
    fun `listFiles returns folders from common prefixes`() = runTest {
        coEvery { mockClient.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
            commonPrefixes = listOf(
                CommonPrefix { prefix = "documents/" },
                CommonPrefix { prefix = "images/" }
            )
            contents = emptyList()
        }

        val result = repository.listFiles()

        assertEquals(2, result.size)
        assertTrue(result[0].isFolder)
        assertTrue(result[1].isFolder)
        assertEquals("documents/", result[0].key)
        assertEquals("images/", result[1].key)
    }

    @Test
    fun `listFiles returns files from contents`() = runTest {
        coEvery { mockClient.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
            commonPrefixes = emptyList()
            contents = listOf(
                S3Object {
                    key = "file1.txt"
                    size = 1024
                    lastModified = Instant.fromEpochSeconds(1704067200)
                },
                S3Object {
                    key = "file2.pdf"
                    size = 2048
                    lastModified = Instant.fromEpochSeconds(1704067200)
                }
            )
        }

        val result = repository.listFiles()

        assertEquals(2, result.size)
        assertFalse(result[0].isFolder)
        assertFalse(result[1].isFolder)
    }

    @Test
    fun `listFiles skips prefix itself from contents`() = runTest {
        coEvery { mockClient.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
            commonPrefixes = emptyList()
            contents = listOf(
                S3Object {
                    key = "documents/"
                    size = 0
                    lastModified = Instant.fromEpochSeconds(1704067200)
                },
                S3Object {
                    key = "documents/file.txt"
                    size = 100
                    lastModified = Instant.fromEpochSeconds(1704067200)
                }
            )
        }

        val result = repository.listFiles("documents/")

        assertEquals(1, result.size)
        assertEquals("documents/file.txt", result[0].key)
    }

    @Test
    fun `listFiles sorts folders first then files alphabetically`() = runTest {
        coEvery { mockClient.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
            commonPrefixes = listOf(
                CommonPrefix { prefix = "zebra/" }
            )
            contents = listOf(
                S3Object {
                    key = "alpha.txt"
                    size = 100
                    lastModified = Instant.fromEpochSeconds(1704067200)
                },
                S3Object {
                    key = "beta.txt"
                    size = 200
                    lastModified = Instant.fromEpochSeconds(1704067200)
                }
            )
        }

        val result = repository.listFiles()

        assertEquals(3, result.size)
        assertTrue(result[0].isFolder)
        assertEquals("zebra/", result[0].key)
        assertEquals("alpha.txt", result[1].key)
        assertEquals("beta.txt", result[2].key)
    }

    @Test
    fun `listFiles returns empty list when bucket is empty`() = runTest {
        coEvery { mockClient.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
            commonPrefixes = null
            contents = null
        }

        val result = repository.listFiles()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `uploadFile calls putObject on client`() = runTest {
        coEvery { mockClient.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

        val inputStream = "test content".byteInputStream()
        repository.uploadFile("test/file.txt", inputStream, "text/plain")

        coVerify { mockClient.putObject(any<PutObjectRequest>()) }
    }

    @Test
    fun `deleteFile calls deleteObject on client`() = runTest {
        coEvery { mockClient.deleteObject(any<DeleteObjectRequest>()) } returns DeleteObjectResponse {}

        repository.deleteFile("test/file.txt")

        coVerify { mockClient.deleteObject(any<DeleteObjectRequest>()) }
    }

    @Test
    fun `createFolder appends slash if missing`() = runTest {
        coEvery { mockClient.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

        repository.createFolder("new-folder")

        coVerify {
            mockClient.putObject(match<PutObjectRequest> { it.key == "new-folder/" })
        }
    }

    @Test
    fun `createFolder keeps slash if already present`() = runTest {
        coEvery { mockClient.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

        repository.createFolder("new-folder/")

        coVerify {
            mockClient.putObject(match<PutObjectRequest> { it.key == "new-folder/" })
        }
    }

    @Test
    fun `listFiles handles files with null key`() = runTest {
        coEvery { mockClient.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
            commonPrefixes = emptyList()
            contents = listOf(
                S3Object {
                    key = null
                    size = 100
                    lastModified = Instant.fromEpochSeconds(1704067200)
                },
                S3Object {
                    key = "valid-file.txt"
                    size = 200
                    lastModified = Instant.fromEpochSeconds(1704067200)
                }
            )
        }

        val result = repository.listFiles()

        assertEquals(1, result.size)
        assertEquals("valid-file.txt", result[0].key)
    }

    @Test
    fun `listFiles uses default size 0 when size is null`() = runTest {
        coEvery { mockClient.listObjectsV2(any<ListObjectsV2Request>()) } returns ListObjectsV2Response {
            commonPrefixes = emptyList()
            contents = listOf(
                S3Object {
                    key = "file.txt"
                    size = null
                    lastModified = Instant.fromEpochSeconds(1704067200)
                }
            )
        }

        val result = repository.listFiles()

        assertEquals(1, result.size)
        assertEquals(0L, result[0].size)
    }
}
