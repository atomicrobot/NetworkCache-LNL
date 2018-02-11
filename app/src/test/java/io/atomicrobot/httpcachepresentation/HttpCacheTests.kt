package io.atomicrobot.httpcachepresentation

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class HttpCacheTests {
    lateinit var server: MockWebServer
    lateinit var client: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start(8000)

        client = OkHttpClient()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun testMockServerShouldWork() {
        val baseUrl = server.url("/v1/model/")
        server.enqueue(MockResponse().setBody("hello, world!"))
        server.enqueue(MockResponse().setBody("hello, again!"))
        server.enqueue(MockResponse().setBody("hello, one more time!"))
        val request = Request.Builder()
                .url(baseUrl)
                .build()

        val response1 = client.newCall(request).execute()
        val response2 = client.newCall(request).execute()
        val response3 = client.newCall(request).execute()

        Assert.assertEquals("hello, world!", response1.body()?.string())
        Assert.assertEquals("hello, again!", response2.body()?.string())
        Assert.assertEquals("hello, one more time!", response3.body()?.string())
    }
}
