package com.valamas.chimereminder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderLogDao {

    @Query("SELECT * FROM reminder_logs WHERE triggeredAt > :cutoff ORDER BY triggeredAt DESC")
    fun getRecent(cutoff: Long): Flow<List<ReminderLog>>

    @Insert
    suspend fun insert(log: ReminderLog)

    @Query("DELETE FROM reminder_logs WHERE triggeredAt < :olderThan")
    suspend fun pruneOlderThan(olderThan: Long)
}
