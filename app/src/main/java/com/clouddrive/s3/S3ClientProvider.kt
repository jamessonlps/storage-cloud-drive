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

    private var currentClient: S3Client? = null
    private var currentConfig: S3Config? = null

    fun getClient(config: S3Config): S3Client {
        if (currentConfig == config && currentClient != null) {
            return currentClient!!
        }

        currentClient?.close()

        val client = S3Client {
            region = config.region
            credentialsProvider = StaticCredentialsProvider(
                config.accessKeyId,
                config.secretAccessKey,
            )
        }

        currentClient = client
        currentConfig = config
        return client
    }

    fun close() {
        currentClient?.close()
        currentClient = null
        currentConfig = null
    }
}
