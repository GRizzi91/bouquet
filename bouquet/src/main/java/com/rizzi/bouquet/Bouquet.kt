package com.rizzi.bouquet

import android.content.Context
import android.net.Uri
import android.os.FileUtils
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Constraints
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.rizzi.bouquet.network.getDownloadInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.math.abs

@Composable
fun VerticalPDFReader(
    state: VerticalPdfReaderState,
    modifier: Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        val ctx = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val lazyState = state.lazyState
        DisposableEffect(key1 = Unit) {
            load(
                coroutineScope,
                ctx,
                state,
                constraints.maxWidth,
                constraints.maxHeight,
                true
            )
            onDispose {
                state.close()
            }
        }
        state.pdfRender?.let { pdf ->
            println("DEBUG: scroll ${state.lazyState.firstVisibleItemScrollOffset}")
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pinchToZoomVertical(state, constraints),
                horizontalAlignment = Alignment.CenterHorizontally,
                state = lazyState
            ) {
                items(pdf.pageCount) {
                    val pageContent = pdf.pageLists[it].stateFlow.collectAsState().value
                    DisposableEffect(key1 = Unit) {
                        pdf.pageLists[it].load()
                        onDispose {
                            pdf.pageLists[it].recycle()
                        }
                    }
                    val height = pageContent.bitmap.height * state.scale
                    val width = constraints.maxWidth * state.scale
                    PdfImage(
                        graphicsLayerData = {
                            GraphicsLayerData(
                                scale = 1f,
                                translationX = 0f,
                                translationY = 0f
                            )
                        },
                        bitmap = {
                            pageContent.bitmap.asImageBitmap()
                        },
                        contentDescription = pageContent.contentDescription,
                        dimension = {
                            Dimension(
                                height = with(density) { height.toDp() },
                                width = with(density) { width.toDp() }
                            )
                        }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Divider(color = Color.Red)
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun HorizontalPDFReader(
    state: HorizontalPdfReaderState,
    modifier: Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        val ctx = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        DisposableEffect(key1 = Unit) {
            load(
                coroutineScope,
                ctx,
                state,
                constraints.maxWidth,
                constraints.maxHeight,
                constraints.maxHeight > constraints.maxWidth
            )
            onDispose {
                state.close()
            }
        }
        state.pdfRender?.let { pdf ->
            HorizontalPager(
                modifier = Modifier
                    .fillMaxSize()
                    .pinchToZoomHorizontal(state, constraints),
                count = state.pdfPageCount,
                state = state.pagerState,
                userScrollEnabled = state.scale == 1f
            ) { page ->
                val pageContent = pdf.pageLists[page].stateFlow.collectAsState().value
                DisposableEffect(key1 = Unit) {
                    pdf.pageLists[page].load()
                    onDispose {
                        pdf.pageLists[page].recycle()
                    }
                }
                val height = pageContent.bitmap.height * state.scale
                val width = constraints.maxWidth * state.scale
                PdfImage(
                    graphicsLayerData = {
                        if (page == state.currentPage) {
                            GraphicsLayerData(
                                scale = state.scale,
                                translationX = state.offset.x,
                                translationY = state.offset.y
                            )
                        } else {
                            GraphicsLayerData(
                                scale = 1f,
                                translationX = 0f,
                                translationY = 0f
                            )
                        }
                    },
                    bitmap = { pageContent.bitmap.asImageBitmap() },
                    contentDescription = pageContent.contentDescription,
                    dimension = {
                        Dimension(
                            height = with(density) { height.toDp() },
                            width = with(density) { width.toDp() }
                        )
                    }
                )
            }
        }
    }
}


private fun load(
    coroutineScope: CoroutineScope,
    context: Context,
    state: PdfReaderState,
    width: Int,
    height: Int,
    portrait: Boolean
) {
    runCatching {
        if (state.isLoaded) {
            coroutineScope.launch(Dispatchers.IO) {
                val pFD =
                    ParcelFileDescriptor.open(state.mFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val textForEachPage =
                    if (state.isAccessibleEnable) getTextByPage(context, pFD) else emptyList()
                state.pdfRender = BouquetPdfRender(pFD, textForEachPage, width, height, portrait)
            }
        } else {
            when (val res = state.resource) {
                is ResourceType.Local -> {
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openFileDescriptor(res.uri, "r")?.let {
                                val textForEachPage = if (state.isAccessibleEnable) {
                                    getTextByPage(context, it)
                                } else emptyList()
                                state.pdfRender =
                                    BouquetPdfRender(it, textForEachPage, width, height, portrait)
                                state.mFile = context.uriToFile(res.uri)
                            } ?: throw IOException("File not found")
                        }.onFailure {
                            state.mError = it
                        }
                    }
                }

                is ResourceType.Remote -> {
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            val bufferSize = 8192
                            var downloaded = 0
                            val file = File(context.cacheDir, generateFileName())
                            val response = getDownloadInterface(
                                res.headers
                            ).downloadFile(
                                res.url
                            )
                            val byteStream = response.byteStream()
                            byteStream.use { input ->
                                file.outputStream().use { output ->
                                    val totalBytes = response.contentLength()
                                    var data = ByteArray(bufferSize)
                                    var count = input.read(data)
                                    while (count != -1) {
                                        if (totalBytes > 0) {
                                            downloaded += bufferSize
                                            state.mLoadPercent =
                                                (downloaded * (100 / totalBytes.toFloat())).toInt()
                                        }
                                        output.write(data, 0, count)
                                        data = ByteArray(bufferSize)
                                        count = input.read(data)
                                    }
                                }
                            }
                            val pFD = ParcelFileDescriptor.open(
                                file,
                                ParcelFileDescriptor.MODE_READ_ONLY
                            )
                            val textForEachPage = if (state.isAccessibleEnable) {
                                getTextByPage(context, pFD)
                            } else emptyList()
                            state.pdfRender =
                                BouquetPdfRender(pFD, textForEachPage, width, height, portrait)
                            state.mFile = file
                        }.onFailure {
                            state.mError = it
                        }
                    }
                }

                is ResourceType.Base64 -> {
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            val file = context.base64ToPdf(res.file)
                            val pFD = ParcelFileDescriptor.open(
                                file,
                                ParcelFileDescriptor.MODE_READ_ONLY
                            )
                            val textForEachPage = if (state.isAccessibleEnable) {
                                getTextByPage(context, pFD)
                            } else emptyList()
                            state.pdfRender =
                                BouquetPdfRender(pFD, textForEachPage, width, height, portrait)
                            state.mFile = file
                        }.onFailure {
                            state.mError = it
                        }
                    }
                }
            }
        }
    }.onFailure {
        state.mError = it
    }
}

