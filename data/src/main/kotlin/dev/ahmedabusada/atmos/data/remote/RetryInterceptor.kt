package dev.ahmedabusada.atmos.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries a request after a network error, an HTTP 5xx or an HTTP 429, with exponential back-off
 * (API_CONTRACT §4): wait [baseDelayMillis], then twice that, and so on. For 429 "Too Many
 * Requests" it waits the server's `Retry-After` instead, when given and short (ADR-0008).
 *
 * Every other HTTP 4xx is **not** retried: it means our request is wrong, and sending it again
 * will not help.
 */
internal class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMillis: Long = 1_000,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        while (true) {
            val backOff = baseDelayMillis shl attempt
            val response = try {
                chain.proceed(chain.request())
            } catch (exception: IOException) {
                if (attempt >= maxRetries || chain.call().isCanceled()) throw exception
                null
            }
            val wait = if (response == null) backOff else retryDelayMillis(response, backOff)
            if (response != null && (wait == null || attempt >= maxRetries)) return response

            response?.close()
            sleep(wait ?: backOff)
            attempt++
        }
    }

    companion object {
        private const val SERVER_ERROR = 500
        private const val TOO_MANY_REQUESTS = 429
        private const val MILLIS_PER_SECOND = 1_000L

        /** The longest `Retry-After` the app waits for: longer, and the user hears "try later" now. */
        const val MAX_RETRY_AFTER_MILLIS = 10_000L

        /**
         * How long to wait before retrying [response], or null when it must not be retried:
         * HTTP 5xx → [backOff]; HTTP 429 → `Retry-After` seconds if given and at most 10 s
         * (longer: null), or [backOff] without the header or with a date; anything else → null.
         */
        fun retryDelayMillis(response: Response, backOff: Long): Long? {
            if (response.code >= SERVER_ERROR) return backOff
            if (response.code != TOO_MANY_REQUESTS) return null
            val header = response.header("Retry-After")?.trim().orEmpty()
            if (header.isEmpty() || header.any { it !in '0'..'9' }) return backOff
            val millis = header.toLongOrNull()?.let { it * MILLIS_PER_SECOND } ?: return null
            return millis.takeIf { it <= MAX_RETRY_AFTER_MILLIS }
        }
    }
}
