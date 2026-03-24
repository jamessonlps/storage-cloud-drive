package com.clouddrive.s3

import aws.sdk.kotlin.services.s3.model.AbortMultipartUploadRequest
import aws.sdk.kotlin.services.s3.model.CompleteMultipartUploadRequest
import aws.sdk.kotlin.services.s3.model.CompletedMultipartUpload
import aws.sdk.kotlin.services.s3.model.CompletedPart
import aws.sdk.kotlin.services.s3.model.CreateMultipartUploadRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Response
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.UploadPartRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.InputStream
import java.text.DecimalFormat

data class S3FileItem(
    val key: String,
    val size: Long,
    val lastModified: String,
    val isFolder: Boolean = false,
) {
    val fileName: String
        get() = key.trimEnd('/').substringAfterLast('/')

    val formattedSize: String
        get() {
            if (isFolder) return "--"
            val units = arrayOf("B", "KB", "MB", "GB")
            var s = size.toDouble()
            var unitIndex = 0
            while (s >= 1024 && unitIndex < units.size - 1) {
                s /= 1024
                unitIndex++
            }
            return DecimalFormat("#,##0.#").format(s) + " " + units[unitIndex]
        }
}

data class PagedResult(
    val items: List<S3FileItem>,
    val nextToken: String?,
)

class S3Repository(private val config: S3Config) {

    private val client get() = S3ClientProvider.getClient(config)
    private val bucket get() = config.bucketName

    suspend fun listFiles(prefix: String = ""): List<S3FileItem> {
        val request = ListObjectsV2Request {
            this.bucket = this@S3Repository.bucket
            this.prefix = prefix
            this.delimiter = "/"
        }

        val response = client.listObjectsV2(request)
        return parseResponse(response, prefix)
    }

    suspend fun listFilesPaged(
        prefix: String = "",
        maxKeys: Int = 100,
        continuationToken: String? = null,
    ): PagedResult {
        val request = ListObjectsV2Request {
            this.bucket = this@S3Repository.bucket
            this.prefix = prefix
            this.delimiter = "/"
            this.maxKeys = maxKeys
            if (continuationToken != null) {
                this.continuationToken = continuationToken
            }
        }

        val response = client.listObjectsV2(request)
        val items = parseResponse(response, prefix)
        val nextToken = if (response.isTruncated == true) response.nextContinuationToken else null
        return PagedResult(items = items, nextToken = nextToken)
    }

    private fun parseResponse(
        response: ListObjectsV2Response,
        prefix: String,
    ): List<S3FileItem> {
        val items = mutableListOf<S3FileItem>()

        // Add folders (common prefixes)
        response.commonPrefixes?.forEach { cp ->
            cp.prefix?.let { p ->
                items.add(
                    S3FileItem(
                        key = p,
                        size = 0,
                        lastModified = "",
                        isFolder = true,
                    )
                )
            }
        }

        // Add files
        response.contents?.forEach { obj ->
            val key = obj.key ?: return@forEach
            // Skip the prefix itself
            if (key == prefix) return@forEach
            items.add(
                S3FileItem(
                    key = key,
                    size = obj.size ?: 0,
                    lastModified = obj.lastModified?.toString() ?: "",
                )
            )
        }

        return items.sortedWith(compareByDescending<S3FileItem> { it.isFolder }.thenBy { it.fileName.lowercase() })
    }

    suspend fun uploadFile(key: String, inputStream: InputStream, contentType: String?) {
        val bytes = inputStream.readBytes()
        val request = PutObjectRequest {
            this.bucket = this@S3Repository.bucket
            this.key = key
            this.contentType = contentType
            this.body = ByteStream.fromBytes(bytes)
        }
        client.putObject(request)
    }

    suspend fun uploadFileWithProgress(
        key: String,
        inputStream: InputStream,
        contentType: String?,
        totalSize: Long,
        metadata: Map<String, String>? = null,
        onProgress: (bytesTransferred: Long) -> Unit,
    ) {
        val bytes = inputStream.readBytes()
        onProgress(bytes.size.toLong())
        val request = PutObjectRequest {
            this.bucket = this@S3Repository.bucket
            this.key = key
            this.contentType = contentType
            this.body = ByteStream.fromBytes(bytes)
            if (metadata != null) this.metadata = metadata
        }
        client.putObject(request)
        onProgress(totalSize)
    }

