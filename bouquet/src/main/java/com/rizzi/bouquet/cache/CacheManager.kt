package com.rizzi.bouquet.cache

import android.graphics.Bitmap
import androidx.collection.LruCache

class CacheManager {

    private var memoryCache: LruCache<String, Bitmap>

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    fun saveBitmap(
        key: String,
        bitmap: Bitmap
    ) = memoryCache.put(key,bitmap)

    fun getBitmap(key: String): Bitmap? {
        return memoryCache.get(key)
    }
}