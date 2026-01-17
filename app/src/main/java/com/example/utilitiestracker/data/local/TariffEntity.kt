package com.example.utilitiestracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tariffs")
data class TariffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meterType: String,          // WATER / ELECTRICITY / GAS
    val pricePerUnit: Double,       // lei per unitate
    val fixedMonthly: Double,       // abonament / taxe fixe
    val currency: String = "RON",
    val updatedAtEpochMs: Long,
    val sourceMode: String = "AUTO", // "AUTO" sau "MANUAL"

// AUTO (internet)
    val autoPricePerUnit: Double? = null,
    val autoUpdatedAtEpochMs: Long? = null,
    val autoSource: String? = null, // "EUROSTAT"

// MANUAL
    val manualPricePerUnit: Double? = null,
    val manualFixedMonthly: Double? = null,
    val manualProvider: String? = null,
    val manualUpdatedAtEpochMs: Long? = null
)
