package com.valamas.chimereminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAll(): Flow<List<UserRecording>>

    @Insert
    suspend fun insert(recording: UserRecording): Long

    @Delete
    suspend fun delete(recording: UserRecording)

    @Query("SELECT COUNT(*) FROM reminders WHERE soundUri = :uri")
    fun usageCount(uri: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM reminders WHERE soundUri = :uri")
    suspend fun usageCountOnce(uri: String): Int
}