fun Modifier.pinchToZoomHorizontal(
    state: HorizontalPdfReaderState,
    constraints: Constraints
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "pinchToZoom"
        properties["state"] = state
    }
) {
    pointerInput(Unit) {
        detectTransformGestures(true) { centroid, pan, zoom, rotation ->
            if (!state.mIsZoomEnable) return@detectTransformGestures
            val nScale = (state.scale * zoom)
                .coerceAtLeast(1f)
                .coerceAtMost(3f)
            val nOffset = if (nScale > 1f) {
                val maxT = constraints.maxWidth * (state.scale - 1)
                val maxH = constraints.maxHeight * (state.scale - 1)
                Offset(
                    x = (state.offset.x + pan.x).coerceIn(
                        minimumValue = -maxT / 2,
                        maximumValue = maxT / 2
                    ),
                    y = (state.offset.y + pan.y).coerceIn(
                        minimumValue = -maxH / 2,
                        maximumValue = maxH / 2
                    )
                )
            } else {
                Offset(0f, 0f)
            }
            state.mScale = nScale
            state.offset = nOffset
        }
    }.graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
    }
}

fun Modifier.pinchToZoomVertical(
    state: VerticalPdfReaderState,
    constraints: Constraints
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "pinchToZoom"
        properties["state"] = state
    }
) {
    val coroutineScope = rememberCoroutineScope()
    pointerInput(Unit) {
        detectTransformGestures(true) { centroid, pan, zoom, rotation ->
            //if (!state.mIsZoomEnable) return@detectTransformGestures
            val nScale = (state.scale * zoom)
                .coerceAtLeast(1f)
                .coerceAtMost(3f)
            val nOffset = if (nScale > 1f) {
                val maxT = (constraints.maxWidth * state.scale) - constraints.maxWidth
                Offset(
                    x = (state.offset.x + pan.x).coerceIn(
                        minimumValue = -maxT / 2,
                        maximumValue = maxT / 2
                    ),
                    y = (state.offset.y + pan.y).coerceIn(
                        minimumValue = -maxT / 2,
                        maximumValue = maxT / 2
                    )
                )
            } else {
                Offset(0f, 0f)
            }
            state.mScale = nScale
            state.offset = nOffset
        }
    }
        .graphicsLayer {
            scaleX = state.scale
            scaleY = state.scale
            translationX = state.offset.x
            translationY = state.offset.y
        }
}

