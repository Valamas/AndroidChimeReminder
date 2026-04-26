package com.valamas.chimereminder.ui.reminders

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.valamas.chimereminder.R
import com.valamas.chimereminder.data.Reminder
import com.valamas.chimereminder.data.RepeatType
import com.valamas.chimereminder.databinding.ItemProBannerBinding
import com.valamas.chimereminder.databinding.ItemReminderBinding
import java.util.Locale

class RemindersAdapter(
    private val onToggle: (Reminder) -> Unit,
    private val onClick: (Reminder) -> Unit,
    private val onLongClick: (Reminder) -> Unit,
    private val onUpgradeClick: () -> Unit
) : ListAdapter<Reminder, RecyclerView.ViewHolder>(DiffCallback) {

    private var showBanner = false

    fun setBannerVisible(show: Boolean) {
        if (showBanner == show) return
        val bannerPosition = currentList.size
        showBanner = show
        if (show) notifyItemInserted(bannerPosition)
        else notifyItemRemoved(bannerPosition)
    }

    override fun getItemCount() = super.getItemCount() + if (showBanner) 1 else 0

    override fun getItemViewType(position: Int) =
        if (showBanner && position == currentList.size) TYPE_PRO_BANNER else TYPE_REMINDER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_PRO_BANNER) {
            BannerViewHolder(ItemProBannerBinding.inflate(inflater, parent, false))
        } else {
            ReminderViewHolder(ItemReminderBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ReminderViewHolder -> holder.bind(getItem(position))
            is BannerViewHolder -> holder.bind()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ReminderViewHolder) holder.cancelTick()
    }

    inner class BannerViewHolder(private val binding: ItemProBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener { onUpgradeClick() }
        }
    }

    inner class ReminderViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val handler = Handler(Looper.getMainLooper())
        private var tickRunnable: Runnable? = null

        fun bind(reminder: Reminder) {
            cancelTick()

            binding.timeText.text =
                String.format(Locale.getDefault(), "%02d:%02d", reminder.hour, reminder.minute)

            val iconRes = if (reminder.isCountdown) R.drawable.ic_type_hourglass
                          else R.drawable.ic_type_clock
            binding.reminderTypeIcon.setImageDrawable(
                ContextCompat.getDrawable(binding.root.context, iconRes)
            )

            if (reminder.isCountdown && reminder.isEnabled && reminder.nextTriggerMs > 0) {
                startTick(reminder.nextTriggerMs)
            } else {
                binding.remainingText.visibility = View.GONE
            }

            if (reminder.label.isNotEmpty()) {
                binding.labelText.text = reminder.label
                binding.labelText.visibility = View.VISIBLE
            } else {
                binding.labelText.visibility = View.GONE
            }

            binding.repeatText.text = when (reminder.repeatType) {
                RepeatType.ONE_TIME -> binding.root.context.getString(R.string.repeat_once)
                RepeatType.DAILY    -> binding.root.context.getString(R.string.repeat_daily)
                RepeatType.CUSTOM_DAYS -> formatCustomDays(reminder.customDays)
            }

            binding.enabledSwitch.setOnCheckedChangeListener(null)
            binding.enabledSwitch.isChecked = reminder.isEnabled
            binding.enabledSwitch.setOnCheckedChangeListener { _, _ -> onToggle(reminder) }

            binding.root.setOnClickListener { onClick(reminder) }
            binding.root.setOnLongClickListener { onLongClick(reminder); true }
        }

        private fun startTick(targetMs: Long) {
            tickRunnable = object : Runnable {
                override fun run() {
                    val diff = targetMs - System.currentTimeMillis()
                    if (diff <= 0) {
                        binding.remainingText.visibility = View.GONE
                        return
                    }
                    binding.remainingText.text = formatRemaining(diff)
                    binding.remainingText.visibility = View.VISIBLE
                    handler.postDelayed(this, 1_000L)
                }
            }
            handler.post(tickRunnable!!)
        }

        fun cancelTick() {
            tickRunnable?.let { handler.removeCallbacks(it) }
            tickRunnable = null
        }

        private fun formatRemaining(diffMs: Long): String {
            val totalSeconds = diffMs / 1000
            val days    = totalSeconds / 86400
            val hours   = (totalSeconds % 86400) / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                days > 0  -> "in ${days}d ${hours}h"
                hours > 0 -> "in ${hours}h ${minutes}m"
                else      -> "in ${minutes}m ${seconds}s"
            }
        }

        private fun formatCustomDays(customDays: String): String {
            val ctx = binding.root.context
            val names = mapOf(
                1 to ctx.getString(R.string.day_sun),
                2 to ctx.getString(R.string.day_mon),
                3 to ctx.getString(R.string.day_tue),
                4 to ctx.getString(R.string.day_wed),
                5 to ctx.getString(R.string.day_thu),
                6 to ctx.getString(R.string.day_fri),
                7 to ctx.getString(R.string.day_sat)
            )
            return customDays.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .sortedWith(compareBy { if (it == 1) 8 else it })
                .joinToString(", ") { names[it] ?: it.toString() }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Reminder>() {
        private const val TYPE_REMINDER = 0
        private const val TYPE_PRO_BANNER = 1

        override fun areItemsTheSame(a: Reminder, b: Reminder) = a.id == b.id
        override fun areContentsTheSame(a: Reminder, b: Reminder) = a == b
    }
}
