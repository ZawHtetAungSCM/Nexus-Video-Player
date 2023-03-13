package com.example.videoplayer.activity

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.videoplayer.Constants
import com.example.videoplayer.R
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.ItemDto
import com.example.videoplayer.databinding.ActivityAudioPlayerBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.model.FileType
import com.example.videoplayer.util.FileUtil
import com.example.videoplayer.util.ProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioPlayerActivity : AppCompatActivity(),Runnable {

    companion object {
        const val AUDIO_ITEM = "audio_item"
        const val SEEK_DURATION = 15*1000 // 15sec
    }

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var binding: ActivityAudioPlayerBinding
    private lateinit var seekBar: SeekBar
    private var audioItem: ItemDto? = null
    private var audioFile: File? = null
    private var wasPlaying = false
    private var wasPause = false
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Constants.setCurrentActivity(this)
        progressDialog = ProgressDialog(this)

        seekBar = binding.seekbar

        audioItem = intent?.extras?.getSerializable(AUDIO_ITEM) as ItemDto?

        if (audioItem != null) {
            // Bind Data
            decryptDownloadedFileWithFlow(audioItem!!)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.audioCurrentTime.text = convertMilliSecToTimeFormat(progress)

//                if (progress > 0 && mediaPlayer!= null && !mediaPlayer?.isPlaying!!) {
//                    clearMediaPlayer()
//                    binding.btnPlay.setBackgroundResource(com.example.videoplayer.R.drawable.ic_pause)
//                    seekBar?.progress = 0
//                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (mediaPlayer.isPlaying) {
                    if (seekBar != null) {
                        mediaPlayer.seekTo(seekBar.progress)
                    }
                }
            }

        })
    }

    private fun decryptDownloadedFileWithFlow(audioItem: ItemDto) {
        try {
            val context = Constants.getContextWithHandler(this) {
                progressDialog.hide()
                showToast("Network Connection Error")
            }
            CoroutineScope(context).launch {
                progressDialog.show()
                FileUtil.decryptDownloadedFile(this@AudioPlayerActivity, audioItem).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadStatus.Success -> {
                                audioFile =  FileUtil.getTemporaryFile(this@AudioPlayerActivity, FileType.AUDIO)
                                playAudioFile()
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

    private fun playAudioFile(){
        binding.audioThumbnail.load(audioItem?.thumbnail)
        binding.audioTitle.text = audioItem?.title

        mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(audioFile?.path)
        mediaPlayer.prepare()
        val mDuration = mediaPlayer.duration
        seekBar.max = mDuration
        binding.audioDuration.text = convertMilliSecToTimeFormat(mDuration)
        binding.audioCurrentTime.text = convertMilliSecToTimeFormat(0)

        binding.btnPlay.setOnClickListener {
            playSong()
        }

        binding.btnForward.setOnClickListener{
            seekForward()
        }
        binding.btnRewind.setOnClickListener {
            seekBackward()
        }
    }

    private fun playSong() {
        if(!wasPlaying){
            if (!wasPause) {
                mediaPlayer.start()
                mediaPlayer.setOnCompletionListener {
                    it.release()
                    mediaPlayer = MediaPlayer()
                    mediaPlayer.setDataSource(audioFile?.path)
                    mediaPlayer.prepare()
                }
            }else{
                mediaPlayer.start()
                wasPause = false
            }
            wasPlaying = true
            Thread(this).start()
            binding.btnPlay.setBackgroundResource(R.drawable.ic_pause)
        }else{
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                wasPause = true
            }
            wasPlaying = false
            binding.btnPlay.setBackgroundResource(R.drawable.ic_play)
        }
    }

    private fun seekForward(){
        if(wasPlaying){
            if (mediaPlayer.isPlaying) {
                val cPosition = mediaPlayer.currentPosition + SEEK_DURATION
                if(cPosition < mediaPlayer.duration){
                    mediaPlayer.seekTo(cPosition)
                }else{
                    mediaPlayer.seekTo(mediaPlayer.duration)
                }
            }
        }
    }

    private fun seekBackward(){
        if(wasPlaying){
            if (mediaPlayer.isPlaying) {
                val cPosition = mediaPlayer.currentPosition - SEEK_DURATION
                if(cPosition > 0 ){
                    mediaPlayer.seekTo(cPosition)
                }else{
                    mediaPlayer.seekTo(0)
                }
            }
        }
    }

    override fun run() {
        if(wasPlaying){
            var currentPosition = mediaPlayer.currentPosition
            val total = mediaPlayer.duration
            while (mediaPlayer.isPlaying && currentPosition < total) {
                currentPosition = try {
                    Thread.sleep(1000)
                    mediaPlayer.currentPosition
                } catch (e: InterruptedException) {
                    return
                } catch (e: java.lang.Exception) {
                    return
                }
                seekBar.progress = currentPosition
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearMediaPlayer()
    }

    private fun clearMediaPlayer() {
        mediaPlayer.stop()
        mediaPlayer.release()
    }

    @SuppressLint("SimpleDateFormat")
    private fun convertMilliSecToTimeFormat(millis: Int): String{
        val secTotal:Int = millis/1000
        val min:Int = secTotal/60
        val sec = secTotal%60
        return if(sec<10) "$min:0$sec" else "$min:$sec"
    }
}