fun Modifier.tapToZoomVertical(
    state: VerticalPdfReaderState,
    constraints: Constraints
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "pinchToZoom"
        properties["state"] = state
    }
) {
    val coroutineScope = rememberCoroutineScope()
    pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { tapCenter ->
                if (state.mScale > 1.0f) {
                    state.mScale = 1.0f
                    state.offset = Offset(0f, 0f)
                } else {
                    state.mScale = 3.0f
                    val center = Pair(constraints.maxWidth / 2, constraints.maxHeight / 2)
                    val xDiff = (tapCenter.x - center.first) * state.scale
                    val yDiff = ((tapCenter.y - center.second) * state.scale).coerceIn(
                        minimumValue = -(center.second * 2f),
                        maximumValue = (center.second * 2f)
                    )
                    state.offset = Offset(
                        -xDiff,
                        -yDiff
                    )
                }
            }
        )
    }
        .pointerInput(Unit) {
            detectTransformGestures(true) { centroid, pan, zoom, rotation ->
                val tupla = if (pan.y > 0) {
                    if (state.lazyState.canScrollBackward) {
                        Pair(0f, pan.y)
                    } else {
                        Pair(pan.y, 0f)
                    }
                } else {
                    if (state.lazyState.canScrollForward) {
                        Pair(0f, pan.y)
                    } else {
                        Pair(pan.y, 0f)
                    }
                }
                val nOffset = if (state.scale > 1f) {
                    val maxT = (constraints.maxWidth * state.scale) - constraints.maxWidth
                    val maxY = (constraints.maxHeight * state.scale) - constraints.maxHeight
                    Offset(
                        x = (state.offset.x + pan.x).coerceIn(
                            minimumValue = (-maxT / 2) * 1.3f,
                            maximumValue = (maxT / 2) * 1.3f
                        ),
                        y = (state.offset.y + tupla.first).coerceIn(
                            minimumValue = (-maxY / 2),
                            maximumValue = (maxY / 2)
                        )
                    )
                } else {
                    Offset(0f, 0f)
                }
                state.offset = nOffset
                coroutineScope.launch { state.lazyState.scrollBy((-tupla.second / state.scale)) }
            }
        }
        .graphicsLayer {
            scaleX = state.scale
            scaleY = state.scale
            translationX = state.offset.x
            translationY = state.offset.y
        }
}

fun Modifier.tapToZoomHorizontal(
    state: HorizontalPdfReaderState,
    constraints: Constraints
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "pinchToZoom"
        properties["state"] = state
    }
) {
    pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { tapCenter ->
                if (state.mScale > 1.0f) {
                    state.mScale = 1.0f
                    state.offset = Offset(0f, 0f)
                } else {
                    state.mScale = 3.0f
                    val center = Pair(constraints.maxWidth / 2, constraints.maxHeight / 2)
                    val xDiff = (tapCenter.x - center.first) * state.scale
                    val yDiff = (tapCenter.y - center.second) * state.scale
                    state.offset = Offset(
                        -xDiff,
                        -yDiff.coerceIn(
                            minimumValue = -(center.second * 2f),
                            maximumValue = (center.second * 2f)
                        )
                    )
                }
            }
        )
    }
        .pointerInput(Unit) {
            detectTransformGestures(true) { centroid, pan, zoom, rotation ->
                val nOffset = if (state.scale > 1f) {
                    val maxT = (constraints.maxWidth * state.scale) - constraints.maxWidth
                    val maxY = (constraints.maxHeight * state.scale) - constraints.maxHeight
                    Offset(
                        x = (state.offset.x + pan.x).coerceIn(
                            minimumValue = (-maxT / 2) * 1.3f,
                            maximumValue = (maxT / 2) * 1.3f
                        ),
                        y = (state.offset.y + pan.y).coerceIn(
                            minimumValue = (-maxY / 2),
                            maximumValue = (maxY / 2)
                        )
                    )
                } else {
                    Offset(0f, 0f)
                }
                state.offset = nOffset
            }
        }
        .graphicsLayer {
            scaleX = state.scale
            scaleY = state.scale
            translationX = state.offset.x
            translationY = state.offset.y
        }
}
