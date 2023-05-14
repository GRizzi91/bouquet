package com.rizzi.bouquet

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.RawRes
import kotlinx.parcelize.Parcelize

sealed class ResourceType {
    @Parcelize
    data class Local(val uri: Uri) : ResourceType(), Parcelable

    @Parcelize
    data class Remote(
        val url: String,
        val headers: HashMap<String,String> = hashMapOf()
    ) : ResourceType(), Parcelable

    @Parcelize
    data class Base64(val file: String) : ResourceType(), Parcelable

    @Parcelize
    data class Asset(@RawRes val assetId: Int) : ResourceType(), Parcelable
}