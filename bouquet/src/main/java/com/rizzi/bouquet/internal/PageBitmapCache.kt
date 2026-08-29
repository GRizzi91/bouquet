package com.rizzi.bouquet.internal

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * In-memory cache of rendered pages bounded by a byte budget.
 *
 * Bitmaps are never recycled: an evicted bitmap may still be on screen for a frame, and
 * recycling it there is exactly the crash 1.x users hit. Dropping the reference lets the GC
 * reclaim it once nothing draws it any more.
 */
internal class PageBitmapCache(maxBytes: Long) {

    internal data class Key(val page: Int, val width: Int, val height: Int)

    private val lru = object : LruCache<Key, Bitmap>(maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    /** Most recently rendered key for each page, so a page can show *something* while re-rendering. */
    private val latestKeyForPage = HashMap<Int, Key>()

    private val inFlight = HashMap<Key, CompletableDeferred<Bitmap>>()

    private val lock = Any()

    fun get(key: Key): Bitmap? = synchronized(lock) {
        lru.get(key)?.takeUnless { it.isRecycled }
    }

    fun latestForPage(page: Int): Bitmap? = synchronized(lock) {
        latestKeyForPage[page]?.let { lru.get(it) }?.takeUnless { it.isRecycled }
    }

    fun put(key: Key, bitmap: Bitmap) {
        synchronized(lock) {
            lru.put(key, bitmap)
            latestKeyForPage[key.page] = key
        }
    }

    fun clear() {
        synchronized(lock) {
            lru.evictAll()
            latestKeyForPage.clear()
        }
    }

    /**
     * Returns the cached bitmap for [key], or renders it with [render]. Concurrent requests for
     * the same key share one render.
     */
    suspend fun getOrRender(key: Key, render: suspend () -> Bitmap): Bitmap {
        get(key)?.let { return it }

        val mine: CompletableDeferred<Bitmap>?
        val existing: CompletableDeferred<Bitmap>?
        synchronized(lock) {
            val current = inFlight[key]
            if (current != null) {
                existing = current
                mine = null
            } else {
                existing = null
                mine = CompletableDeferred<Bitmap>().also { inFlight[key] = it }
            }
        }

        if (existing != null) {
            return try {
                existing.await()
            } catch (e: CancellationException) {
                // The renderer that owned the deferred was cancelled, not us: try again.
                currentCoroutineContext().ensureActive()
                getOrRender(key, render)
            }
        }

        checkNotNull(mine)
        try {
            val bitmap = render()
            put(key, bitmap)
            mine.complete(bitmap)
            return bitmap
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            synchronized(lock) { inFlight.remove(key) }
        }
    }
}
