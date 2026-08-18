package com.valamas.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.valamas.reminders.data.AppDatabase
import com.valamas.reminders.data.RepeatType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val reminder = db.reminderDao().getById(reminderId)
                if (reminder != null && reminder.isEnabled) {
                    when (reminder.repeatType) {
                        RepeatType.DAILY, RepeatType.CUSTOM_DAYS -> {
                            val next = AlarmScheduler.computeNextTrigger(reminder)
                            val updated = reminder.copy(nextTriggerMs = next)
                            db.reminderDao().update(updated)
                            AlarmScheduler.schedule(context, updated)
                        }
                        RepeatType.ONE_TIME -> {}
                    }
                }
            } finally {
                val serviceIntent = Intent(context, ChimeService::class.java).apply {
                    putExtra(ChimeService.EXTRA_REMINDER_ID, reminderId)
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
