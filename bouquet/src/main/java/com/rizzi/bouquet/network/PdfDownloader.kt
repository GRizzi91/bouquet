package com.rizzi.bouquet.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Downloads a remote PDF to a local file.
 *
 * Implement this to plug in your own HTTP stack (authentication, certificate pinning, custom
 * caching...). The default implementation is [OkHttpPdfDownloader].
 */
public interface PdfDownloader {

    /**
     * Downloads [url] into [destination], replacing any existing content.
     *
     * @param headers extra request headers.
     * @param onProgress called with the fraction downloaded in `0f..1f`, or `null` when the
     *   server did not send a `Content-Length`. May be called from a background thread.
     * @throws IOException on network failure or non-2xx response (see [PdfHttpException]).
     */
    public suspend fun download(
        url: String,
        headers: Map<String, String>,
        destination: File,
        onProgress: (Float?) -> Unit,
    )
}

/** Thrown by [OkHttpPdfDownloader] when the server answers with a non-successful status code. */
public class PdfHttpException(
    public val code: Int,
    public val url: String,
) : IOException("HTTP $code while downloading $url")

/**
 * [PdfDownloader] backed by OkHttp.
 *
 * @param client the client used for requests; pass your own to configure timeouts,
 *   interceptors, authenticators or certificate pinning.
 */
public class OkHttpPdfDownloader(
    private val client: OkHttpClient = defaultClient,
) : PdfDownloader {

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        destination: File,
        onProgress: (Float?) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw PdfHttpException(response.code, url)
            val body = response.body
            val total = body.contentLength()
            var downloaded = 0L
            onProgress(if (total > 0) 0f else null)
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null)
                    }
                }
            }
            onProgress(1f)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
    continuation.invokeOnCancellation { cancel() }
}
