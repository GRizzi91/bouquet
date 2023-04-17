package com.rizzi.bouquet.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit

internal fun getRetrofit(
    headers: HashMap<String, String>
): Retrofit = Retrofit.Builder()
    .baseUrl("https://www.google.com")
    .client(getClient(headers))
    .build()

internal fun getClient(
    headers: HashMap<String, String>
): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(DownloadInterceptor(headers))
    .build()