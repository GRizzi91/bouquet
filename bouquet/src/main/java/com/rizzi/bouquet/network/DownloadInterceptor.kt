package com.rizzi.bouquet.network

import okhttp3.Interceptor
import okhttp3.Response

internal class DownloadInterceptor(
    private val headers: HashMap<String, String>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val oldRequest = chain.request()
        val newRequest = oldRequest.newBuilder().apply {
            headers.forEach { entry ->
                header(entry.key, entry.value)
            }
        }.build()
        return chain.proceed(newRequest)
    }
}