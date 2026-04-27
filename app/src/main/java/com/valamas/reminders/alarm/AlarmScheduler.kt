package com.valamas.reminders.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.valamas.reminders.MainActivity
import com.valamas.reminders.data.Reminder
import com.valamas.reminders.data.RepeatType
import java.util.Calendar

object AlarmScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        if (!reminder.isEnabled) return
        scheduleAt(context, reminder, computeNextTrigger(reminder))
    }

    fun scheduleAt(context: Context, reminder: Reminder, triggerMs: Long) {
        if (!reminder.isEnabled) return
        val pi = buildPendingIntent(context, reminder.id)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        when {
            Build.VERSION.SDK_INT >= 31 -> {
                if (am.canScheduleExactAlarms()) {
                    val showIntent = PendingIntent.getActivity(
                        context, 0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showIntent), pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                }
            }
            Build.VERSION.SDK_INT >= 23 -> {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
            else -> {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context, reminderId))
    }

    fun computeNextTrigger(reminder: Reminder): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If the time has already passed today, start from tomorrow
        if (cal.timeInMillis <= now.timeInMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return when (reminder.repeatType) {
            RepeatType.ONE_TIME, RepeatType.DAILY -> cal.timeInMillis
            RepeatType.CUSTOM_DAYS -> {
                val days = reminder.customDays
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()
                for (i in 0..6) {
                    if (cal.get(Calendar.DAY_OF_WEEK) in days) return cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
        }
    }

    private fun buildPendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
