package com.currency.converter.utils

import java.util.concurrent.TimeUnit

/**
 * hit api after timeout
 */
class RateLimiter(timeout: Int, timeUnit: TimeUnit) {

    private val timeout = timeUnit.toMillis(timeout.toLong())

    @Synchronized
    fun shouldFetch(lastFetched: Long?): Boolean {
        val now = now()
        if (lastFetched == null) {
            return true
        }
        val diff = now - lastFetched
        if (diff > timeout) {
            return true
        }
        return false
    }

    private fun now() = System.currentTimeMillis()

}