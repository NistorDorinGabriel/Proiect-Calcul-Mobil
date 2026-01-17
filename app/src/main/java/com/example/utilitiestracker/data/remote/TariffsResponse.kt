package com.example.utilitiestracker.data.remote

data class TariffsResponse(
    val updatedAtEpochMs: Long,
    val currency: String,
    val tariffs: List<TariffDto>
)

data class TariffDto(
    val meterType: String,     // "WATER", "ELECTRICITY", "GAS"
    val pricePerUnit: Double,
    val fixedMonthly: Double
)
