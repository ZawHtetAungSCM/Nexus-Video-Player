package com.example.videoplayer.service

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.example.videoplayer.Constants
import com.example.videoplayer.data.dto.VideoList
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*

interface RetrofitService {

    companion object {

        private const val DEBUG_MODE = true

        private var instance: RetrofitService? = null

        fun getInstance(context: Context): RetrofitService {
            if (instance == null) {
                instance = with(Retrofit.Builder()) {
                    baseUrl(Constants.API)
                    addConverterFactory(ScalarsConverterFactory.create())
                    addConverterFactory(GsonConverterFactory.create())
                    if (DEBUG_MODE) client(getClient(context))
                    build().create(RetrofitService::class.java)
                }
            }
            return instance!!
        }

        private fun getClient(context: Context): OkHttpClient {
            return OkHttpClient.Builder().addInterceptor(
                with(ChuckerInterceptor.Builder(context)) {
                    collector(ChuckerCollector(context))
                    maxContentLength(250000L)
                    redactHeaders(emptySet())
                    alwaysReadResponseBody(false)
                    build()
                }
            ).build()
        }
    }

    @GET("videos")
    suspend fun getVideoList(): Response<VideoList>
}