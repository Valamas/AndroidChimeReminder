package com.valamas.chimereminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val hour: Int,
    val minute: Int,
    val repeatType: RepeatType,
    val customDays: String = "",   // Calendar day-of-week constants, comma-separated: "2,4,6"
    val soundUri: String = "",     // empty = default chime; "raw:NAME" = bundled; content URI = file
    val soundLabel: String = "",   // human-readable name of the selected sound
    val volume: Int = 7,           // 0–15, mapped to STREAM_ALARM max
    val isEnabled: Boolean = true,
    val nextTriggerMs: Long = 0,   // epoch ms of next fire; used for list sort
    val isCountdown: Boolean = false
)

enum class RepeatType { ONE_TIME, DAILY, CUSTOM_DAYS }
