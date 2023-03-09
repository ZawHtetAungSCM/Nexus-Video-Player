package com.example.videoplayer

import android.app.Application
import com.example.videoplayer.service.RetrofitService

class MainApplication : Application() {

//    private val sharePreferencesService by lazy { PreferenceService(super.getApplicationContext()) }
    private val retrofitService: RetrofitService by lazy {
        RetrofitService.getInstance(super.getApplicationContext())
    }
//    val repository by lazy { MainRepository(sharePreferencesService, retrofitService) }
    val repository by lazy { MainRepository(retrofitService) }
}