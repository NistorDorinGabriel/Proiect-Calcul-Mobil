package com.example.utilitiestracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "readings")
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meterId: Long,
    val dateEpochDay: Long,  // LocalDate.toEpochDay()
    val value: Double,
    val isReset: Boolean = false // NEW: contor înlocuit/resetat pentru această citire
)
