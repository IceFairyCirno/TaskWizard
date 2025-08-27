package com.example.todolist
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val requestCode: Long = 0, // Auto-generated unique code
    val title: String,
    val date: String,
    val time: String
)
