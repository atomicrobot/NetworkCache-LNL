package io.atomicrobot.httpcachepresentation

import okhttp3.*
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
    lateinit var request: Request

    val wrong = Int.MAX_VALUE
    val wrongString = "Not the correct value"

    @Before
    fun setup() {
        Logger.getLogger(MockWebServer::class.java.name).level = Level.OFF
        val cacheDir = folder.root

        server = MockWebServer()
        server.start(8000)
        baseUrl = server.url("/v1/model/")
        cache = Cache(cacheDir, DISK_CACHE_SIZE.toLong())
        request = Request.Builder()
                .url(baseUrl)
                .build()

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

        val response1 = client.newCall(request).execute()
        val response2 = client.newCall(request).execute()
        val response3 = client.newCall(request).execute()

        assertEquals("hello, world!", response1.body()?.string())
        assertEquals("hello, again!", response2.body()?.string())
        assertEquals("hello, one more time!", response3.body()?.string())
    }

    @Test
    fun testMaxAge() {
        server.enqueue(MockResponse()
                .setHeader("cache-control", "max-age=1")
                .setBody("First Response"))
        server.enqueue(MockResponse()
                .setBody("Second Response"))
        server.enqueue(MockResponse()
                .setBody("Third Response"))

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

        val response4 = client.newCall(request).execute()
        assertEquals(4, cache.requestCount())
        assertEquals(3, cache.networkCount())
        assertEquals(1, cache.hitCount())
        assertEquals("Third Response", response4.body()?.string())
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

        val response1 = client.newCall(request).execute()
        assertEquals(1, cache.requestCount())
        assertEquals(1, cache.networkCount())
        assertEquals(0, cache.hitCount())
        assertEquals("First Response", response1.body()?.string())


//        OkHTTP is adding the If-None-Match header internally.  If-None-Match example:
//        val request2 = Request.Builder()
//                .url(baseUrl)
//                .header("If-None-Match", "v1")
//                .build()

//        OkHTTP handles the 304 internally.
//        A 200 is exposed and the body is retrieved from the cache
        val response2 = client.newCall(request).execute()
        assertEquals(2, cache.requestCount())
        assertEquals(2, cache.networkCount())
        assertEquals(1, cache.hitCount())
        assertEquals("First Response", response2.body()?.string())
        assertEquals(200, response2.code())
    }

    @Test
    fun testEtagAndMaxAge() {
        server.enqueue(MockResponse()
                .setHeader("cache-control", "private, max-age=1")
                .setHeader("ETag", "v1")
                .setBody("First Response"))
        server.enqueue(MockResponse()
                .setResponseCode(304))

        val response1 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals("First Response", response1.body()?.string())

        val response2 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals("First Response", response2.body()?.string())

        Thread.sleep(1000)

        val response3 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals("First Response", response3.body()?.string())
    }

    @Test
    fun testNoCacheHeader() {
        server.enqueue(MockResponse()
                .setHeader("cache-control", "no-cache")
                .setBody("First Response"))
        server.enqueue(MockResponse()
                .setBody("Second Response"))

        val response1 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response1.body()?.string())

        val response2 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response2.body()?.string())
    }

    @Test
    fun testNoCacheHeaderWithEtag() {
        server.enqueue(MockResponse()
                .setHeader("cache-control", "no-cache")
                .setBody("First Response"))
        server.enqueue(MockResponse()
                .setHeader("cache-control", "no-cache")
                .setHeader("ETag", "v1")
                .setBody("Second Response"))
        server.enqueue(MockResponse()
                .setResponseCode(304))
        server.enqueue(MockResponse()
                .setResponseCode(304))

        val response1 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response1.body()?.string())

        val response2 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response2.body()?.string())

        val response3 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response3.body()?.string())

        val response4 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response4.body()?.string())
    }

    @Test
    fun testNoStoreHeader() {
        server.enqueue(MockResponse()
                .setHeader("cache-control", "no-store")
                .setBody("First Response"))
        server.enqueue(MockResponse()
                .setHeader("cache-control", "no-store")
                .setHeader("ETag", "v1")
                .setBody("Second Response"))
        server.enqueue(MockResponse()
                .setHeader("cache-control", "no-store")
                .setResponseCode(304))

        val response1 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response1.body()?.string())

        val response2 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response2.body()?.string())

        val response3 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response3.body()?.string())
    }

    @Test
    fun testAllTheThingsPart1() {
        server.enqueue(MockResponse()
                .setHeader("cache-control", "private, no-cache, max-age=1")
                .setHeader("ETag", "v1")
                .setBody("First Response"))
        server.enqueue(MockResponse()
                .setResponseCode(304))

        val response1 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals("First Response", response1.body()?.string())

        val response2 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals("First Response", response2.body()?.string())
    }

    // Bonus Time!
    @Test
    fun testForceNetwork() {
        server.enqueue(MockResponse()
                .setHeader("cache-control", "private, max-age=1")
                .setHeader("ETag", "v1")
                .setBody("First Response"))
        server.enqueue(MockResponse()
                .setHeader("cache-control", "private, max-age=1")
                .setHeader("ETag", "v1")
                .setBody("First Response"))

        val response1 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals("First Response", response1.body()?.string())

        val response2 = client.newCall(
                request.newBuilder()
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .build())
                .execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals("First Response", response2.body()?.string())
    }

    @Test
    fun testForceCache() {
        server.enqueue(MockResponse()
                .setHeader("cache-control", "private, max-age=1")
                .setHeader("ETag", "v1")
                .setBody("First Response"))
        server.enqueue(MockResponse()
                .setHeader("cache-control", "private, max-age=1")
                .setHeader("ETag", "v1")
                .setBody("Second Response"))

        val response1 = client.newCall(request).execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response1.body()?.string())

        Thread.sleep(1000)

        val response2 = client.newCall(
                request.newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build())
                .execute()
        assertEquals(wrong, cache.requestCount())
        assertEquals(wrong, cache.networkCount())
        assertEquals(wrong, cache.hitCount())
        assertEquals(wrongString, response2.body()?.string())
    }

    @Test
    fun testForceCacheWithNoData() {
        val response1 = client.newCall(
                request.newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build())
                .execute()
        assertEquals(1, cache.requestCount())
        assertEquals(0, cache.networkCount())
        assertEquals(0, cache.hitCount())
        assertEquals(504, response1.code()) // 504 Unsatisfiable

        server.enqueue(MockResponse()
                .setHeader("cache-control", "no-store")
                .setBody("First Response"))
        client.newCall(request).execute()

        val response2 = client.newCall(
                request.newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build())
                .execute()
        assertEquals(3, cache.requestCount())
        assertEquals(1, cache.networkCount())
        assertEquals(0, cache.hitCount())
        assertEquals(504, response2.code()) // 504 Unsatisfiable
    }

    companion object {
        private const val DISK_CACHE_SIZE = 50 * 1024 * 1024 // 50MB
    }
}
