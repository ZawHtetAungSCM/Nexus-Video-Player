package com.example.videoplayer.activity

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.videoplayer.Constants
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.ItemDto
import com.example.videoplayer.databinding.ActivityVideoPlayerBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.model.FileType
import com.example.videoplayer.util.FileUtil
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private var playerView: StyledPlayerView? = null
    private var videoFile: File? = null

    private var mPlayer: ExoPlayer? = null
    private var mPlayerPosition: Long = 0
    private var mPlayerStartOnPrepared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Constants.setCurrentActivity(this)
        window.decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        val videoItem = intent?.extras?.getSerializable(VIDEO_ITEM) as ItemDto?
        if (videoItem != null) {
            decryptDownloadedFileWithFlow(videoItem)
        }
        playerView = binding.playerView
    }

    private fun decryptDownloadedFileWithFlow(videoItem: ItemDto) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                FileUtil.decryptDownloadedVideoFile(this@VideoPlayerActivity, videoItem).collect {
                    withContext(Dispatchers.Main) {
                        binding.progressBarLayout.visibility = View.VISIBLE
                        when (it) {
                            is DownloadStatus.Success -> {
                                videoFile = FileUtil.getTemporaryFile(this@VideoPlayerActivity, videoItem.fileType)
                                restorePlayer()
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
                binding.progressBarLayout.visibility = View.INVISIBLE
            }
        } catch (e: Exception) {
            binding.progressBarLayout.visibility = View.INVISIBLE
            deleteTempFile()
            showToast("Somethings Wrong")
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (videoFile != null) {
            restorePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        deleteTempFile()
    }

    private fun deleteTempFile() {
        val file = FileUtil.getTemporaryFile(this@VideoPlayerActivity,FileType.VIDEO)
        if (file.exists()) {
            file.delete()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    // A method for restoring the player (should be called when application is resumed from minimized state for example)
    private fun restorePlayer() {
        if (mPlayer == null) {
            preparePlayer()
        }
    }

    // A method for preparing the player
    private fun preparePlayer() {
        if (videoFile != null) {
            mPlayer = ExoPlayer.Builder(this@VideoPlayerActivity)
                .build()
                .also { player ->
                    playerView!!.player = player
                    val uri = Uri.fromFile(videoFile)
                    val mItem = MediaItem.fromUri(uri)
                    player.setMediaItem(mItem)
                    player.playWhenReady = true
                    player.prepare()
                }
            if (mPlayerPosition != 0L) {
                mPlayer!!.seekTo(mPlayerPosition)
            }
        }
    }

    // A method for releasing the player (should be called when application is closed or minimized for example)
    private fun releasePlayer() {
        if (mPlayer != null) {
            mPlayerPosition = mPlayer!!.currentPosition
            mPlayer!!.release()
            mPlayer = null
            mPlayerStartOnPrepared = false
        } else {
            mPlayerPosition = 0
        }
    }

    companion object {
        const val VIDEO_ITEM = "video_item"
    }
}