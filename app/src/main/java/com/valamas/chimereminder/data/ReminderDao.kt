package com.valamas.chimereminder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    // Sort by next trigger time; reminders with nextTriggerMs=0 (fired one-shots) sort last
    @Query("""
        SELECT * FROM reminders
        ORDER BY
            CASE WHEN nextTriggerMs = 0 THEN 9223372036854775807 ELSE nextTriggerMs END ASC
    """)
    fun getAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isEnabled = 1")
    suspend fun getActiveReminders(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("SELECT COUNT(*) FROM reminders")
    fun getCount(): Flow<Int>
}
