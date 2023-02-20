package com.example.videoplayer.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.videoplayer.adapter.VideoListAdapter
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.VideoItem
import com.example.videoplayer.databinding.ActivityMainBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.util.FileUtil
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(), VideoListAdapter.OnItemClick,
    VideoListAdapter.OnItemDownloadClickListener, VideoListAdapter.OnItemDeleteClickListener {

    private lateinit var binding: ActivityMainBinding
    var videoList: List<VideoItem>? = null
    private val listAdapter = VideoListAdapter(this, this, this, this)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoList = fillListWithSamples(getLocalSampleList())

        binding.recyclerView.adapter = listAdapter
        listAdapter.submitList(videoList)
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
                    val videoItem = VideoItem(
                        id = jsonObject.getString("id").toInt(),
                        title = jsonObject.getString("title"),
                        videoUrl = jsonObject.getString("videoUrl"),
                        videoThumbnail = jsonObject.getString("videoThumbnail"),
                        downloaded = fileList.contains(fileName)
                    )
                    videoItemList.add(videoItem)

                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

        }
        return videoItemList
    }

    override fun onItemClick(videoItem: VideoItem) {
        if (videoItem.downloaded) {
            val intent = Intent(this@MainActivity, PlayerActivity::class.java)
            intent.putExtra(PlayerActivity.VIDEO_ITEM, videoItem)
            startActivity(intent)
        } else {
            showToast("Video is not Downloaded")
        }
    }

    @SuppressLint("Range")
    override fun onItemDownloadClick(position: Int, progressText: TextView) {
//        downloadVideFile(position,progressText)
        dowloadFileWithFloaw(position,progressText)
    }

    private fun dowloadFileWithFloaw(position: Int,progressText: TextView) {
        if (isNetworkAvailable()) {
            try{
            CoroutineScope(Dispatchers.IO).launch {
                FileUtil.downloadVideoFileWithFlow(this@MainActivity, videoList?.get(position)!!).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadStatus.Success -> {
                                Log.d("TestingTT","downloadVideoFile Success: $it")
                                videoList?.get(position)?.downloaded = true
                                updateListDataSet()
                            }
                            is DownloadStatus.Error -> {
                                showToast(it.message)
                                updateListDataSet()
                            }
                            is DownloadStatus.Progress -> {
                                progressText.text = "${it.progress}%"
                            }
                        }
                    }
                }
            }
            }catch (e:Exception){
                val fileName = FileUtil.getFileNameFromId(videoList?.get(position)?.id!!)
                val fileDir = FileUtil.getDownloadVideoFileDirectory(this@MainActivity)
                if (FileUtil.isFileExist(fileDir, fileName)) {
                    FileUtil.deleteDownloadedVideoFile(this@MainActivity, videoList?.get(position)!!)
                }
                showToast("Download Video File Fail !")
            }

        } else {
            showToast("Network is not Available")
            updateListDataSet()
        }
    }

    private fun downloadVideFile(position: Int,progressText: TextView) {
        if (isNetworkAvailable()) {
            CoroutineScope(Dispatchers.IO).launch {
                withContext(Dispatchers.Main) {
//                    val result = downloadingProcess(position).await()
                    progressText.text = "0"
                    val result = FileUtil.downloadVideoFile(
                        this@MainActivity,
                        videoList?.get(position)!!
                    ).await()

                    progressText.text = "100"
                    videoList?.get(position)?.downloaded = true
                    updateListDataSet()
                }
            }
        } else {
            showToast("Network is not Available")
            updateListDataSet()
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
                    updateListDataSet()
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