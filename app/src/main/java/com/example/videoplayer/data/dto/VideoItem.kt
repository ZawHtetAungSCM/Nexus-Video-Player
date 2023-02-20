package com.example.videoplayer.data.dto

data class VideoItem (
    val id : Int,
    var title : String,
    var videoUrl : String,
    var videoThumbnail : String,
    var downloaded : Boolean,
) : java.io.Serializable
