package com.example.videoplayer.data.dto

data class VideoItem (
    val id : Int,
    var title : String,
    var thumbnail : String,
    var downloaded : Boolean,
    var fileSize : String,
) : java.io.Serializable

//data class VideoItem (
//    val id : Int,
//    var title : String,
//    var videoUrl : String,
//    var videoThumbnail : String,
//    var downloaded : Boolean,
//    var fileSize : String,
//) : java.io.Serializable
