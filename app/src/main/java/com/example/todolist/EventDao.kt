package com.example.todolist
import androidx.room.*

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long // Returns generated row ID (requestCode)

    @Query("SELECT * FROM event WHERE requestCode = :code")
    suspend fun getEventByRequestCode(code: Int): EventEntity?

    @Query("SELECT * FROM event")
    suspend fun getAllEvents(): List<EventEntity>

    @Query("SELECT requestCode FROM event WHERE title = :title AND date = :date AND time = :time LIMIT 1")
    suspend fun getCodeByDetail(title: String, date: String, time: String): Long?

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM event")
    suspend fun clearAllEvents()
}