package com.example.videoplayer.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.VideoItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class FileUtil {
    companion object {

        suspend fun downloadVideoFileWithFlow(
            context: Context,
            videoItem: VideoItem
        ): Flow<DownloadStatus> {
            val url = URL(videoItem.videoUrl)
            val fileName = getFileNameFromId(videoItem.id!!)
            val fileDir = getDownloadVideoFileDirectory(context)
            val file = getFileNameWithPath(fileDir, fileName)

            return flow {
                try {

                    val connection = url.openConnection() as HttpURLConnection
                    connection.connect()

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("server error: " + connection.responseCode + ", " + connection.responseMessage)
                    }

                    val inputStream = connection.inputStream
                    val fileOutputStream: FileOutputStream = FileOutputStream(file)

                    var fileSize: Long = 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        fileSize = connection.contentLengthLong
                    }

                    val buffer = ByteArray(1024 * 1024)
                    var bytesRead: Int
                    var totalByteRead: Long = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead)
                        totalByteRead += bytesRead.toLong()
                        if (fileSize >= 0) {
                            val progress = (totalByteRead * 100 / fileSize).toInt()
                            emit(DownloadStatus.Progress(progress))
                        }
                    }

                    inputStream.close()
                    connection.disconnect()

                    emit(DownloadStatus.Success)
                } catch (e: Exception) {
                    if (isFileExist(fileDir, fileName)) {
                        deleteDownloadedVideoFile(context, videoItem)
                    }
                    emit(DownloadStatus.Error("Download Video File Fail !"))
                }
                // If Fail
            }
        }

        @OptIn(DelicateCoroutinesApi::class)
        suspend fun downloadVideoFile(context: Context, videoItem: VideoItem) = GlobalScope.async {
            val url = URL(videoItem.videoUrl)
            val fileName = getFileNameFromId(videoItem.id!!)
            val fileDir = getDownloadVideoFileDirectory(context)
            val file = getFileNameWithPath(fileDir, fileName)

            withContext(Dispatchers.IO) {
                url.openStream()
            }.use { inp ->
                BufferedInputStream(inp).use { bis ->
                    FileOutputStream(file).use { fos ->
                        val data = ByteArray(1024)
                        var count: Int
                        while (bis.read(data, 0, 1024).also { count = it } != -1) {
                            fos.write(data, 0, count)
                        }
                    }
                }
            }
            true
        }

//        fun downloadVideoFile(context: Context, videoItem: VideoItem) {
//            val fileName = getFileNameFromId(videoItem.id)
//            val fileDir = getDownloadVideoFileDirectory(context)
//            val file = getFileNameWithPath(fileDir, fileName)
//
//            if (isFileExist(fileDir, fileName)) {
//                return
//            }
//            try {
//                DownloadFileTask(videoItem.videoUrl, file).execute()
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }

        fun getVideoFileSizeFromUrl(url:String): Long {
            val vUrl = URL(url)
            val connection = vUrl.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("server error: " + connection.responseCode + ", " + connection.responseMessage)
            }
            var fileSize: Long = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fileSize = connection.contentLengthLong
            }
            return fileSize
        }

        fun formattedFileSizeToDisplay(fileSize: Long):String{
            val fileSizeInKB = fileSize/1024
            return if(fileSizeInKB<1024){
                "$fileSizeInKB kB"
            }else{
                val fileSizeInMB = fileSizeInKB/1024
                if(fileSizeInMB<1024){
                    "$fileSizeInMB MB"
                }else{
                    val fileSizeInGB = fileSizeInMB/1024
                    "$fileSizeInGB MB"
                }
            }
        }

        fun deleteDownloadedVideoFile(context: Context, videoItem: VideoItem) {
            val fileName = getFileNameFromId(videoItem.id)
            val fileDir = getDownloadVideoFileDirectory(context)
            val file = getFileNameWithPath(fileDir, fileName)
            file.delete()
        }

        fun getFileNameWithPath(fileDirectory: File, fileName: String): File {
            return File(fileDirectory, fileName)
        }

        fun getFileNameFromId(id: Int): String {
            return "$id.mp4"
        }

        fun getDownloadVideoFileDirectory(context: Context): File {
            return context.filesDir
        }

        fun isFileExist(fileDirectory: File, fileName: String): Boolean {
            val fileList = getFileListOfDirectory(fileDirectory)
            return fileList.contains(fileName)
        }

        fun getFileListOfDirectory(folder: File): List<String> {
            val list = folder.listFiles()
            val fileList = mutableListOf<String>()
            if (list != null) {
                for (f in list) {
                    fileList.add(f.name)
                }
            }
            return fileList
        }
    }
}