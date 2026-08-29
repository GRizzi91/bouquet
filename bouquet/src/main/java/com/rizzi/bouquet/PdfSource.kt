package com.rizzi.bouquet

import android.os.Parcelable
import androidx.annotation.RawRes as RawResId
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import android.net.Uri as AndroidUri
import java.io.File as JavaFile

/**
 * Where a PDF comes from.
 *
 * All sources are [Parcelable] so a reader state can survive process death.
 */
@Immutable
public sealed interface PdfSource : Parcelable {

    /**
     * A `content://` or `file://` URI, typically returned by the document picker.
     * The file is read in place through the [android.content.ContentResolver]; no copy is made.
     */
    @Parcelize
    public data class Uri(val uri: AndroidUri) : PdfSource

    /** A file on local storage readable by the app. */
    @Parcelize
    public data class File(val file: JavaFile) : PdfSource

    /**
     * A remote document. It is downloaded once into the app cache (keyed by [url]) and reused
     * on following opens; see [Bouquet.clearCache].
     *
     * @param headers extra HTTP headers sent with the request (for example `Authorization`).
     */
    @Parcelize
    public data class Url(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : PdfSource

    /** A PDF bundled in `res/raw`. */
    @Parcelize
    public data class RawRes(@RawResId val id: Int) : PdfSource

    /** A PDF bundled in the `assets` folder, addressed by its relative path. */
    @Parcelize
    public data class Asset(val path: String) : PdfSource

    /** A Base64-encoded PDF. */
    @Parcelize
    public data class Base64(val data: String) : PdfSource {
        override fun toString(): String = "Base64(length=${data.length})"
    }

    public companion object {

        @Deprecated("Renamed to PdfSource.Url", ReplaceWith("PdfSource.Url(url, headers)"))
        public fun Remote(url: String, headers: Map<String, String> = emptyMap()): Url = Url(url, headers)

        @Deprecated("Renamed to PdfSource.Uri", ReplaceWith("PdfSource.Uri(uri)"))
        public fun Local(uri: AndroidUri): Uri = Uri(uri)

        @Deprecated(
            "Renamed to PdfSource.RawRes; PdfSource.Asset now reads from the assets folder",
            ReplaceWith("PdfSource.RawRes(assetId)"),
        )
        public fun Asset(@RawResId assetId: Int): RawRes = RawRes(assetId)
    }
}
