package com.rizzi.bouquet

import androidx.compose.runtime.Immutable

/** Progress of opening the document held by a [PdfReaderState]. */
@Immutable
public sealed interface PdfLoadState {

    /** Nothing has been requested yet, or the reader left the composition and released the document. */
    public data object Idle : PdfLoadState

    /**
     * A [PdfSource.Url] is being downloaded.
     *
     * @param progress fraction in `0f..1f`, or `null` when the server did not report a size.
     */
    public data class Downloading(val progress: Float?) : PdfLoadState

    /** The file is available locally and is being parsed. */
    public data object Opening : PdfLoadState

    /** The document is open and its pages can be rendered. */
    public data class Loaded(val pageCount: Int) : PdfLoadState

    /** Opening failed. Call [PdfReaderState.retry] to try again. */
    public data class Error(val exception: PdfException) : PdfLoadState
}

/** Typed failures reported through [PdfLoadState.Error]. */
public sealed class PdfException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * The download failed.
     *
     * @param statusCode HTTP status when the server answered with an error, `null` otherwise.
     */
    public class Network(
        public val url: String,
        public val statusCode: Int?,
        cause: Throwable? = null,
    ) : PdfException(
        if (statusCode != null) "HTTP $statusCode while downloading $url" else "Failed to download $url",
        cause,
    )

    /** The source could not be opened: missing file, revoked URI permission, unknown asset... */
    public class NotFound(
        public val source: PdfSource,
        cause: Throwable? = null,
    ) : PdfException("Could not open $source", cause)

    /** The bytes are not a valid PDF, or the document is damaged. */
    public class Corrupted(cause: Throwable? = null) :
        PdfException("The file is not a valid PDF or is damaged", cause)

    /** The document is encrypted and no (or a wrong) password was provided. */
    public class PasswordRequired(cause: Throwable? = null) :
        PdfException("The document is password protected", cause)

    /** The requested feature is not available on this device. */
    public class Unsupported(message: String) : PdfException(message)

    /** Anything else. */
    public class Unknown(cause: Throwable) :
        PdfException(cause.message ?: "Unexpected error while opening the document", cause)
}
