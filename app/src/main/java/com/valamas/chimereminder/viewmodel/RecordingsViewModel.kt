package com.valamas.chimereminder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valamas.chimereminder.App
import com.valamas.chimereminder.data.UserRecording
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordingsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as App).database.recordingDao()

    val recordings: StateFlow<List<UserRecording>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPro: StateFlow<Boolean> = app.billingManager.isPro

    fun insert(recording: UserRecording) = viewModelScope.launch {
        dao.insert(recording)
    }

    fun delete(recording: UserRecording) = viewModelScope.launch {
        dao.delete(recording)
    }

    suspend fun usageCount(recording: UserRecording): Int =
        dao.usageCountOnce("recording:${recording.filename}")
}
