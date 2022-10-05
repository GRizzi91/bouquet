package com.rizzi.bouquet

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import java.io.File

class PdfReaderState(
    val resource: ResourceType,
    isZoomEnable: Boolean = false
) {
    internal var mError by mutableStateOf<Throwable?>(null)
    val error: Throwable?
        get() = mError

    internal var mIsZoomEnable by mutableStateOf(isZoomEnable)
    val isZoomEnable: Boolean
        get() = mIsZoomEnable

    fun changeZoomState(enable: Boolean) {
        mIsZoomEnable = enable
    }

    internal var mScale by mutableStateOf(1f)
    val scale: Float
        get() = mScale

    internal var offset by mutableStateOf(Offset(0f, 0f))

    internal var mFile by mutableStateOf<File?>(null)
    val file: File?
        get() = mFile

    internal var lazyState: LazyListState = LazyListState()

    internal var pdfRender by mutableStateOf<BouquetPdfRender?>(null)

    internal var mLoadPercent by mutableStateOf(0)
    val loadPercent: Int
        get() = mLoadPercent

    val pdfPageCount: Int
        get() = pdfRender?.pageCount ?: 0

    val currentPage: Int
        get() = currentPage()

    private fun currentPage(): Int {
        return pdfRender?.let { pdfRender ->
            val currentMinIndex = lazyState.firstVisibleItemIndex
            var lastVisibleIndex = currentMinIndex
            var totalVisiblePortion =
                (pdfRender.pageLists[currentMinIndex].dimension.height * scale) - lazyState.firstVisibleItemScrollOffset
            for (i in currentMinIndex + 1 until pdfPageCount) {
                val newTotalVisiblePortion =
                    totalVisiblePortion + (pdfRender.pageLists[i].dimension.height * scale)
                if (newTotalVisiblePortion <= pdfRender.height) {
                    lastVisibleIndex = i
                    totalVisiblePortion = newTotalVisiblePortion
                } else {
                    break
                }
            }
            lastVisibleIndex + 1
        } ?: 0
    }

    val isLoaded
        get() = mFile != null

    val isScrolling: Boolean
        get() = lazyState.isScrollInProgress

    companion object {
        val Saver: Saver<PdfReaderState, *> = listSaver(
            save = {
                listOf(it.resource, it.isZoomEnable)
            },
            restore = {
                PdfReaderState(it[0] as ResourceType, it[1] as Boolean)
            }
        )
    }
}

@Composable
fun rememberPdfReaderState(
    resource: ResourceType,
    isZoomEnable: Boolean = true
): PdfReaderState {
    return rememberSaveable(saver = PdfReaderState.Saver) {
        PdfReaderState(resource, isZoomEnable)
    }
}
