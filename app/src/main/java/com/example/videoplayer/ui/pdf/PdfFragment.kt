package com.example.videoplayer.ui.pdf

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
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.coroutineScope
import com.example.videoplayer.Constants
import com.example.videoplayer.MainApplication
import com.example.videoplayer.MainRepository
import com.example.videoplayer.activity.PdfViewActivity
import com.example.videoplayer.adapter.VideoListAdapter
import com.example.videoplayer.data.dto.*
import com.example.videoplayer.databinding.FragmentPdfBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.model.FileType
import com.example.videoplayer.util.FileUtil
import com.example.videoplayer.util.ItemUtil
import com.example.videoplayer.util.ProgressDialog
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import java.nio.charset.StandardCharsets

class PdfFragment : Fragment(), VideoListAdapter.OnItemClick,
    VideoListAdapter.OnItemDownloadClickListener, VideoListAdapter.OnItemDeleteClickListener {

    private var _binding: FragmentPdfBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: MainRepository
    private lateinit var ctx: Activity
    private lateinit var progressDialog: ProgressDialog
    private lateinit var listAdapter: VideoListAdapter

    private val pdfItemList = mutableListOf<ItemDto>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ctx = requireActivity()
        listAdapter =
            VideoListAdapter(ctx, this@PdfFragment, this@PdfFragment, this@PdfFragment)
        Constants.setCurrentActivity(requireActivity())
        ItemUtil.setCurrentActivity(requireActivity())
        progressDialog = ProgressDialog(requireActivity())
        repository = (ctx.application as MainApplication).repository

        pdfItemList.addAll(
            ItemUtil.fillListWithSamples(
                requireContext(),
                "pdf_sample_list.json",
                FileType.PDF
            )
        )
        getAndUpdateFileSize()

        binding.recyclerView.adapter = listAdapter
        listAdapter.submitList(pdfItemList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getAndUpdateFileSize() {
        if (pdfItemList != null) {
            pdfItemList?.forEachIndexed { index, item ->
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
    }

    private fun updateFileSizeOfItem(position: Int,fileSize:Long){
        if (fileSize > 0){
            val fileSizeString =
                FileUtil.formattedFileSizeToDisplay(fileSize)
            pdfItemList!![position].fileSize = fileSizeString
            listAdapter.notifyItemChanged(position)
        }
    }

    override fun onItemClick(videoItem: ItemDto) {
        if (videoItem.downloaded) {
            val intent = Intent(ctx, PdfViewActivity::class.java)
            intent.putExtra(PdfViewActivity.PDF_ITEM, videoItem)
            startActivity(intent)
        } else {
            ctx.showToast("PDF is not Downloaded")
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
                    pdfItemList?.get(position)!!
                ).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadStatus.Success -> {
                                pdfItemList?.get(position)?.downloaded = true
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
            FileUtil.deleteFileOfItem(ctx, pdfItemList[position])
            ctx.showToast("Download PDF File Fail !")
        }
    }

    override fun onItemDeleteClick(position: Int) {
        showDeleteConfirmDialog(position)
    }

    private fun showDeleteConfirmDialog(position: Int) {
        val vTitle = pdfItemList?.get(position)?.title
        AlertDialog.Builder(ctx)
            .setCancelable(true)
            .setTitle("Delete PDF")
            .setMessage("(${vTitle}) will be deleted from download.")
            .setPositiveButton(
                "Confirm"
            ) { _, _ ->
                deletePDFItem(position)
            }
            .setNegativeButton("No")
            { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deletePDFItem(position: Int) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                withContext(Dispatchers.Main) {
                    FileUtil.deleteFileOfItem(ctx, pdfItemList[position])
                    pdfItemList?.get(position)?.downloaded = false
                    listAdapter.notifyItemChanged(position)
                    ctx.showToast("PDF File is Deleted")
                }
            }
        } catch (e: Exception) {
            ctx.showToast("There was Error in File Deleting")
        }
    }
}