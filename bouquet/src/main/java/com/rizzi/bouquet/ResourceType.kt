package com.rizzi.bouquet

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class ResourceType {
    @Parcelize
    data class Local(val uri: Uri) : ResourceType(), Parcelable

    @Parcelize
    data class Remote(val url: String) : ResourceType(), Parcelable

    @Parcelize
    data class Base64(val file: String) : ResourceType(), Parcelable
}