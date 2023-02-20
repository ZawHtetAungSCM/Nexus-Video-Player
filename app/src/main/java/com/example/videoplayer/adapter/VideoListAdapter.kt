package com.example.videoplayer.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color.red
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.videoplayer.R
import com.example.videoplayer.data.dto.VideoItem
import com.example.videoplayer.databinding.ItemVideoListBinding
import com.example.videoplayer.util.FileUtil
import com.example.videoplayer.util.FileUtil.Companion.formattedFileSizeToDisplay
import com.google.common.reflect.Reflection.getPackageName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class VideoListAdapter(
    private val context: Context,
    private val onItemClick: OnItemClick,
    private val onItemDownloadClick: OnItemDownloadClickListener,
    private val onItemDeleteClick: OnItemDeleteClickListener,
) : ListAdapter<VideoItem,
        VideoListAdapter.VideListViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(
            oldItem: VideoItem,
            newItem: VideoItem
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: VideoItem,
            newItem: VideoItem
        ): Boolean {
            return oldItem.title == newItem.title &&
                    oldItem.videoUrl == newItem.videoUrl &&
                    oldItem.downloaded == newItem.downloaded
        }
    }

    /**
     * Initialize view elements
     */

    class VideListViewHolder(
        private var binding: ItemVideoListBinding,
        private val context: Context,
        private val onItemDownloadClick: OnItemDownloadClickListener,
        private val onItemDeleteClick: OnItemDeleteClickListener,
    ) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n", "DiscouragedApi", "UseCompatLoadingForDrawables")
        fun bind(item: VideoItem, position: Int) {

            val thumbnailUri = item.videoThumbnail
            val imageResource: Int = context.resources.getIdentifier(thumbnailUri, null, context.packageName)
            val res: Drawable = context.resources.getDrawable(imageResource)

            binding.videoThumbnail.setImageDrawable(res)
            binding.videoTitle.text = item.title
            CoroutineScope(Dispatchers.IO).launch {
                val fileSize= FileUtil.getVideoFileSizeFromUrl(item.videoUrl)
                if( fileSize > 0){
                    binding.videoFileSizeText.text = FileUtil.formattedFileSizeToDisplay(fileSize)
                }
            }
            val loadingLayout = binding.progressBarLayout
            loadingLayout.visibility = View.INVISIBLE
            if (item.downloaded) {
                // Download
                binding.downloadBtn.setImageResource(R.drawable.ic_download_done)
                // Delete
                binding.deleteBtn.isVisible = true
                binding.deleteBtn.setOnClickListener {
                    onItemDeleteClick.onItemDeleteClick(position)
                }
            } else {
                // Download
                binding.downloadBtn.setImageResource(R.drawable.ic_download)
                binding.downloadBtn.setOnClickListener {
                    val progressText = binding.progressText
                    loadingLayout.visibility = View.VISIBLE
                    onItemDownloadClick.onItemDownloadClick(position,progressText)
                }
                // Delete
                binding.deleteBtn.isVisible = false
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VideoListAdapter.VideListViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return VideoListAdapter.VideListViewHolder(
            ItemVideoListBinding.inflate(layoutInflater, parent, false),
            context,
            onItemDownloadClick,
            onItemDeleteClick
        )
    }

    override fun onBindViewHolder(holder: VideoListAdapter.VideListViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.itemView.setOnClickListener {
            onItemClick.onItemClick(currentItem)
        }
        holder.bind(currentItem, position)
    }

    // Click item action
    // Click item action
    interface OnItemClick {
        fun onItemClick(videoItem: VideoItem)
    }
    interface OnItemDownloadClickListener {
        fun onItemDownloadClick(position : Int, progressText: TextView)
    }

    interface OnItemDeleteClickListener {
        fun onItemDeleteClick(position : Int)
    }
}
