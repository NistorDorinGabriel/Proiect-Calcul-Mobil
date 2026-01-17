package com.example.utilitiestracker.data.local

enum class UtilityType(val displayName: String, val unit: String) {
    WATER("Apă", "m³"),
    ELECTRICITY("Curent", "kWh"),
    GAS("Gaz", "m³")
}
