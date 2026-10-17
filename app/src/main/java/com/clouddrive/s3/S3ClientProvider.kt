package com.clouddrive.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes

class StaticCredentialsProvider(
    private val accessKeyId: String,
    private val secretAccessKey: String,
) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials {
        return Credentials(accessKeyId, secretAccessKey)
    }
}

object S3ClientProvider {

    /**
     * The client does not depend on the bucket (every request carries its own bucket name), so
     * the cache is keyed only by credentials + region. Keying on the whole [S3Config] used to
     * close the shared client whenever another bucket was used, breaking transfers still
     * running against the previous one.
     */
    private data class ClientKey(
        val accessKeyId: String,
        val secretAccessKey: String,
        val region: String,
    ) {
        override fun toString(): String = "ClientKey(region=$region)"
    }

    private var currentClient: S3Client? = null
    private var currentKey: ClientKey? = null

    private fun createClient(config: S3Config): S3Client = S3Client {
        region = config.region
        credentialsProvider = StaticCredentialsProvider(
            config.accessKeyId,
            config.secretAccessKey,
        )
    }

    /**
     * Builds the real client. Replaceable only so unit tests can run: the AWS SDK cannot start in
     * a local JVM test of an Android module (it reads Build.VERSION fields that are null in the
     * stub android.jar and fails with a NullPointerException).
     */
    internal var clientFactory: (S3Config) -> S3Client = { config -> createClient(config) }

    @Synchronized
    fun getClient(config: S3Config): S3Client {
        val key = ClientKey(config.accessKeyId, config.secretAccessKey, config.region)
        currentClient?.let { existing ->
            if (currentKey == key) return existing
        }

        currentClient?.close()

        val client = clientFactory(config)

        currentClient = client
        currentKey = key
        return client
    }

    @Synchronized
    fun close() {
        currentClient?.close()
        currentClient = null
        currentKey = null
    }
}
