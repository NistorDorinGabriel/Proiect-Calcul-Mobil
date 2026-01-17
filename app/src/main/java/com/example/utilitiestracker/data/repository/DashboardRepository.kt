package com.example.utilitiestracker.data.repository

import com.example.utilitiestracker.data.local.AppDatabase
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

data class UtilityCard(
    val meterType: String,
    val title: String,
    val unit: String,
    val consumption: Double?,
    val cost: Double?,
    val fixedMonthly: Double,
    val currency: String,
    val lastReadingLabel: String,
    val missingThisMonth: Boolean
)

class DashboardRepository(private val db: AppDatabase) {

    suspend fun getAvailableMonths(): List<YearMonth> {
        val all = db.readingDao().getAllReadingsForExport()
        return all
            .map { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) }
            .distinct()
            .sortedDescending()
    }

    suspend fun loadMonthlyCards(ym: YearMonth): List<UtilityCard> {
        val readings = db.readingDao().getAllReadingsForExport()
        val tariffs = db.tariffDao().getAllOnce()
        val tariffMap = tariffs.associateBy { it.meterType }

        // Calculăm consum pe fiecare reading, pe fiecare meterId, pe toată istoria (corect pentru prima citire din lună)
        val grouped = readings.groupBy { it.meterId }
        val consumptionByReadingId = mutableMapOf<Long, Double?>()

        grouped.values.forEach { list ->
            val sorted = list.sortedWith(compareBy({ it.dateEpochDay }, { it.id }))
            for (i in sorted.indices) {
                val cur = sorted[i]
                val prev = sorted.getOrNull(i - 1)
                val cons = when {
                    cur.isReset -> null
                    prev == null -> null
                    else -> cur.value - prev.value
                }
                consumptionByReadingId[cur.id] = cons
            }
        }

        val readingsInMonth = readings.filter {
            YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == ym
        }

        fun makeCard(type: String, title: String): UtilityCard {
            val tariff = tariffMap[type]
            val currency = tariff?.currency ?: "RON"
            val fixedMonthly = tariff?.fixedMonthly ?: 0.0

            val list = readingsInMonth.filter { it.meterType == type }
            val missing = list.isEmpty()

            val unit = list.firstOrNull()?.meterUnit ?: when (type) {
                "ELECTRICITY" -> "kWh"
                else -> "m³"
            }

            // Sumăm consumurile din luna respectivă
            val totalConsumption = list.mapNotNull { r ->
                val c = consumptionByReadingId[r.id]
                if (c == null) null else max(0.0, c)
            }.sum().let { if (it == 0.0 && list.isNotEmpty()) 0.0 else it }

            val consumptionOrNull = if (list.isEmpty()) null else totalConsumption

            // Cost: consum * price + fixedMonthly (o singură dată pe lună)
            val costOrNull = if (tariff != null && list.isNotEmpty()) {
                totalConsumption * tariff.pricePerUnit + tariff.fixedMonthly
            } else if (tariff != null && list.isEmpty()) {
                // dacă vrei să arăți abonamentul chiar și fără citiri luna asta:
                tariff.fixedMonthly
            } else null

            // Ultima citire din lună
            val last = list.maxWithOrNull(compareBy({ it.dateEpochDay }, { it.id }))
            val lastLabel = if (last == null) {
                "Fără citire în luna selectată"
            } else {
                val d = LocalDate.ofEpochDay(last.dateEpochDay)
                "Ultima: ${d.dayOfMonth}.${"%02d".format(d.monthValue)}.${d.year} • ${"%.2f".format(last.value)} $unit"
            }

            return UtilityCard(
                meterType = type,
                title = title,
                unit = unit,
                consumption = consumptionOrNull,
                cost = costOrNull,
                fixedMonthly = fixedMonthly,
                currency = currency,
                lastReadingLabel = lastLabel,
                missingThisMonth = missing
            )
        }

        return listOf(
            makeCard("WATER", "Apă"),
            makeCard("ELECTRICITY", "Curent"),
            makeCard("GAS", "Gaz")
        )
    }
}
