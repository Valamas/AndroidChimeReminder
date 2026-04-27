package com.valamas.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_logs")
data class ReminderLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val label: String,
    val triggeredAt: Long = System.currentTimeMillis()
)
