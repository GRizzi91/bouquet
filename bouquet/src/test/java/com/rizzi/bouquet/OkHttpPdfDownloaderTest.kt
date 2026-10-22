package com.rizzi.bouquet

import com.rizzi.bouquet.network.OkHttpPdfDownloader
import com.rizzi.bouquet.network.PdfHttpException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OkHttpPdfDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val downloader = OkHttpPdfDownloader()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `downloads the body and reports monotonic progress ending at one`() = runBlocking {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(payload)).build())
        val target = folder.newFile("doc.pdf")
        val progress = mutableListOf<Float?>()

        downloader.download(server.url("/doc.pdf").toString(), emptyMap(), target) { progress += it }

        assertArrayEquals(payload, target.readBytes())
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it != null })
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a!! <= b!! })
    }

    @Test
    fun `reports null progress when the length is unknown`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .chunkedBody(okio.Buffer().write(ByteArray(50_000)), 8 * 1024)
                .build(),
        )
        val target = folder.newFile("doc.pdf")
        val progress = mutableListOf<Float?>()

        downloader.download(server.url("/doc.pdf").toString(), emptyMap(), target) { progress += it }

        assertEquals(50_000L, target.length())
        assertTrue(progress.dropLast(1).all { it == null })
        assertEquals(1f, progress.last())
    }

    @Test
    fun `forwards custom headers`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("x").build())
        val target = folder.newFile("doc.pdf")

        downloader.download(
            server.url("/doc.pdf").toString(),
            mapOf("Authorization" to "Bearer token", "X-Test" to "1"),
            target,
        ) {}

        val request = server.takeRequest()
        assertEquals("Bearer token", request.headers["Authorization"])
        assertEquals("1", request.headers["X-Test"])
    }

    @Test
    fun `throws PdfHttpException on error status`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).body("missing").build())
        val target = folder.newFile("doc.pdf")

        try {
            downloader.download(server.url("/missing.pdf").toString(), emptyMap(), target) {}
            fail("Expected PdfHttpException")
        } catch (e: PdfHttpException) {
            assertEquals(404, e.code)
        }
    }
}
