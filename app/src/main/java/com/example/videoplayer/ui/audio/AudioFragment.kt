package com.example.videoplayer.ui.audio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.videoplayer.Constants
import com.example.videoplayer.MainApplication
import com.example.videoplayer.MainRepository
import com.example.videoplayer.activity.AudioPlayerActivity
import com.example.videoplayer.adapter.VideoListAdapter
import com.example.videoplayer.data.dto.*
import com.example.videoplayer.databinding.FragmentAudioBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.model.FileType
import com.example.videoplayer.util.FileUtil
import com.example.videoplayer.util.ItemUtil
import com.example.videoplayer.util.ProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioFragment : Fragment(), VideoListAdapter.OnItemClick,
    VideoListAdapter.OnItemDownloadClickListener, VideoListAdapter.OnItemDeleteClickListener {

    private var _binding: FragmentAudioBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: MainRepository
    private lateinit var ctx: Activity
    private lateinit var progressDialog: ProgressDialog
    private lateinit var listAdapter: VideoListAdapter

    private val audioItemList = mutableListOf<ItemDto>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ctx = requireActivity()
        listAdapter =
            VideoListAdapter(ctx, this@AudioFragment, this@AudioFragment, this@AudioFragment)
        Constants.setCurrentActivity(requireActivity())
        ItemUtil.setCurrentActivity(requireActivity())
        progressDialog = ProgressDialog(requireActivity())
        repository = (ctx.application as MainApplication).repository

        audioItemList.addAll(
            ItemUtil.fillListWithSamples(
                requireContext(),
                "audio_sample_list.json",
                FileType.AUDIO
            )
        )
        getAndUpdateFileSize()

        binding.recyclerView.adapter = listAdapter
        listAdapter.submitList(audioItemList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getAndUpdateFileSize() {
        audioItemList.forEachIndexed { index, item ->
            if (item.downloaded) {
                val fileSize = FileUtil.getDownloadVideoFileSize(ctx, item)
                updateFileSizeOfItem(index,fileSize)
            } else {
                if (Constants.checkNetwork()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        withContext(Dispatchers.Main) {
                            val fileSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                FileUtil.getFileSizeFromUrl(item.url)
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

    private fun updateFileSizeOfItem(position: Int,fileSize:Long){
        if (fileSize > 0){
            val fileSizeString =
                FileUtil.formattedFileSizeToDisplay(fileSize)
            audioItemList[position].fileSize = fileSizeString
            listAdapter.notifyItemChanged(position)
        }
    }

    override fun onItemClick(videoItem: ItemDto) {
        if (videoItem.downloaded) {
            val intent = Intent(ctx, AudioPlayerActivity::class.java)
            intent.putExtra(AudioPlayerActivity.AUDIO_ITEM, videoItem)
            startActivity(intent)
        } else {
            ctx.showToast("Audio File is not Downloaded")
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
                FileUtil.downloadFileFromUrl(
                    ctx,
                    audioItemList.get(position)
                ).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadStatus.Success -> {
                                audioItemList[position].downloaded = true
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
            FileUtil.deleteFileOfItem(ctx, audioItemList[position])
            ctx.showToast("Download Audio File Fail !")
        }
    }

    override fun onItemDeleteClick(position: Int) {
        showDeleteConfirmDialog(position)
    }

    private fun showDeleteConfirmDialog(position: Int) {
        val vTitle = audioItemList.get(position)?.title
        AlertDialog.Builder(ctx)
            .setCancelable(true)
            .setTitle("Delete Video")
            .setMessage("(${vTitle}) video will be deleted from download.")
            .setPositiveButton(
                "Confirm"
            ) { _, _ ->
                deleteAudioItem(position)
            }
            .setNegativeButton("No")
            { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteAudioItem(position: Int) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                withContext(Dispatchers.Main) {
                    FileUtil.deleteFileOfItem(ctx, audioItemList[position])
                    audioItemList[position].downloaded = false
                    listAdapter.notifyItemChanged(position)
                    ctx.showToast("Audio File is Deleted")
                }
            }
        } catch (e: Exception) {
            ctx.showToast("There was Error in File Deleting")
        }
    }
}