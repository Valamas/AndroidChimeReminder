package com.valamas.chimereminder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.valamas.chimereminder.App
import com.valamas.chimereminder.data.ReminderLog
import kotlinx.coroutines.flow.Flow

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as App).database.reminderLogDao()

    val logs: Flow<List<ReminderLog>> = dao.getRecent(
        System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    )
}
