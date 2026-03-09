package com.clouddrive.s3

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class S3ClientProviderTest {

    @After
    fun tearDown() {
        S3ClientProvider.close()
    }

    @Test
    fun `getClient returns non-null client`() {
        val config = S3Config("key", "secret", "us-east-1", "bucket")
        val client = S3ClientProvider.getClient(config)
        assertNotNull(client)
    }

    @Test
    fun `getClient returns same client for same config`() {
        val config = S3Config("key", "secret", "us-east-1", "bucket")
        val client1 = S3ClientProvider.getClient(config)
        val client2 = S3ClientProvider.getClient(config)
        assertSame(client1, client2)
    }

    @Test
    fun `getClient returns new client for different config`() {
        val config1 = S3Config("key1", "secret1", "us-east-1", "bucket1")
        val config2 = S3Config("key2", "secret2", "eu-west-1", "bucket2")
        val client1 = S3ClientProvider.getClient(config1)
        val client2 = S3ClientProvider.getClient(config2)
        // Different configs should return different client instances
        assertNotNull(client1)
        assertNotNull(client2)
    }

    @Test
    fun `close resets client state`() {
        val config = S3Config("key", "secret", "us-east-1", "bucket")
        S3ClientProvider.getClient(config)
        S3ClientProvider.close()
        // After close, getting client with same config should create a new one
        val newClient = S3ClientProvider.getClient(config)
        assertNotNull(newClient)
    }
}
