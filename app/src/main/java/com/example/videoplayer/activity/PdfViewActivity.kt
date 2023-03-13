package com.example.videoplayer.activity

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.danjdt.pdfviewer.PdfViewer
import com.danjdt.pdfviewer.interfaces.OnErrorListener
import com.danjdt.pdfviewer.interfaces.OnPageChangedListener
import com.danjdt.pdfviewer.utils.PdfPageQuality
import com.danjdt.pdfviewer.view.PdfViewerRecyclerView
import com.example.videoplayer.Constants
import com.example.videoplayer.R
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.ItemDto
import com.example.videoplayer.databinding.ActivityPdfviewBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.model.FileType
import com.example.videoplayer.util.FileUtil
import com.example.videoplayer.util.ProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class PdfViewActivity : AppCompatActivity(), OnPageChangedListener, OnErrorListener {

    companion object {
        const val PDF_ITEM = "pdf_item"
    }
    private lateinit var binding: ActivityPdfviewBinding
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Constants.setCurrentActivity(this)
        progressDialog = ProgressDialog(this)

        val pdfItem = intent?.extras?.getSerializable(PDF_ITEM) as ItemDto?
        if (pdfItem != null) {
            decryptDownloadedFileWithFlow(pdfItem)
        }
    }

    private fun decryptDownloadedFileWithFlow(pdfItem: ItemDto) {
        try {
            val context = Constants.getContextWithHandler(this) {
                progressDialog.hide()
                showToast("Network Connection Error")
            }
            CoroutineScope(context).launch {
                progressDialog.show()
                FileUtil.decryptDownloadedFile(this@PdfViewActivity, pdfItem).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadStatus.Success -> {
                                openPdfFile()
                            }
                            is DownloadStatus.Error -> {
                                showToast(it.message)
                            }
                            is DownloadStatus.Progress -> {
                                Log.d("TestingTT", "Decrypting : ${it.progress}%")
                            }
                        }
                    }
                }
                progressDialog.hide()
            }
        } catch (e: Exception) {
            progressDialog.hide()
            showToast("Somethings Wrong")
            finish()
        }
    }

    private fun openPdfFile(){
        val filePath =  FileUtil.getTemporaryFile(this@PdfViewActivity, FileType.PDF)
        PdfViewer.Builder(binding.rootView)
            .view(PdfViewerRecyclerView(this))
            .setMaxZoom(3f)
            .setZoomEnabled(true)
            .quality(PdfPageQuality.QUALITY_1080)
            .setOnErrorListener(this)
            .setOnPageChangedListener(this)
            .build()
            .load(filePath)
    }

    override fun onPageChanged(page: Int, total: Int) {
        binding.tvCounter.text = getString(R.string.pdf_page_counter, page, total)
    }

    override fun onFileLoadError(e: Exception) {
        //Handle error ...
        e.printStackTrace()
    }

    override fun onAttachViewError(e: Exception) {
        //Handle error ...
        e.printStackTrace()
    }

    override fun onPdfRendererError(e: IOException) {
        //Handle error ...
        e.printStackTrace()
    }
}
