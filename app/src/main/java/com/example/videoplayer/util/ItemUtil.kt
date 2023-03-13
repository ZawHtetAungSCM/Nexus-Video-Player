package com.example.videoplayer.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import com.example.videoplayer.Constants
import com.example.videoplayer.data.dto.ItemDto
import com.example.videoplayer.model.FileType
import org.json.JSONArray
import org.json.JSONException
import java.nio.charset.StandardCharsets

class ItemUtil {

    companion object{
        @SuppressLint("StaticFieldLeak")
        var mCurrentActivity: Activity? = null

        // get the current activity for token expire check logout function
        fun getCurrentActivity(): Activity? {
            return this.mCurrentActivity
        }

        // set Current Activity
        fun setCurrentActivity(mCurrentActivity: Activity?) {
            this.mCurrentActivity = mCurrentActivity
        }

        fun fillListWithSamples(context: Context,jsonFileName: String, fileType: FileType): List<ItemDto> {
            val jsonArray = getLocalSampleList(jsonFileName)
            val itemList = mutableListOf<ItemDto>()
            val vDownloadFilePath = FileUtil.getDownloadFileDirectory(context)
            val fileList = FileUtil.getFileListOfDirectory(vDownloadFilePath)
            if (jsonArray != null) {
                for (i in 0 until jsonArray.length()) {
                    try {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val audioItem = ItemDto(
                            id = jsonObject.getString("id").toInt(),
                            title = jsonObject.getString("title"),
                            url = jsonObject.getString("url"),
                            thumbnail = jsonObject.getString("thumbnail"),
                            downloaded = false,
                            fileType = fileType,
                            fileSize = ""
                        )
                        val itemFileName = FileUtil.getFileNameForItem(audioItem)
                        audioItem.downloaded = fileList.contains(itemFileName)
                        itemList.add(audioItem)

                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }

            }
            return itemList
        }

        // A method for converting local sample videos list to JSONArray
        private fun getLocalSampleList(fileName:String): JSONArray? {
            val jsonArray: JSONArray = try {
                val inputStream = mCurrentActivity?.assets?.open(fileName)
                val size = inputStream?.available()
                val buffer = ByteArray(size!!)
                inputStream.read(buffer)
                inputStream.close()
                val jsonString = String(buffer, StandardCharsets.UTF_8)
                JSONArray(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
            return jsonArray
        }
    }
}