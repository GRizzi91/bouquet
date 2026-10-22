package com.rizzi.bouquet

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the readers inside a real activity and asserts on the state they expose.
 *
 * It deliberately avoids `createComposeRule`: the Espresso layer it depends on does not run on
 * the newest Android releases yet, and everything worth checking here is observable on
 * [PdfReaderState].
 */
@RunWith(AndroidJUnit4::class)
class PdfReaderTest {

    private lateinit var scenario: ActivityScenario<TestActivity>
    private val asset = PdfSource.Asset("lorem_ipsum.pdf")

    @Before
    fun launch() {
        scenario = ActivityScenario.launch(TestActivity::class.java)
    }

    @After
    fun close() {
        scenario.close()
    }

    @Test
    fun verticalReaderLoadsTheDocument() {
        val state = VerticalPdfReaderState(asset)
        scenario.onActivity { activity ->
            activity.setContent { VerticalPdfReader(state = state, modifier = Modifier.fillMaxSize()) }
        }

        waitUntil("document loaded") { state.isLoaded }
        assertTrue(state.pageCount > 0)
        assertNotNull(state.file)
        // The list lays out one frame after the document opens.
        waitUntil("first layout") { state.canScrollForward }
        assertEquals(0, state.currentPage)
        assertFalse(state.canScrollBackward)
    }

    @Test
    fun verticalReaderScrollsToPage() {
        val state = VerticalPdfReaderState(asset)
        scenario.onActivity { activity ->
            activity.setContent { VerticalPdfReader(state = state, modifier = Modifier.fillMaxSize()) }
        }
        waitUntil("document loaded") { state.isLoaded }

        val target = state.pageCount - 1
        onMain { runBlocking { state.scrollToPage(target) } }
        waitUntil("scrolled to last page") { state.currentPage == target }
        waitUntil("cannot scroll forward") { !state.canScrollForward }
    }

    @Test
    fun horizontalReaderScrollsToPage() {
        val state = HorizontalPdfReaderState(asset)
        scenario.onActivity { activity ->
            activity.setContent { HorizontalPdfReader(state = state, modifier = Modifier.fillMaxSize()) }
        }
        waitUntil("document loaded") { state.isLoaded }

        val target = state.pageCount - 1
        onMain { runBlocking { state.scrollToPage(target) } }
        waitUntil("scrolled to last page") { state.currentPage == target }
        waitUntil("cannot scroll forward") { !state.canScrollForward }
    }

    @Test
    fun loadReplacesTheDocumentAndReportsErrors() {
        val state = VerticalPdfReaderState(asset)
        scenario.onActivity { activity ->
            activity.setContent { VerticalPdfReader(state = state, modifier = Modifier.fillMaxSize()) }
        }
        waitUntil("document loaded") { state.isLoaded }

        onMain { state.load(PdfSource.File(File("/definitely/missing.pdf"))) }
        waitUntil("error reported") { state.error != null }
        assertTrue(state.error is PdfException.NotFound)
        assertEquals(0, state.pageCount)

        onMain { state.load(asset) }
        waitUntil("document reloaded") { state.isLoaded }
        assertTrue(state.pageCount > 0)
    }

    @Test
    fun singlePageThumbnailLoads() {
        val state = VerticalPdfReaderState(asset)
        scenario.onActivity { activity ->
            activity.setContent { PdfPage(state = state, pageIndex = 0, modifier = Modifier.size(120.dp)) }
        }
        waitUntil("document loaded") { state.isLoaded }
        assertTrue(state.pageCount > 0)
    }

    @Test
    fun documentIsReleasedWhenReaderLeavesComposition() {
        val state = VerticalPdfReaderState(asset)
        var show by mutableStateOf(true)
        scenario.onActivity { activity ->
            activity.setContent { if (show) VerticalPdfReader(state = state, modifier = Modifier.fillMaxSize()) }
        }
        waitUntil("document loaded") { state.isLoaded }

        onMain { show = false }
        waitUntil("document released") { state.loadState is PdfLoadState.Idle }

        onMain { show = true }
        waitUntil("document reopened") { state.isLoaded }
    }

    @Test
    fun zoomIsClampedAndResets() {
        val state = VerticalPdfReaderState(asset, PdfReaderConfig(maxZoom = 3f))
        scenario.onActivity { activity ->
            activity.setContent { VerticalPdfReader(state = state, modifier = Modifier.fillMaxSize()) }
        }
        waitUntil("document loaded") { state.isLoaded }

        onMain { state.setZoom(10f) }
        assertEquals(3f, state.zoom)
        onMain { state.resetZoom() }
        assertEquals(1f, state.zoom)
    }

    /** Runs [block] on the main thread; unlike `onActivity` it does not need a resumed activity. */
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    /** Polls [condition] (snapshot state reads are thread-safe) until it holds or [timeoutMs] elapses. */
    private fun waitUntil(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for: $what")
    }
}
