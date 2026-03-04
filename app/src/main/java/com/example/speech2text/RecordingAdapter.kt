package com.example.speech2text

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying voice recording files in RecyclerView
 */
class RecordingAdapter(
    private var recordings: MutableList<File>,
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPosition: Int = -1

    inner class RecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_recording_name)
        val tvInfo: TextView = itemView.findViewById(R.id.tv_recording_info)
        val btnPlay: ImageButton = itemView.findViewById(R.id.btn_play)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_recording_icon)

        fun bind(file: File, position: Int) {
            // Set file name
            tvName.text = file.name

            // Set file info (size and date)
            val sizeKB = file.length() / 1024
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = dateFormat.format(Date(file.lastModified()))
            tvInfo.text = "22050 Hz • ${sizeKB} KB • $date"

            // Update play button icon based on playback state
            if (currentPlayingPosition == position && mediaPlayer?.isPlaying == true) {
                btnPlay.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnPlay.setImageResource(android.R.drawable.ic_media_play)
            }

            // Play button click listener
            btnPlay.setOnClickListener {
                if (currentPlayingPosition == position && mediaPlayer?.isPlaying == true) {
                    // Pause playback
                    pausePlayback()
                } else {
                    // Start playback
                    playRecording(file, position)
                }
            }

            // Delete button click listener
            btnDelete.setOnClickListener {
                stopPlayback()
                onDelete(file)
            }

            // Icon animation when playing
            if (currentPlayingPosition == position && mediaPlayer?.isPlaying == true) {
                ivIcon.alpha = 1.0f
            } else {
                ivIcon.alpha = 0.6f
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return RecordingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        holder.bind(recordings[position], position)
    }

    override fun getItemCount(): Int = recordings.size

    /**
     * Play recording file
     */
    private fun playRecording(file: File, position: Int) {
        try {
            // Stop any current playback
            stopPlayback()

            // Create and prepare new MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                currentPlayingPosition = position

                // Update UI when playback completes
                setOnCompletionListener {
                    currentPlayingPosition = -1
                    notifyItemChanged(position)
                }
            }

            // Update UI
            notifyItemChanged(position)

        } catch (e: Exception) {
            // Context is not available here, skip toast
            android.util.Log.e("RecordingAdapter", "Error playing recording: ${e.message}")
        }
    }

    /**
     * Pause playback
     */
    private fun pausePlayback() {
        mediaPlayer?.pause()
        if (currentPlayingPosition >= 0) {
            notifyItemChanged(currentPlayingPosition)
        }
    }

    /**
     * Stop playback
     */
    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        val previousPosition = currentPlayingPosition
        currentPlayingPosition = -1
        mediaPlayer = null

        if (previousPosition >= 0 && previousPosition < recordings.size) {
            notifyItemChanged(previousPosition)
        }
    }

    /**
     * Update recordings list
     */
    fun updateRecordings(newRecordings: List<File>) {
        stopPlayback()
        recordings.clear()
        recordings.addAll(newRecordings)
        notifyDataSetChanged()
    }

    /**
     * Remove recording at position
     */
    fun removeAt(position: Int) {
        if (position >= 0 && position < recordings.size) {
            if (currentPlayingPosition == position) {
                stopPlayback()
            }
            recordings.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    /**
     * Release resources
     */
    fun release() {
        stopPlayback()
    }
}

