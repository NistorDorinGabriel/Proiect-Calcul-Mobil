package com.example.utilitiestracker.data.local

data class ReadingListItem(
    val id: Long,
    val dateEpochDay: Long,
    val value: Double,
    val isReset: Int,
    val meterType: String,
    val meterName: String,
    val unit: String
)
