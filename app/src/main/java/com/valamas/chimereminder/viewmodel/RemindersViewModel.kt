package com.valamas.chimereminder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valamas.chimereminder.App
import com.valamas.chimereminder.alarm.AlarmScheduler
import com.valamas.chimereminder.data.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemindersViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as App).database.reminderDao()
    private val appScope = (app as App).applicationScope

    val reminders: Flow<List<Reminder>> = dao.getAll()

    fun saveInBackground(reminder: Reminder) {
        appScope.launch { doSave(reminder) }
    }

    suspend fun save(reminder: Reminder) = withContext(Dispatchers.IO) { doSave(reminder) }

    private suspend fun doSave(reminder: Reminder) {
        val now = System.currentTimeMillis()
        val next = if (reminder.nextTriggerMs > now) reminder.nextTriggerMs
                   else AlarmScheduler.computeNextTrigger(reminder)
        val ready = reminder.copy(nextTriggerMs = next)
        if (ready.id == 0L) {
            val id = dao.insert(ready)
            if (ready.isEnabled) {
                AlarmScheduler.scheduleAt(getApplication(), ready.copy(id = id), next)
            }
        } else {
            dao.update(ready)
            AlarmScheduler.cancel(getApplication(), ready.id)
            if (ready.isEnabled) {
                AlarmScheduler.scheduleAt(getApplication(), ready, next)
            }
        }
    }

    fun delete(reminder: Reminder) = viewModelScope.launch {
        AlarmScheduler.cancel(getApplication(), reminder.id)
        dao.delete(reminder)
    }

    fun toggleEnabled(reminder: Reminder) = viewModelScope.launch {
        // Always recompute nextTriggerMs so list sort stays correct even when disabled
        val next = AlarmScheduler.computeNextTrigger(reminder)
        val updated = reminder.copy(isEnabled = !reminder.isEnabled, nextTriggerMs = next)
        dao.update(updated)
        if (updated.isEnabled) {
            AlarmScheduler.schedule(getApplication(), updated)
        } else {
            AlarmScheduler.cancel(getApplication(), updated.id)
        }
    }
}
