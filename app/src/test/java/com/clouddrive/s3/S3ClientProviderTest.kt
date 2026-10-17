package com.clouddrive.s3

import aws.sdk.kotlin.services.s3.S3Client
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Uses a fake client factory: the real AWS client cannot be created in a local unit test of an
 * Android module (see [S3ClientProvider.clientFactory]).
 */
class S3ClientProviderTest {

    private val created = mutableListOf<S3Client>()
    private val originalFactory = S3ClientProvider.clientFactory

    @Before
    fun setUp() {
        S3ClientProvider.close()
        created.clear()
        S3ClientProvider.clientFactory = { mockk<S3Client>(relaxed = true).also { created.add(it) } }
    }

    @After
    fun tearDown() {
        S3ClientProvider.close()
        S3ClientProvider.clientFactory = originalFactory
    }

    @Test
    fun `getClient returns non-null client`() {
        val client = S3ClientProvider.getClient(S3Config("key", "secret", "us-east-1", "bucket"))
        assertNotNull(client)
    }

    @Test
    fun `getClient returns same client for same config`() {
        val config = S3Config("key", "secret", "us-east-1", "bucket")
        val client1 = S3ClientProvider.getClient(config)
        val client2 = S3ClientProvider.getClient(config)
        assertSame(client1, client2)
        assertEquals(1, created.size)
    }

    @Test
    fun `getClient reuses client when only the bucket changes`() {
        val client1 = S3ClientProvider.getClient(S3Config("key", "secret", "us-east-1", "bucket-a"))
        val client2 = S3ClientProvider.getClient(S3Config("key", "secret", "us-east-1", "bucket-b"))
        assertSame(client1, client2)
        assertEquals(1, created.size)
        verify(exactly = 0) { client1.close() }
    }

    @Test
    fun `getClient returns new client and closes the old one when credentials or region change`() {
        val client1 = S3ClientProvider.getClient(S3Config("key1", "secret1", "us-east-1", "bucket"))
        val client2 = S3ClientProvider.getClient(S3Config("key2", "secret2", "us-east-1", "bucket"))
        val client3 = S3ClientProvider.getClient(S3Config("key2", "secret2", "eu-west-1", "bucket"))

        assertNotSame(client1, client2)
        assertNotSame(client2, client3)
        assertEquals(3, created.size)
        verify(exactly = 1) { client1.close() }
        verify(exactly = 1) { client2.close() }
    }

    @Test
    fun `close resets client state`() {
        val config = S3Config("key", "secret", "us-east-1", "bucket")
        val first = S3ClientProvider.getClient(config)
        S3ClientProvider.close()
        val second = S3ClientProvider.getClient(config)

        assertNotSame(first, second)
        assertEquals(2, created.size)
        verify(exactly = 1) { first.close() }
    }
}
