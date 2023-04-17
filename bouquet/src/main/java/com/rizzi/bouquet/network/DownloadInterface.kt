package com.rizzi.bouquet.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

internal interface DownloadInterface {
    @Streaming
    @GET
    suspend fun downloadFile(
        @Url url: String
    ): ResponseBody
}

internal fun getDownloadInterface(
    headers: HashMap<String,String>
): DownloadInterface = getRetrofit(
    headers
).create(DownloadInterface::class.java)
