package com.valamas.reminders.ui.recordings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.valamas.reminders.data.UserRecording
import com.valamas.reminders.databinding.ItemRecordingBinding

class RecordingsAdapter(
    private val onPlay: (UserRecording) -> Unit,
    private val onDelete: (UserRecording) -> Unit
) : ListAdapter<UserRecording, RecordingsAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemRecordingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(recording: UserRecording) {
            binding.recordingName.text = recording.name
            binding.recordingDuration.text = formatDuration(recording.durationMs)
            binding.playButton.setOnClickListener { onPlay(recording) }
            binding.deleteButton.setOnClickListener { onDelete(recording) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return ""
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserRecording>() {
            override fun areItemsTheSame(a: UserRecording, b: UserRecording) = a.id == b.id
            override fun areContentsTheSame(a: UserRecording, b: UserRecording) = a == b
        }
    }
}
