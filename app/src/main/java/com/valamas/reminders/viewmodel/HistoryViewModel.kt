package com.valamas.reminders.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.valamas.reminders.App
import com.valamas.reminders.data.ReminderLog
import kotlinx.coroutines.flow.Flow

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as App).database.reminderLogDao()

    val logs: Flow<List<ReminderLog>> = dao.getRecent(
        System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    )
}
