package io.atomicrobot.httpcachepresentation

import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.logging.Level
import java.util.logging.Logger


class HttpCacheTests {
    @JvmField
    @Rule
    val folder = TemporaryFolder()

    lateinit var server: MockWebServer
    lateinit var client: OkHttpClient
    lateinit var baseUrl: HttpUrl
    lateinit var cache: Cache

    @Before
    fun setup() {
        Logger.getLogger(MockWebServer::class.java.name).level = Level.OFF
        val cacheDir = folder.root

        server = MockWebServer()
        server.start(8000)
        baseUrl = server.url("/v1/model/")
        cache = Cache(cacheDir, DISK_CACHE_SIZE.toLong())

        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY
        client = OkHttpClient().newBuilder()
//                .addInterceptor(logging)
                .cache(cache)
                .build()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun testMockServerShouldWork() {
        server.enqueue(MockResponse().setBody("hello, world!"))
        server.enqueue(MockResponse().setBody("hello, again!"))
        server.enqueue(MockResponse().setBody("hello, one more time!"))
        val request = Request.Builder()
                .url(baseUrl)
                .build()

        val response1 = client.newCall(request).execute()
        val response2 = client.newCall(request).execute()
        val response3 = client.newCall(request).execute()

        assertEquals("hello, world!", response1.body()?.string())
        assertEquals("hello, again!", response2.body()?.string())
        assertEquals("hello, one more time!", response3.body()?.string())
    }

    @Test
    fun testMaxAge() {
        server.enqueue(MockResponse().setHeader("cache-control", "max-age=1").setBody("First Response"))
        server.enqueue(MockResponse().setHeader("cache-control", "max-age=2").setBody("Second Response"))
        val request = Request.Builder()
                .url(baseUrl)
                .build()

        val response1 = client.newCall(request).execute()
        assertEquals(1, cache.requestCount())
        assertEquals(1, cache.networkCount())
        assertEquals(0, cache.hitCount())
        assertEquals("First Response", response1.body()?.string())

        val response2 = client.newCall(request).execute()
        assertEquals(2, cache.requestCount())
        assertEquals(1, cache.networkCount())
        assertEquals(1, cache.hitCount())
        assertEquals("First Response", response2.body()?.string())

        // Wait until the max-age expires, 1 second
        Thread.sleep(1000)

        val response3 = client.newCall(request).execute()
        assertEquals(3, cache.requestCount())
        assertEquals(2, cache.networkCount())
        assertEquals(1, cache.hitCount())
        assertEquals("Second Response", response3.body()?.string())
    }

    // Also referred to as conditionally cached
    @Test
    fun testEtag() {
        server.enqueue(MockResponse()
                .setHeader("ETag", "v1")
                .setResponseCode(200)
                .setBody("First Response"))
        server.enqueue(MockResponse()
                .setResponseCode(304))
        val request = Request.Builder()
                .url(baseUrl)
                .build()

        val response1 = client.newCall(request).execute()
        assertEquals(1, cache.requestCount())
        assertEquals(1, cache.networkCount())
        assertEquals(0, cache.hitCount())
        assertEquals("First Response", response1.body()?.string())

// OkHTTP is adding the If-None-Match header internally
//        val request2 = Request.Builder()
//                .url(baseUrl)
//                .header("If-None-Match", "v1")
//                .build()

        val response2 = client.newCall(request).execute()
        assertEquals(2, cache.requestCount())
        assertEquals(2, cache.networkCount())
        assertEquals(1, cache.hitCount())
        assertEquals("First Response", response2.body()?.string())
    }

    companion object {
        private const val DISK_CACHE_SIZE = 50 * 1024 * 1024 // 50MB
    }
}
