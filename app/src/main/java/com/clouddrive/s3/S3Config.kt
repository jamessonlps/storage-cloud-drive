package com.clouddrive.s3

/**
 * Holds the AWS S3 configuration needed to connect to a bucket.
 */
data class S3Config(
    val accessKeyId: String,
    val secretAccessKey: String,
    val region: String,
    val bucketName: String,
) {
    /**
     * Never expose credentials through the auto-generated data class `toString()`
     * (it would leak the secret key into logs, crash reports and debugger output).
     */
    override fun toString(): String =
        "S3Config(accessKeyId=${maskAccessKey(accessKeyId)}, secretAccessKey=****, " +
            "region=$region, bucketName=$bucketName)"

    private fun maskAccessKey(key: String): String =
        if (key.length <= 8) "****" else key.take(4) + "****"
}

data class S3Profile(
    val name: String,
    val config: S3Config,
) {
    override fun toString(): String = "S3Profile(name=$name, config=$config)"
}
