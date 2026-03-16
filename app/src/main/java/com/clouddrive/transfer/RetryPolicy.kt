package com.clouddrive.transfer

import java.io.IOException

object RetryPolicy {
    private const val MAX_RETRIES = 5
    private const val BASE_DELAY_MS = 1000L

    fun shouldRetry(exception: Exception, currentRetry: Int): Boolean {
        if (currentRetry >= MAX_RETRIES) return false
        return exception is IOException ||
            exception.message?.contains("timeout", ignoreCase = true) == true ||
            exception.message?.contains("connection", ignoreCase = true) == true ||
            exception.message?.contains("reset", ignoreCase = true) == true ||
            exception.message?.contains("broken pipe", ignoreCase = true) == true
    }

    fun delayMs(retryCount: Int): Long {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s
        return BASE_DELAY_MS * (1L shl retryCount.coerceAtMost(4))
    }
}