    suspend fun uploadMultipart(
        key: String,
        inputStream: InputStream,
        contentType: String?,
        totalSize: Long,
        partSizeBytes: Long = 5L * 1024 * 1024,
        metadata: Map<String, String>? = null,
        onPartCompleted: (partNumber: Int, completedPartsCount: Int, bytesTransferred: Long) -> Unit,
    ) {
        val createReq = CreateMultipartUploadRequest {
            this.bucket = this@S3Repository.bucket
            this.key = key
            this.contentType = contentType
            if (metadata != null) this.metadata = metadata
        }
        val createResp = client.createMultipartUpload(createReq)
        val uploadId = createResp.uploadId ?: throw Exception("Falha ao iniciar multipart upload")

        try {
            val completedParts = mutableListOf<CompletedPart>()
            var partNumber = 1
            var totalBytesRead = 0L

            while (totalBytesRead < totalSize) {
                val remaining = totalSize - totalBytesRead
                val chunkSize = minOf(partSizeBytes, remaining).toInt()
                val buffer = ByteArray(chunkSize)

                var bytesReadForChunk = 0
                while (bytesReadForChunk < chunkSize) {
                    val read = inputStream.read(buffer, bytesReadForChunk, chunkSize - bytesReadForChunk)
                    if (read == -1) break
                    bytesReadForChunk += read
                }

                if (bytesReadForChunk == 0) break

                val partData = if (bytesReadForChunk == chunkSize) buffer else buffer.copyOf(bytesReadForChunk)

                val uploadPartReq = UploadPartRequest {
                    this.bucket = this@S3Repository.bucket
                    this.key = key
                    this.uploadId = uploadId
                    this.partNumber = partNumber
                    this.body = ByteStream.fromBytes(partData)
                }
                val partResp = client.uploadPart(uploadPartReq)

                completedParts.add(CompletedPart {
                    this.partNumber = partNumber
                    this.eTag = partResp.eTag
                })

                totalBytesRead += bytesReadForChunk
                onPartCompleted(partNumber, completedParts.size, totalBytesRead)
                partNumber++
            }

            val completeReq = CompleteMultipartUploadRequest {
                this.bucket = this@S3Repository.bucket
                this.key = key
                this.uploadId = uploadId
                this.multipartUpload = CompletedMultipartUpload {
                    this.parts = completedParts.sortedBy { it.partNumber }
                }
            }
            client.completeMultipartUpload(completeReq)
        } catch (e: Exception) {
            try {
                val abortReq = AbortMultipartUploadRequest {
                    this.bucket = this@S3Repository.bucket
                    this.key = key
                    this.uploadId = uploadId
                }
                client.abortMultipartUpload(abortReq)
            } catch (_: Exception) {}
            throw e
        }
    }

    suspend fun downloadFileAsBytes(key: String): ByteArray {
        val request = GetObjectRequest {
            this.bucket = this@S3Repository.bucket
            this.key = key
        }
        var result: ByteArray? = null
        client.getObject(request) { response ->
            result = response.body?.toByteArray()
        }
        return result ?: throw Exception("Arquivo vazio ou nao encontrado")
    }

    suspend fun downloadFile(key: String, destinationFile: File) {
        val request = GetObjectRequest {
            this.bucket = this@S3Repository.bucket
            this.key = key
        }
        client.getObject(request) { response ->
            val bytes = response.body?.toByteArray() ?: return@getObject
            destinationFile.parentFile?.mkdirs()
            destinationFile.writeBytes(bytes)
        }
    }

    suspend fun downloadFileBytes(key: String): ByteArray {
        val request = GetObjectRequest {
            this.bucket = this@S3Repository.bucket
            this.key = key
        }
        var result = byteArrayOf()
        client.getObject(request) { response ->
            result = response.body?.toByteArray() ?: byteArrayOf()
        }
        return result
    }

    suspend fun headObject(key: String): Map<String, String> {
        val request = HeadObjectRequest {
            this.bucket = this@S3Repository.bucket
            this.key = key
        }
        val response = client.headObject(request)
        return response.metadata ?: emptyMap()
    }

    suspend fun listAllKeys(prefix: String = ""): List<S3FileItem> {
        val allItems = mutableListOf<S3FileItem>()
        var token: String? = null
        do {
            val result = listFilesPaged(prefix, maxKeys = 1000, continuationToken = token)
            allItems.addAll(result.items)
            token = result.nextToken
        } while (token != null)
        return allItems
    }

    suspend fun deleteFile(key: String) {
        val request = DeleteObjectRequest {
            this.bucket = this@S3Repository.bucket
            this.key = key
        }
        client.deleteObject(request)
    }

    suspend fun deleteFiles(keys: List<String>) {
        coroutineScope {
            keys.map { key -> async { deleteFile(key) } }.awaitAll()
        }
    }

    suspend fun createFolder(prefix: String) {
        val folderKey = if (prefix.endsWith("/")) prefix else "$prefix/"
        val request = PutObjectRequest {
            this.bucket = this@S3Repository.bucket
            this.key = folderKey
            this.body = ByteStream.fromBytes(byteArrayOf())
        }
        client.putObject(request)
    }
}
