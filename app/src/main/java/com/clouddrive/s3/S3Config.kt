package com.clouddrive.s3

/**
 * Holds the AWS S3 configuration needed to connect to a bucket.
 */
data class S3Config(
    val accessKeyId: String,
    val secretAccessKey: String,
    val region: String,
    val bucketName: String,
)

data class S3Profile(
    val name: String,
    val config: S3Config,
)
