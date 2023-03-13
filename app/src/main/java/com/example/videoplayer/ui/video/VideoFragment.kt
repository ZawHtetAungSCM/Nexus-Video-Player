package com.example.videoplayer.ui.video

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.videoplayer.Constants
import com.example.videoplayer.MainApplication
import com.example.videoplayer.MainRepository
import com.example.videoplayer.activity.VideoPlayerActivity
import com.example.videoplayer.adapter.VideoListAdapter
import com.example.videoplayer.data.dto.*
import com.example.videoplayer.databinding.FragmentVideoBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.model.FileType
import com.example.videoplayer.util.FileUtil
import com.example.videoplayer.util.ProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoFragment : Fragment(), VideoListAdapter.OnItemClick,
    VideoListAdapter.OnItemDownloadClickListener, VideoListAdapter.OnItemDeleteClickListener {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: MainRepository
    private lateinit var ctx: Activity
    private val videoItemList = mutableListOf<ItemDto>()
    private lateinit var progressDialog: ProgressDialog
    private lateinit var listAdapter: VideoListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ctx = requireActivity()
        listAdapter =
            VideoListAdapter(ctx, this@VideoFragment, this@VideoFragment, this@VideoFragment)
        Constants.setCurrentActivity(requireActivity())
        progressDialog = ProgressDialog(requireActivity())
        repository = (ctx.application as MainApplication).repository

        binding.recyclerView.adapter = listAdapter
        getVideoList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getVideoList() {
        val context = Constants.getContextWithHandler(requireActivity()) {
            progressDialog.hide()
            requireActivity().showToast("Network Connection Error")
        }
        CoroutineScope(context).launch {
            progressDialog.show()
            when (val result = repository.getVideoList()) {
                is Result.Success -> {
                    val videoList = (result.data as ItemList).data
                    updateFileDownloadStatus(videoList)
                }
                else -> requireActivity().showToast("Error")
            }
            progressDialog.hide()
        }
    }

    private fun updateFileDownloadStatus(videoList: List<ItemDto>) {
        videoItemList?.clear()
        val vDownloadFilePath = FileUtil.getDownloadFileDirectory(ctx)
        val fileList = FileUtil.getFileListOfDirectory(vDownloadFilePath)
        videoList.forEach { v ->
            v.fileType = FileType.VIDEO
            val itemFileName = FileUtil.getFileNameForItem(v)
            v.downloaded = fileList.contains(itemFileName)
            videoItemList.add(v)
        }
        listAdapter.submitList(videoItemList)
        getAndUpdateVideoFileSize()
    }

    private fun getAndUpdateVideoFileSize() {
        if (videoItemList != null) {
            videoItemList?.forEachIndexed { index, item ->
                if (item.downloaded) {
                    val fileSize = FileUtil.getDownloadVideoFileSize(ctx, item)
                    updateFileSizeOfItem(index,fileSize)
                } else {
                    if (Constants.checkNetwork()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            withContext(Dispatchers.Main) {
                                val fileSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    FileUtil.getVideoFileSizeFromUrl(item.id).await()
                                } else {
                                    0.toLong()
                                }
                                updateFileSizeOfItem(index,fileSize)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateFileSizeOfItem(position: Int,fileSize:Long){
        if (fileSize > 0){
            val fileSizeString =
                FileUtil.formattedFileSizeToDisplay(fileSize)
            videoItemList!![position].fileSize = fileSizeString
            listAdapter.notifyItemChanged(position)
        }
    }

    override fun onItemClick(videoItem: ItemDto) {
        if (videoItem.downloaded) {
            val intent =
                Intent(ctx, VideoPlayerActivity::class.java)
            intent.putExtra(VideoPlayerActivity.VIDEO_ITEM, videoItem)
            startActivity(intent)
        } else {
            ctx.showToast("Video is not Downloaded")
        }
    }

    override fun onItemDownloadClick(position: Int, progressText: TextView) {
        if (Constants.checkNetwork()) {
            downloadEncryptedFileWithFlow(position, progressText)
        } else {
            ctx.showToast("Network is not Available")
            listAdapter.notifyItemChanged(position)
        }
    }

    private fun downloadEncryptedFileWithFlow(position: Int, progressText: TextView) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                FileUtil.downloadEncryptedVideoFile(
                    ctx,
                    videoItemList?.get(position)!!
                ).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadStatus.Success -> {
                                videoItemList?.get(position)?.downloaded = true
                                listAdapter.notifyItemChanged(position)
                            }
                            is DownloadStatus.Error -> {
                                ctx.showToast(it.message)
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
            FileUtil.deleteFileOfItem(ctx,videoItemList[position])
            ctx.showToast("Download Video File Fail !")
        }
    }

    override fun onItemDeleteClick(position: Int) {
        showDeleteConfirmDialog(position)
    }

    private fun showDeleteConfirmDialog(position: Int) {
        val vTitle = videoItemList?.get(position)?.title
        AlertDialog.Builder(ctx)
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
                    FileUtil.deleteFileOfItem(
                        ctx,
                        videoItemList?.get(position)!!
                    )
                    videoItemList?.get(position)?.downloaded = false
                    listAdapter.notifyItemChanged(position)
                    ctx.showToast("Video File is Deleted")
                }
            }
        } catch (e: Exception) {
            ctx.showToast("There was Error in File Deleting")
        }
    }
}