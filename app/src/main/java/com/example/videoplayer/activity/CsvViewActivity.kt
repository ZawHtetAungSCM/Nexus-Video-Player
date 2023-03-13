package com.example.videoplayer.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.videoplayer.Constants
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.ItemDto
import com.example.videoplayer.databinding.ActivityCsvViewBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.model.FileType
import com.example.videoplayer.util.FileUtil
import com.example.videoplayer.util.ProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class CsvViewActivity : AppCompatActivity() {

    companion object {
        const val CSV_ITEM = "csv_item"
    }

    private lateinit var binding: ActivityCsvViewBinding
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCsvViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Constants.setCurrentActivity(this)
        progressDialog = ProgressDialog(this)

        val csvItem = intent?.extras?.getSerializable(CSV_ITEM) as ItemDto?
        if (csvItem != null) {
            decryptDownloadedFileWithFlow(csvItem)
        }
    }

    private fun decryptDownloadedFileWithFlow(csvItem: ItemDto) {
        try {
            val context = Constants.getContextWithHandler(this) {
                progressDialog.hide()
                showToast("Network Connection Error")
            }
            CoroutineScope(context).launch {
                progressDialog.show()
                FileUtil.decryptDownloadedFile(this@CsvViewActivity, csvItem).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadStatus.Success -> {
                                openCsvFileViewer()
                            }
                            is DownloadStatus.Error -> {
                                showToast(it.message)
                            }
                            is DownloadStatus.Progress -> {
                                // Log.d("TestingTT", "Decrypting : ${it.progress}%")
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

    private fun openCsvFileViewer() {
        val file = FileUtil.getTemporaryFile(this@CsvViewActivity, FileType.CSV)
        val rowArr = mutableListOf<String>()

        try {
            val br = BufferedReader(FileReader(file))
            var line: String?
            line = br.readLine()
            //Header
            if (line != null) {
                val hLineSplit = line.split(",")
                val headerArr = mutableListOf<String>()
                for (i: Int in hLineSplit.indices) {
                    headerArr.add("<th style='border: 1px solid gray; text-align: left;'>${hLineSplit[i]}</th>")
                }
                rowArr.add("<tr>${headerArr.joinToString("")}</tr>")
            }

            while (br.readLine().also { line = it } != null) {
                val lineSplit = line?.split((",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)").toRegex())
                val colArr = mutableListOf<String>()
                for (i: Int in lineSplit?.indices!!) {
                    colArr.add(
                        "<td style='border: 1px solid gray; text-align: left;'>${
                            lineSplit[i].trim().removeSurrounding("\"")
                        }</td>"
                    )
                }
                rowArr.add("<tr>${colArr.joinToString("")}</tr>")
            }
            br.close()
        } catch (e: IOException) {
            //You'll need to add proper error handling here
        }

        val htmlString = """
                        <html>
                            <head>
                                <meta charset="UTF-8">
                                <title>CSV File</title>
                            </head>
                            <body>
                                <table style="border-collapse: collapse;">
                                    ${rowArr.joinToString("")}
                                </table>
                            </body>
                        </html>
                    """.trimIndent()
        val webView = binding.webView
        webView.isScrollContainer = true
        webView.isScrollbarFadingEnabled = false
        webView.settings.builtInZoomControls = true
        webView.loadData(htmlString, "text/html", "UTF-8")

    }

    override fun onDestroy() {
        super.onDestroy()
        deleteTempFile()
    }

    private fun deleteTempFile() {
        val file = File(filesDir, "temp_play_file.csv")
        if (file.exists()) {
            file.delete()
        }
    }
}