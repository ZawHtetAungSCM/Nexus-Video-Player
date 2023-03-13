package com.example.videoplayer.data.dto

import com.example.videoplayer.model.FileType

data class ItemList(
    val data: List<ItemDto>
)

data class ItemDto (
    val id : Int,
    var title : String,
    var url : String,
    var thumbnail : String,
    var downloaded : Boolean,
    var fileType : FileType,
    var fileSize : String,
) : java.io.Serializable