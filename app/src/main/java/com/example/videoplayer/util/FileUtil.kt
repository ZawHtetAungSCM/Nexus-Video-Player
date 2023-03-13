package com.example.videoplayer.util

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.videoplayer.Constants
import com.example.videoplayer.Constants.Companion.AES_ALGORITHM
import com.example.videoplayer.Constants.Companion.AES_TRANSFORMATION
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.ItemDto
import com.example.videoplayer.model.FileType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class FileUtil {
    companion object {

        /**
         * Video File
         */
        suspend fun downloadEncryptedVideoFile(
            context: Context,
            videoItem: ItemDto
        ): Flow<DownloadStatus> {
            val url = URL(Constants.API + "video/" + videoItem.id)
            val file = getFileForItem(context, videoItem)

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
                    deleteFileIfExist(file)
                    emit(DownloadStatus.Error("Download Video File Fail !"))
                }
                // If Fail
            }
        }

        suspend fun decryptDownloadedVideoFile(
            context: Context,
            videoItem: ItemDto
        ): Flow<DownloadStatus> {
            val file = getFileForItem(context, videoItem)
            val tempFile = getTemporaryFile(context,videoItem.fileType)

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
                    deleteFileIfExist(file)
                    emit(DownloadStatus.Error("Decrypt Video File Fail !"))
                }
                // If Fail
            }
        }

        /**
         * Others File(Audio,Pdf,Csv)
         */
        suspend fun downloadFileFromUrl(
            context: Context,
            item: ItemDto
        ): Flow<DownloadStatus> {
            val url = URL(item.url)
            //TODO::Check File Type
            val file = getFileForItem(context, item)

            return flow {
                try {
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connect()

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("server error: " + connection.responseCode + ", " + connection.responseMessage)
                    }

                    val inputStream = connection.inputStream
                    val fileOutputStream = FileOutputStream(file)
                    val cipherOutputStream =
                        CipherOutputStream(fileOutputStream, tempEncryptCipher())

                    var fileSize: Long = 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        fileSize = connection.contentLengthLong
                    }

                    val buffer = ByteArray(1024 * 1024)
                    var bytesRead: Int
                    var totalByteRead: Long = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        //fileOutputStream.write(buffer, 0, bytesRead)
                        cipherOutputStream.write(buffer, 0, bytesRead)
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
                    deleteFileIfExist(file)
                    emit(DownloadStatus.Error("Download ${item.fileType} File Fail !"))
                }
                // If Fail
            }
        }

        suspend fun decryptDownloadedFile(
            context: Context,
            item: ItemDto,
        ): Flow<DownloadStatus> {
            val file = getFileForItem(context, item)
            val tempFile = getTemporaryFile(context, item.fileType)

            return flow {
                try {
                    val inputStream = FileInputStream(file)
                    val fileOutputStream = FileOutputStream(tempFile)
                    val cipherOutputStream =
                        CipherOutputStream(fileOutputStream, tempDecryptCipher())

                    var fileSize: Long = 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        fileSize = file.length()
                    }

                    val buffer = ByteArray(Constants.CHUNKS_SIZE)
                    var bytesRead: Int
                    var totalByteRead: Long = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        cipherOutputStream.write(buffer, 0, bytesRead)
                        totalByteRead += bytesRead.toLong()
                        if (fileSize >= 0) {
                            val progress = (totalByteRead * 100 / fileSize).toInt()
                            emit(DownloadStatus.Progress(progress))
                        }
                    }
                    emit(DownloadStatus.Success)
                } catch (e: Exception) {
                    deleteFileIfExist(file)
                    emit(DownloadStatus.Error("Decrypt ${item.fileType} File Fail !"))
                }
                // If Fail
            }
        }

        /**
         * Delete File
         */
        fun deleteFileOfItem(context: Context, videoItem: ItemDto){
            val file = getFileForItem(context, videoItem)
            if(file.exists()){
                file.delete()
            }
        }

        private fun deleteFileIfExist(file:File){
            if (file.exists()) file.delete()
        }

        fun getFileForItem(context: Context, item: ItemDto): File {
            val fileDirectory = getDownloadFileDirectory(context)
            val fileName = getFileNameForItem(item)
            return File(fileDirectory, fileName)
        }

        fun getFileNameForItem(item: ItemDto): String {
            return getFileNameFromId(item.id,item.fileType)
        }

        private fun getFileNameFromId(id: Int, fileType: FileType): String {
            return "$id.${fileType.ext}"
        }

        fun getTemporaryFile(context: Context, fileType: FileType): File {
            val fileDir = getDownloadFileDirectory(context)
            val fileName = "${Constants.TEMP_FILE_NAME}.${fileType.ext}"

            return File(fileDir, fileName)
        }

        fun getDownloadFileDirectory(context: Context): File {
            return context.filesDir
        }

        /**
         * Others
         */
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

        fun getDownloadVideoFileSize(context: Context, item:ItemDto ): Long {
            val file = getFileForItem(context, item)
            return file.length()
        }

        @OptIn(DelicateCoroutinesApi::class)
        @RequiresApi(Build.VERSION_CODES.N)
        fun getVideoFileSizeFromUrl(id: Int) = GlobalScope.async {
            val url = URL(Constants.API + "video/" + id)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("server error: " + connection.responseCode + ", " + connection.responseMessage)
            }
            connection.contentLengthLong
        }

        @RequiresApi(Build.VERSION_CODES.N)
        suspend fun getFileSizeFromUrl(url: String) =
            withContext(Dispatchers.Default) {
                val itemUrl = URL(url)
                val connection = itemUrl.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("server error: " + connection.responseCode + ", " + connection.responseMessage)
                }
                connection.contentLengthLong
            }

        /**
         * Temp
         */
        private fun tempEncryptCipher(): Cipher {
            val encryptionKey = Constants.ENCRYPT_KEY
            val tSecretKeySpec = SecretKeySpec(encryptionKey, AES_ALGORITHM)

            val encryptionIv = Constants.ENCRYPT_KEY
            val tIvParameterSpec = IvParameterSpec(encryptionIv)

            val tCipher = Cipher.getInstance(AES_TRANSFORMATION)
            tCipher.init(Cipher.ENCRYPT_MODE, tSecretKeySpec, tIvParameterSpec)
            return tCipher
        }

        private fun tempDecryptCipher(): Cipher {
            val encryptionKey = Constants.ENCRYPT_KEY
            val tSecretKeySpec = SecretKeySpec(encryptionKey, AES_ALGORITHM)

            val encryptionIv = Constants.ENCRYPT_KEY
            val tIvParameterSpec = IvParameterSpec(encryptionIv)

            val tCipher = Cipher.getInstance(AES_TRANSFORMATION)
            tCipher.init(Cipher.DECRYPT_MODE, tSecretKeySpec, tIvParameterSpec)
            return tCipher
        }

    }
}