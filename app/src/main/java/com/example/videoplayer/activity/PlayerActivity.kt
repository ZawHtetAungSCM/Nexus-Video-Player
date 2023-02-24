package com.example.videoplayer.activity

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.videoplayer.Constants.Companion.AES_TRANSFORMATION
import com.example.videoplayer.data.dto.DownloadStatus
import com.example.videoplayer.data.dto.VideoItem
import com.example.videoplayer.databinding.ActivityPlayerBinding
import com.example.videoplayer.ext.showToast
import com.example.videoplayer.util.EncryptedFileDataSourceFactory
import com.example.videoplayer.util.FileUtil
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.Cipher

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var playerView : StyledPlayerView? = null
    private var videoFile: File? = null

    private var mPlayer: ExoPlayer? = null
    private var mPlayerPosition: Long = 0
    private var mPlayerStartOnPrepared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);


        val videoItem = intent?.extras?.getSerializable(VIDEO_ITEM) as VideoItem?
        if(videoItem!=null){
            val fileName = FileUtil.getFileNameFromId(videoItem.id!!)
            val fileDir = FileUtil.getDownloadVideoFileDirectory(this@PlayerActivity)
            videoFile = FileUtil.getFileNameWithPath(fileDir, fileName)
        }
        playerView = binding.playerView
    }

    override fun onResume() {
        super.onResume()
        if(videoFile!=null){
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

    private fun deleteTempFile(){
        val file = FileUtil.getTempPlayFile(this@PlayerActivity)
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
        if(videoFile !=null){

            val bandwidthMeter = DefaultBandwidthMeter()
            val trackSelector: TrackSelector = DefaultTrackSelector()
            val loadControl: LoadControl = DefaultLoadControl()

            //TODO:: Fix Build In Key
            val mSecretKeySpec = FileUtil.getEncryptKeySpec()
            val mIvParameterSpec = FileUtil.getEncryptIvParamSpec()
            val mCipher = Cipher.getInstance(AES_TRANSFORMATION)
            mCipher?.init(Cipher.DECRYPT_MODE, mSecretKeySpec, mIvParameterSpec)

            val dataSourceFactory: DataSource.Factory = EncryptedFileDataSourceFactory(
                mCipher,
                mSecretKeySpec,
                mIvParameterSpec,
                bandwidthMeter
            )
            val extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory()

            val mediaSourceFactory: MediaSource.Factory =
                DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)


            mPlayer = ExoPlayer.Builder(this@PlayerActivity)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .also { player ->
                    playerView!!.player = player
                    val uri = Uri.fromFile(videoFile)
                    val mItem = MediaItem.fromUri(uri)
                    player.setMediaItem(mItem)
                    player.playWhenReady = true
                    player.prepare()
                }
            // mPlayer!!.playWhenReady = mPlayerStartOnPrepared
            if (mPlayerPosition != 0L) {
                mPlayer!!.seekTo(mPlayerPosition)
            }
        }
    }

    // A method for releasing the player (should be called when application is closed or minimized for example)
    private fun releasePlayer() {
        if (mPlayer != null) {
            mPlayerPosition = mPlayer!!.getCurrentPosition()
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