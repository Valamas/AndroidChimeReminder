package com.valamas.reminders.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.valamas.reminders.alarm.AlarmScheduler
import com.valamas.reminders.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val active = db.reminderDao().getActiveReminders()
                active.forEach { reminder ->
                    val next = AlarmScheduler.computeNextTrigger(reminder)
                    val updated = reminder.copy(nextTriggerMs = next)
                    db.reminderDao().update(updated)
                    AlarmScheduler.schedule(context, updated)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
