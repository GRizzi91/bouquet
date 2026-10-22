package com.rizzi.bouquet.internal

import android.content.Context
import android.content.res.Resources
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.core.content.pm.PackageInfoCompat
import com.rizzi.bouquet.PdfException
import com.rizzi.bouquet.PdfReaderConfig
import com.rizzi.bouquet.PdfSource
import com.rizzi.bouquet.network.PdfHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Turns a [PdfSource] into an open, seekable file descriptor.
 *
 * Remote, bundled and Base64 documents are materialised once under `cacheDir/bouquet/` with a
 * deterministic name, so opening the same source twice reuses the file. Downloads and copies go
 * to a `.part` file that is renamed only when complete, so a crash never leaves a truncated PDF
 * behind that looks valid.
 */
internal object PdfLoader {

    internal class OpenedPdf(
        val fileDescriptor: ParcelFileDescriptor,
        /** Local file, when one exists. */
        val file: File?,
        /** Opens a fresh stream over the document, for text extractors. */
        val openStream: () -> InputStream,
        /** `true` when [file] is a copy owned by Bouquet's cache. */
        val isCacheCopy: Boolean,
    )

    private const val CACHE_DIR = "bouquet"

    fun cacheDir(context: Context): File = File(context.cacheDir, CACHE_DIR)

    fun clearCache(context: Context): Boolean = cacheDir(context).deleteRecursively()

    suspend fun open(
        context: Context,
        source: PdfSource,
        config: PdfReaderConfig,
        onProgress: (Float?) -> Unit,
    ): OpenedPdf = withContext(Dispatchers.IO) {
        try {
            when (source) {
                is PdfSource.Uri -> openUri(context, source)
                is PdfSource.File -> openFile(source.file, isCacheCopy = false)
                is PdfSource.Url -> openUrl(context, source, config, onProgress)
                is PdfSource.RawRes -> openRawRes(context, source)
                is PdfSource.Asset -> openAsset(context, source)
                is PdfSource.Base64 -> openBase64(context, source)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PdfException) {
            throw e
        } catch (e: PdfHttpException) {
            throw PdfException.Network(e.url, e.code, e)
        } catch (e: FileNotFoundException) {
            throw PdfException.NotFound(source, e)
        } catch (e: SecurityException) {
            throw PdfException.NotFound(source, e)
        } catch (e: Resources.NotFoundException) {
            throw PdfException.NotFound(source, e)
        } catch (e: IOException) {
            if (source is PdfSource.Url) throw PdfException.Network(source.url, null, e)
            throw PdfException.NotFound(source, e)
        }
    }

    // -------------------------------------------------------------------------------------

    private fun openUri(context: Context, source: PdfSource.Uri): OpenedPdf {
        val resolver = context.contentResolver
        val descriptor = resolver.openFileDescriptor(source.uri, "r")
            ?: throw PdfException.NotFound(source)
        val file = if (source.uri.scheme == "file") source.uri.path?.let(::File) else null
        return OpenedPdf(
            fileDescriptor = descriptor,
            file = file,
            openStream = { resolver.openInputStream(source.uri) ?: throw FileNotFoundException(source.uri.toString()) },
            isCacheCopy = false,
        )
    }

    private fun openFile(file: File, isCacheCopy: Boolean): OpenedPdf {
        if (!file.isFile) throw FileNotFoundException(file.path)
        return OpenedPdf(
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            file = file,
            openStream = { file.inputStream() },
            isCacheCopy = isCacheCopy,
        )
    }

    private suspend fun openUrl(
        context: Context,
        source: PdfSource.Url,
        config: PdfReaderConfig,
        onProgress: (Float?) -> Unit,
    ): OpenedPdf {
        val target = cacheFile(context, cacheFileName("url", source.url))
        if (!target.isValidCacheFile()) {
            materialise(target) { part ->
                config.downloader.download(source.url, source.headers, part, onProgress)
            }
        }
        return openFile(target, isCacheCopy = true)
    }

    private suspend fun openRawRes(context: Context, source: PdfSource.RawRes): OpenedPdf {
        val entryName = context.resources.getResourceEntryName(source.id)
        val target = cacheFile(context, cacheFileName("raw", "$entryName-${appVersion(context)}"))
        if (!target.isValidCacheFile()) {
            materialise(target) { part ->
                context.resources.openRawResource(source.id).use { it.copyToCancellable(part) }
            }
        }
        return openFile(target, isCacheCopy = true)
    }

    private suspend fun openAsset(context: Context, source: PdfSource.Asset): OpenedPdf {
        val target = cacheFile(context, cacheFileName("asset", "${source.path}-${appVersion(context)}"))
        if (!target.isValidCacheFile()) {
            materialise(target) { part ->
                context.assets.open(source.path).use { it.copyToCancellable(part) }
            }
        }
        return openFile(target, isCacheCopy = true)
    }

    private suspend fun openBase64(context: Context, source: PdfSource.Base64): OpenedPdf {
        val target = cacheFile(context, cacheFileName("b64", source.data))
        if (!target.isValidCacheFile()) {
            val bytes = try {
                Base64.decode(source.data, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw PdfException.Corrupted(e)
            }
            materialise(target) { part -> part.writeBytes(bytes) }
        }
        return openFile(target, isCacheCopy = true)
    }

    // -------------------------------------------------------------------------------------

    /** Deterministic, filesystem-safe cache file name for a source of [kind] identified by [key]. */
    internal fun cacheFileName(kind: String, key: String): String = "$kind-${sha256(key)}.pdf"

    internal fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cacheFile(context: Context, name: String): File {
        val dir = cacheDir(context)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create ${dir.path}")
        return File(dir, name)
    }

    private fun File.isValidCacheFile(): Boolean = isFile && length() > 0L

    /**
     * Writes through a `.part` sibling and renames it into [target] on success, deleting the
     * partial file on failure or cancellation.
     */
    private suspend fun materialise(target: File, write: suspend (File) -> Unit) {
        val part = File(target.path + ".part")
        try {
            write(part)
            if (part.length() == 0L) throw IOException("Empty document")
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) throw IOException("Cannot move ${part.name} to ${target.name}")
            }
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    private suspend fun InputStream.copyToCancellable(target: File) {
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContextEnsureActive()
                val read = read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
            }
        }
    }

    private suspend fun currentCoroutineContextEnsureActive() {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
    }

    private fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info).toString()
    }.getOrDefault("0")
}
