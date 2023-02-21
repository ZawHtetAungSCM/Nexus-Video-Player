package com.example.videoplayer.activity

import android.annotation.SuppressLint
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import com.example.videoplayer.adapter.VideoListAdapter
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.VideoItem
import com.example.videoplayer.databinding.ActivityMainBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.util.FileUtil
import com.example.videoplayer.util.FileUtil.Companion.formattedFileSizeToDisplay
import com.example.videoplayer.util.FileUtil.Companion.getVideoFileSizeFromUrlSus
import com.example.videoplayer.util.ProgressDialog
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(), VideoListAdapter.OnItemClick,
    VideoListAdapter.OnItemDownloadClickListener, VideoListAdapter.OnItemDeleteClickListener {

    private lateinit var binding: ActivityMainBinding
    var videoList: List<VideoItem>? = null
    private lateinit var progressDialog: ProgressDialog
    private val listAdapter = VideoListAdapter(this, this, this, this)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        progressDialog = ProgressDialog(this)

        videoList = fillListWithSamples(getLocalSampleList())

        binding.recyclerView.adapter = listAdapter
        listAdapter.submitList(videoList)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getAndUpdateVideoFileSize()
        }
    }

    // A method for converting local sample videos list to JSONArray
    private fun getLocalSampleList(): JSONArray? {
        val jsonArray: JSONArray = try {
            val inputStream = assets.open("sample_list.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
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

    private fun fillListWithSamples(jsonArray: JSONArray?): List<VideoItem> {
        val videoItemList = mutableListOf<VideoItem>()
        val fileList = FileUtil.getFileListOfDirectory(filesDir)
        if (jsonArray != null) {
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val fileName = FileUtil.getFileNameFromId(jsonObject.getString("id").toInt())
                    // TODO:: Check Validation
                    val videoItem = VideoItem(
                        id = jsonObject.getString("id").toInt(),
                        title = jsonObject.getString("title"),
                        videoUrl = jsonObject.getString("videoUrl"),
                        videoThumbnail = jsonObject.getString("videoThumbnail"),
                        downloaded = fileList.contains(fileName),
                        fileSize = ""
                    )
                    videoItemList.add(videoItem)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

        }
        return videoItemList
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getAndUpdateVideoFileSize() {
        if (videoList != null) {
            videoList?.forEachIndexed { index, item ->
                if (item.downloaded) {
                    val fileSize = FileUtil.getDownloadVideoFileSize(this, item.id)
                    if (fileSize > 0) {
                        val fileSizeString = FileUtil.formattedFileSizeToDisplay(fileSize)
                        videoList!![index].fileSize = fileSizeString
                        listAdapter.notifyItemChanged(index)
                    }
                } else {
                    if (isNetworkAvailable()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            withContext(Dispatchers.Main) {
                                val fileSize = getVideoFileSizeFromUrlSus(item.videoUrl).await()
                                if (fileSize > 0) {
                                    val fileSizeString = formattedFileSizeToDisplay(fileSize)
                                    videoList!![index].fileSize = fileSizeString
                                    listAdapter.notifyItemChanged(index)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onItemClick(videoItem: VideoItem) {
        if (videoItem.downloaded) {
            val intent =
                Intent(this@MainActivity, PlayerActivity::class.java)
            intent.putExtra(PlayerActivity.VIDEO_ITEM, videoItem)
            startActivity(intent)
        } else {
            showToast("Video is not Downloaded")
        }
    }

    @SuppressLint("Range")
    override fun onItemDownloadClick(position: Int, progressText: TextView) {
        if (isNetworkAvailable()) {
            downloadAndEncryptFileWithFlow(position, progressText)
        } else {
            showToast("Network is not Available")
            listAdapter.notifyItemChanged(position)
        }
    }

    private fun downloadAndEncryptFileWithFlow(position: Int, progressText: TextView) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                FileUtil.downloadEncryptedVideoFile(
                    this@MainActivity,
                    videoList?.get(position)!!
                ).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadStatus.Success -> {
                                Log.d("TestingTT", "downloadVideoFile Success: $it")
                                videoList?.get(position)?.downloaded = true
                                listAdapter.notifyItemChanged(position)
                            }
                            is DownloadStatus.Error -> {
                                showToast(it.message)
                                listAdapter.notifyItemChanged(position)
                            }
                            is DownloadStatus.Progress -> {
                                progressText.text = "${it.progress}%"
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val fileName = FileUtil.getFileNameFromId(videoList?.get(position)?.id!!)
            val fileDir = FileUtil.getDownloadVideoFileDirectory(this@MainActivity)
            if (FileUtil.isFileExist(fileDir, fileName)) {
                FileUtil.deleteDownloadedVideoFile(
                    this@MainActivity,
                    videoList?.get(position)!!
                )
            }
            showToast("Download Video File Fail !")
        }
    }

    /**
     * Check Network is available or not
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        var activeNetworkInfo: NetworkInfo? = null
        if (connectivityManager != null) {
            activeNetworkInfo = connectivityManager.activeNetworkInfo
        }
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    override fun onItemDeleteClick(position: Int) {
        showDeleteConfirmDialog(position)
    }

    private fun showDeleteConfirmDialog(position: Int) {
        val vTitle = videoList?.get(position)?.title
        AlertDialog.Builder(this@MainActivity)
            .setCancelable(true)
            .setTitle("Delete Video")
            .setMessage("(${vTitle}) video will be deleted from download.")
            .setPositiveButton(
                "Confirm"
            ) { _, _ ->
                deleteVideoItem(position)
            }
            .setNegativeButton("No")
            { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteVideoItem(position: Int) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                withContext(Dispatchers.Main) {
                    FileUtil.deleteDownloadedVideoFile(
                        this@MainActivity,
                        videoList?.get(position)!!
                    )
                    videoList?.get(position)?.downloaded = false
                    listAdapter.notifyItemChanged(position)
                    showToast("Video File is Deleted")
                }
            }
        } catch (e: Exception) {
            showToast("There was Error in File Deleting")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateListDataSet() {
        listAdapter.submitList(videoList)
        listAdapter.notifyDataSetChanged()
    }
}