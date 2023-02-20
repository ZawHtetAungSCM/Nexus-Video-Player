package com.example.videoplayer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.videoplayer.data.dto.VideoItem

class VideoViewModel() : ViewModel() {

    private val _videoItems = MutableLiveData<List<VideoItem>>()
    val videoItems: LiveData<List<VideoItem>> = _videoItems

}