package com.example.videoplayer.data.dto

data class VideoList(
    val data: List<VideoData>
)

data class VideoData(
    val id: Int,
    val title: String,
    val thumbnail: String,
    val created_at: String,
    val updated_at: String,
)