package com.rizzi.bouquet

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.net.toUri

class VerticalPdfReaderState(
    resource: ResourceType,
    isZoomEnable: Boolean = false,
    isAccessibleEnable: Boolean = false,
    ) : PdfReaderState(resource, isZoomEnable, isAccessibleEnable) {

    internal var lazyState: LazyListState = LazyListState()
        private set

    override val currentPage: Int
        get() = currentPage()

    override val isScrolling: Boolean
        get() = lazyState.isScrollInProgress

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

    companion object {
        val Saver: Saver<VerticalPdfReaderState, *> = listSaver(
            save = {
                val resource = it.file?.let { file ->
                    ResourceType.Local(
                        file.toUri()
                    )
                } ?: it.resource
                listOf(
                    resource,
                    it.isZoomEnable,
                    it.isAccessibleEnable,
                    it.lazyState.firstVisibleItemIndex,
                    it.lazyState.firstVisibleItemScrollOffset
                )
            },
            restore = {
                VerticalPdfReaderState(
                    it[0] as ResourceType,
                    it[1] as Boolean,
                    it[2] as Boolean
                ).apply {
                    lazyState = LazyListState(
                        firstVisibleItemIndex = it[2] as Int,
                        firstVisibleItemScrollOffset = it[3] as Int
                    )
                }
            }
        )
    }
}

@Composable
fun rememberVerticalPdfReaderState(
    resource: ResourceType,
    isZoomEnable: Boolean = true,
    isAccessibleEnable: Boolean = false,
    ): VerticalPdfReaderState {
    return rememberSaveable(saver = VerticalPdfReaderState.Saver) {
        VerticalPdfReaderState(resource, isZoomEnable, isAccessibleEnable)
    }
}
