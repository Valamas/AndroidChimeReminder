package com.valamas.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class UserRecording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filename: String,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
