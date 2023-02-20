package com.example.videoplayer.activity

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.videoplayer.data.dto.VideoItem
import com.example.videoplayer.databinding.ActivityPlayerBinding
import com.example.videoplayer.util.FileUtil
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var playerView : StyledPlayerView? = null
    private var videoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);


        val videoItem = intent?.extras?.getSerializable(VIDEO_ITEM) as VideoItem?
        if(videoItem != null){
            val videoFileDir = FileUtil.getDownloadVideoFileDirectory(this)
            val fileName = FileUtil.getFileNameFromId(videoItem.id)
            videoFile = FileUtil.getFileNameWithPath(videoFileDir,fileName)
        }

        playerView = binding.playerView
        if(videoFile!=null){
            playVideo()
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

    private fun playVideo() {
        ExoPlayer.Builder(this@PlayerActivity)
            .build()
            .also { player ->
                playerView!!.player = player
                val uri = Uri.fromFile(videoFile)
                val mItem = MediaItem.fromUri(uri)
                player.setMediaItem(mItem)
                player.playWhenReady = true
                player.prepare()
            }
    }

    companion object {
        const val VIDEO_ITEM = "video_item"
    }
}