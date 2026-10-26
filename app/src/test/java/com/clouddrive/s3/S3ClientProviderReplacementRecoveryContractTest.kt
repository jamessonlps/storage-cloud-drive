package com.clouddrive.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Response
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class S3ClientProviderReplacementRecoveryContractTest {

    private val originalFactory = S3ClientProvider.clientFactory
    private val profileA = S3Config("test-key-a", "test-secret-a", "us-east-1", "bucket-a")
    private val profileB = S3Config("test-key-b", "test-secret-b", "eu-west-1", "bucket-b")

    @Before
    fun setUp() {
        S3ClientProvider.close()
    }

    @After
    fun tearDown() {
        try {
            S3ClientProvider.close()
        } finally {
            S3ClientProvider.clientFactory = originalFactory
        }
    }

    @Test
    fun failedReplacementPreservesExceptionAndUsableCachedClient() {
        val clientA = mockk<S3Client>(relaxed = true)
        val failure = IllegalStateException("Replacement creation failed")
        val requests = mutableListOf<S3Config>()
        var closeCount = 0
        every { clientA.close() } answers {
            closeCount++
            Unit
        }
        val response = ListObjectsV2Response {}
        coEvery { clientA.listObjectsV2(any<ListObjectsV2Request>()) } answers {
            assertEquals("A must remain open during use", 0, closeCount)
            response
        }
        S3ClientProvider.clientFactory = { config ->
            requests.add(config)
            if (config == profileA) clientA else throw failure
        }

        assertSame(clientA, S3ClientProvider.getClient(profileA))
        val thrown = runCatching { S3ClientProvider.getClient(profileB) }.exceptionOrNull()
        assertSame(failure, thrown)

        val recovered = S3ClientProvider.getClient(profileA)
        assertSame(clientA, recovered)
        assertEquals(listOf(profileA, profileB), requests)
        assertEquals("Failed replacement must not close A", 0, closeCount)
        runTest {
            val result = recovered.listObjectsV2(ListObjectsV2Request { bucket = profileA.bucketName })
            assertSame(response, result)
        }
        verify(exactly = 0) { clientA.close() }
    }

    @Test
    fun successfulRetryClosesAOnlyAfterCreationAndCachesB() {
        val clientA = mockk<S3Client>(relaxed = true)
        val clientB = mockk<S3Client>(relaxed = true)
        val failure = IllegalStateException("First B attempt failed")
        val events = mutableListOf<String>()
        var attemptsB = 0
        every { clientA.close() } answers {
            events.add("close A")
            Unit
        }
        S3ClientProvider.clientFactory = { config ->
            if (config == profileA) {
                events.add("create A")
                clientA
            } else {
                attemptsB++
                if (attemptsB == 1) {
                    events.add("fail B")
                    throw failure
                }
                events.add("create B")
                clientB
            }
        }

        assertSame(clientA, S3ClientProvider.getClient(profileA))
        assertSame(failure, runCatching { S3ClientProvider.getClient(profileB) }.exceptionOrNull())
        assertSame(clientB, S3ClientProvider.getClient(profileB))
        assertSame(clientB, S3ClientProvider.getClient(profileB))
        assertSame(clientB, S3ClientProvider.getClient(profileB.copy(bucketName = "another-bucket")))
        assertEquals(2, attemptsB)
        assertEquals(listOf("create A", "fail B", "create B", "close A"), events)
        verify(exactly = 1) { clientA.close() }
        verify(exactly = 0) { clientB.close() }

        S3ClientProvider.close()
        S3ClientProvider.close()
        verify(exactly = 1) { clientA.close() }
        verify(exactly = 1) { clientB.close() }
    }

    @Test
    fun bucketChangesReuseClientAndCloseClearsCache() {
        val first = mockk<S3Client>(relaxed = true)
        val second = mockk<S3Client>(relaxed = true)
        val requests = mutableListOf<S3Config>()
        val otherBucket = profileA.copy(bucketName = "other-bucket")
        S3ClientProvider.clientFactory = { config ->
            requests.add(config)
            if (requests.size == 1) first else second
        }

        assertSame(first, S3ClientProvider.getClient(profileA))
        assertSame(first, S3ClientProvider.getClient(profileA))
        assertSame(first, S3ClientProvider.getClient(otherBucket))
        assertEquals(listOf(profileA), requests)
        verify(exactly = 0) { first.close() }

        S3ClientProvider.close()
        S3ClientProvider.close()
        verify(exactly = 1) { first.close() }
        assertSame(second, S3ClientProvider.getClient(otherBucket))
        assertEquals(listOf(profileA, otherBucket), requests)
        verify(exactly = 0) { second.close() }

        S3ClientProvider.close()
        S3ClientProvider.close()
        verify(exactly = 1) { first.close() }
        verify(exactly = 1) { second.close() }
    }
}
