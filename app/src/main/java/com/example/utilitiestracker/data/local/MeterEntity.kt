package com.example.utilitiestracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meters")
data class MeterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,        // WATER / ELECTRICITY / GAS
    val name: String,        // ex: "Curent"
    val unit: String,        // ex: "kWh"
    val sortOrder: Int       // pentru ordonare în spinner
)
