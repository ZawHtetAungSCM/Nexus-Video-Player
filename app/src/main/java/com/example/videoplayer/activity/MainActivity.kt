package com.example.videoplayer.activity


import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.videoplayer.Constants
import com.example.videoplayer.MainApplication
import com.example.videoplayer.MainRepository
import com.example.videoplayer.adapter.VideoListAdapter
import com.example.videoplayer.data.dto.*
import com.example.videoplayer.databinding.ActivityMainBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.util.ProgressDialog
import kotlinx.coroutines.*
import com.example.videoplayer.util.FileUtil
import com.example.videoplayer.util.FileUtil.Companion.formattedFileSizeToDisplay
import com.example.videoplayer.util.FileUtil.Companion.getVideoFileSizeFromUrlSus

class MainActivity : AppCompatActivity(), VideoListAdapter.OnItemClick,
    VideoListAdapter.OnItemDownloadClickListener, VideoListAdapter.OnItemDeleteClickListener {

    private lateinit var repository: MainRepository
    private lateinit var binding: ActivityMainBinding
    private val videoItemList = mutableListOf<VideoItem>()
    private lateinit var progressDialog: ProgressDialog
    private val listAdapter = VideoListAdapter(this, this, this, this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Constants.setCurrentActivity(this)
        progressDialog = ProgressDialog(this)
        repository = (application as MainApplication).repository

        binding.recyclerView.adapter = listAdapter

        getVideoList()
    }

    private fun getVideoList() {
        val context = Constants.getContextWithHandler(this) {
            progressDialog.hide()
            showToast("Network Connection Error")
        }
        CoroutineScope(context).launch {
            progressDialog.show()
            when (val result = repository.getVideoList()) {
                is Result.Success -> {
                    val videoList = (result.data as VideoList).data
                    changeVideoDataToItem(videoList)
                }
                else -> showToast("Error")
            }
            progressDialog.hide()
        }
    }

    private fun changeVideoDataToItem(videoList: List<VideoData>) {
        videoItemList?.clear()
        val fileList = FileUtil.getFileListOfDirectory(filesDir)
        videoList.forEach { v ->
            val fileName = FileUtil.getFileNameFromId(v.id)
            val tempItem = VideoItem(
                id = v.id,
                title = v.title,
                thumbnail = v.thumbnail,
                downloaded = fileList.contains(fileName),
                fileSize = ""
            )
            videoItemList.add(tempItem)
        }
        listAdapter.submitList(videoItemList)
        getAndUpdateVideoFileSize()
    }

    private fun getAndUpdateVideoFileSize() {
        if (videoItemList != null) {
            videoItemList?.forEachIndexed { index, item ->
                if (item.downloaded) {
                    val fileSize = FileUtil.getDownloadVideoFileSize(this, item.id)
                    if (fileSize > 0) {
                        val fileSizeString = FileUtil.formattedFileSizeToDisplay(fileSize)
                        videoItemList!![index].fileSize = fileSizeString
                        listAdapter.notifyItemChanged(index)
                    }
                } else {
                    if (Constants.checkNetwork()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            withContext(Dispatchers.Main) {
                                val fileSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    getVideoFileSizeFromUrlSus(item.id).await()
                                } else {
                                    0.toLong()
                                }
                                if (fileSize > 0) {
                                    val fileSizeString = formattedFileSizeToDisplay(fileSize)
                                    videoItemList!![index].fileSize = fileSizeString
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

    override fun onItemDownloadClick(position: Int, progressText: TextView) {
        if (Constants.checkNetwork()) {
            downloadEncryptedFileWithFlow(position, progressText)
        } else {
            showToast("Network is not Available")
            listAdapter.notifyItemChanged(position)
        }
    }

    private fun downloadEncryptedFileWithFlow(position: Int, progressText: TextView) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                FileUtil.downloadEncryptedVideoFile(
                    this@MainActivity,
                    videoItemList?.get(position)!!
                ).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadStatus.Success -> {
                                Log.d("TestingTT", "downloadVideoFile Success: $it")
                                videoItemList?.get(position)?.downloaded = true
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
            val fileName = FileUtil.getFileNameFromId(videoItemList?.get(position)?.id!!)
            val fileDir = FileUtil.getDownloadVideoFileDirectory(this@MainActivity)
            if (FileUtil.isFileExist(fileDir, fileName)) {
                FileUtil.deleteDownloadedVideoFile(
                    this@MainActivity,
                    videoItemList?.get(position)!!
                )
            }
            showToast("Download Video File Fail !")
        }
    }

    override fun onItemDeleteClick(position: Int) {
        showDeleteConfirmDialog(position)
    }

    private fun showDeleteConfirmDialog(position: Int) {
        val vTitle = videoItemList?.get(position)?.title
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
                        videoItemList?.get(position)!!
                    )
                    videoItemList?.get(position)?.downloaded = false
                    listAdapter.notifyItemChanged(position)
                    showToast("Video File is Deleted")
                }
            }
        } catch (e: Exception) {
            showToast("There was Error in File Deleting")
        }
    }

}