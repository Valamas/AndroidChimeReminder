package com.valamas.chimereminder.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.valamas.chimereminder.R
import com.valamas.chimereminder.data.ReminderLog
import com.valamas.chimereminder.databinding.ItemHistoryLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : ListAdapter<ReminderLog, HistoryAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemHistoryLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: ReminderLog) {
            binding.timeText.text = dateFormat.format(Date(log.triggeredAt))
            binding.labelText.text = log.label.ifEmpty { getString(R.string.no_label) }
        }

        private fun getString(id: Int) = binding.root.context.getString(id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemHistoryLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<ReminderLog>() {
        override fun areItemsTheSame(a: ReminderLog, b: ReminderLog) = a.id == b.id
        override fun areContentsTheSame(a: ReminderLog, b: ReminderLog) = a == b
    }
}
