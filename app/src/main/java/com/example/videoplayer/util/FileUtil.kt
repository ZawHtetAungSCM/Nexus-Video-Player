package com.example.videoplayer.util

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.videoplayer.Constants
import com.example.videoplayer.Constants.Companion.AES_ALGORITHM
import com.example.videoplayer.Constants.Companion.AES_TRANSFORMATION
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.VideoItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class FileUtil {
    companion object {

        suspend fun downloadEncryptedVideoFile(
            context: Context,
            videoItem: VideoItem
        ): Flow<DownloadStatus> {
            val url = URL(Constants.API + "video/" + videoItem.id)
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

        suspend fun decryptDownloadedVideoFile(
            context: Context,
            videoItem: VideoItem
        ): Flow<DownloadStatus> {

            val fileName = getFileNameFromId(videoItem.id!!)
            val fileDir = getDownloadVideoFileDirectory(context)
            val file = getFileNameWithPath(fileDir, fileName)

            val tempFile = getTempPlayFile(context)

            return flow {
                try {

                    val inputStream = FileInputStream(file)
                    val fileOutputStream = FileOutputStream(tempFile)

                    val encryptionKey = Constants.ENCRYPT_KEY
                    val tSecretKeySpec = SecretKeySpec(encryptionKey, AES_ALGORITHM)

                    val encryptionIv = ByteArray(16)
                    inputStream.read(encryptionIv) // 16 byte read
                    val tIvParameterSpec = IvParameterSpec(encryptionIv)
                    var fileSize: Long = 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        fileSize = file.length()
                    }

                    val buffer = ByteArray(Constants.CHUNKS_SIZE)
                    var bytesRead: Int
                    var totalByteRead: Long = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val dCipher = Cipher.getInstance(AES_TRANSFORMATION)
                        dCipher.init(Cipher.DECRYPT_MODE, tSecretKeySpec, tIvParameterSpec)
                        val cipherFileOutputStream = CipherOutputStream(fileOutputStream, dCipher)
                        cipherFileOutputStream.write(buffer, 0, bytesRead)
                        totalByteRead += bytesRead.toLong()
                        if (fileSize >= 0) {
                            val progress = (totalByteRead * 100 / fileSize).toInt()
                            emit(DownloadStatus.Progress(progress))
                        }
                    }
                    emit(DownloadStatus.Success)
                } catch (e: Exception) {
                    if (isFileExist(fileDir, fileName)) {
                        deleteDownloadedVideoFile(context, videoItem)
                    }
                    emit(DownloadStatus.Error("Decrypt Video File Fail !"))
                }
                // If Fail
            }
        }

        fun getDownloadVideoFileSize(context: Context, id: Int): Long {
            val fileName = getFileNameFromId(id)
            val fileDir = getDownloadVideoFileDirectory(context)
            val file = getFileNameWithPath(fileDir, fileName)
            return file.length()
        }

        @RequiresApi(Build.VERSION_CODES.N)
        suspend fun getVideoFileSizeFromUrlSus(id: Int) = GlobalScope.async {
            val url = URL(Constants.API + "video/" + id)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("server error: " + connection.responseCode + ", " + connection.responseMessage)
            }
            connection.contentLengthLong
        }

        fun formattedFileSizeToDisplay(fileSize: Long): String {
            val fileSizeInKB = fileSize / 1024
            return if (fileSizeInKB < 1024) {
                "$fileSizeInKB kB"
            } else {
                val fileSizeInMB = fileSizeInKB / 1024
                if (fileSizeInMB < 1024) {
                    "$fileSizeInMB MB"
                } else {
                    val fileSizeInGB = fileSizeInMB / 1024
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

        fun getTempPlayFile(context: Context): File {
            val fileDir = getDownloadVideoFileDirectory(context)
            return getFileNameWithPath(fileDir, "temp_play_file.mp4")
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