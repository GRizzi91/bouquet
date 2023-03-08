package com.rizzi.bouquet

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

internal suspend fun Context.base64ToPdf(
    yourBase64String: String?,
    cacheFileName: String = generateFileName()
): File {
    val file = File(cacheDir, cacheFileName)
    yourBase64String?.let {
        with(FileOutputStream(file, false)) {
            withContext(Dispatchers.IO) {
                write(Base64.decode(yourBase64String, Base64.DEFAULT))
                flush()
                close()
            }
        }
    }
    return file
}

internal suspend fun Context.uriToFile(
    uri: Uri,
    cacheFileName: String = generateFileName()
): File  {
    val file = File(cacheDir, cacheFileName)
    withContext(Dispatchers.IO) {
        val fileOutputStream = FileOutputStream(file, false)
        val inputStream = contentResolver.openInputStream(uri)
        inputStream?.readBytes()?.let {
            fileOutputStream.write(it)
        }
        fileOutputStream.flush()
        fileOutputStream.close()
        inputStream?.close()
    }
    return file
}

internal fun generateFileName(): String {
    return "${Date().time}.pdf"
}

internal fun Modifier.size(
    dimension: Dimension
): Modifier = height(dimension.height).width(dimension.width)
