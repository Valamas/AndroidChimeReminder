package com.valamas.chimereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        val serviceIntent = Intent(context, ChimeService::class.java).apply {
            putExtra(ChimeService.EXTRA_REMINDER_ID, reminderId)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
