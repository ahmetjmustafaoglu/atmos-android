package dev.ahmedabusada.atmos.data.remote

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {
    private val server = MockWebServer()
    private val delays = mutableListOf<Long>()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun call(): Int {
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor(RetryInterceptor(maxRetries = 2, baseDelayMillis = 1_000, sleep = { delays += it }))
            .build()
        return client.newCall(Request(server.url("/"))).execute().use { it.code }
    }

    @Test
    fun `back-off doubles between attempts`() {
        server.enqueue(MockResponse(code = 500))
        server.enqueue(MockResponse(code = 500))
        server.enqueue(MockResponse(code = 200))

        assertEquals(200, call())
        assertEquals(listOf(1_000L, 2_000L), delays)
    }

    @Test
    fun `a network failure is retried`() {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse(code = 200))

        assertEquals(200, call())
        assertEquals(listOf(1_000L), delays)
    }

    private fun tooManyRequests(retryAfter: String? = null): MockResponse {
        val builder = MockResponse.Builder().code(429)
        if (retryAfter != null) builder.addHeader("Retry-After", retryAfter)
        return builder.build()
    }

    @Test
    fun `429 is retried after the Retry-After the server asks for`() {
        server.enqueue(tooManyRequests("3"))
        server.enqueue(MockResponse(code = 200))

        assertEquals(200, call())
        assertEquals(listOf(3_000L), delays)
    }

    @Test
    fun `429 without a usable Retry-After is retried with the normal back-off`() {
        server.enqueue(tooManyRequests())
        server.enqueue(tooManyRequests("Wed, 21 Oct 2026 07:28:00 GMT"))
        server.enqueue(MockResponse(code = 200))

        assertEquals(200, call())
        assertEquals(listOf(1_000L, 2_000L), delays)
    }

    @Test
    fun `given a Retry-After longer than 10 s, then 429 is not retried`() {
        server.enqueue(tooManyRequests("60"))
        server.enqueue(MockResponse(code = 200))

        assertEquals(429, call())
        assertEquals(emptyList<Long>(), delays)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `given 429 every time, then the last answer comes back after two retries`() {
        repeat(3) { server.enqueue(tooManyRequests("1")) }

        assertEquals(429, call())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `other client errors are not retried`() {
        server.enqueue(MockResponse(code = 400))
        server.enqueue(MockResponse(code = 200))

        assertEquals(400, call())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a success is returned at once`() {
        server.enqueue(MockResponse(code = 200))

        assertEquals(200, call())
        assertEquals(emptyList<Long>(), delays)
    }
}
