package com.example.utilitiestracker.data.repository

import com.example.utilitiestracker.ReadingRow
import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.local.ReadingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlin.math.max

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.YearMonth
import com.example.utilitiestracker.data.local.ReadingListItem


class ReadingsRepository(private val db: AppDatabase) {

    private val roLocale = Locale("ro", "RO")
    private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", roLocale)

    fun observeReadingRows(): Flow<List<ReadingRow>> {
        val readingsFlow = db.readingDao().observeReadingsWithMeter()
        val tariffsFlow = db.tariffDao().observeAll()

        return combine(readingsFlow, tariffsFlow) { readings, tariffs ->
            val tariffMap = tariffs.associateBy { it.meterType }

            // grupăm pe meterId ca să calculăm diferențele (consumul)
            val grouped = readings.groupBy { it.meterId }

            // mapăm fiecare reading id -> consumption
            val consumptionByReadingId = mutableMapOf<Long, Double?>()

            grouped.values.forEach { list ->
                val sorted = list.sortedWith(
                    compareByDescending<com.example.utilitiestracker.data.local.ReadingWithMeter> { it.dateEpochDay }
                        .thenByDescending { it.id }
                )
                for (i in sorted.indices) {
                    val current = sorted[i]
                    val nextOlder = sorted.getOrNull(i + 1)
                    val consumption = when {
                        current.isReset -> null
                        nextOlder == null -> null
                        else -> current.value - nextOlder.value
                    }
                    consumptionByReadingId[current.id] = consumption
                }
            }

            readings.map { item ->
                val month = LocalDate.ofEpochDay(item.dateEpochDay).format(monthFmt)
                val consumption = consumptionByReadingId[item.id]

                val tariff = tariffMap[item.meterType]
                val cost = if (consumption != null && tariff != null) {
                    // protecție: consumul nu ar trebui să fie negativ, dar dacă e, îl plafonăm la 0
                    max(0.0, consumption) * tariff.pricePerUnit + tariff.fixedMonthly
                } else null

                val currency = tariff?.currency ?: "RON"
                val consumptionText = consumption?.let { String.format(roLocale, "%.2f", it) } ?: "—"
                val costText = cost?.let { String.format(roLocale, "%.2f %s", it, currency) } ?: "—"
                val abonText = if (tariff != null && tariff.fixedMonthly > 0 && cost != null) {
                    String.format(roLocale, " (+ abon. %.2f %s)", tariff.fixedMonthly, currency)
                } else ""

                ReadingRow(
                    id = item.id,
                    title = "${item.meterName} • $month",
                    details = "Citire: ${item.value} ${item.meterUnit} • Consum: $consumptionText ${item.meterUnit} • Cost: $costText$abonText"
                )
            }
        }
    }

    suspend fun addReadingValidated(
        meterId: Long,
        value: Double,
        dateEpochDay: Long,
        isReset: Boolean
    ): String? {
        val duplicateId = db.readingDao().findIdByMeterAndDate(meterId, dateEpochDay)
        if (duplicateId != null) {
            return "Exista deja o citire in aceeasi zi pentru acest contor."
        }
        val prev = db.readingDao().getPreviousForMeter(meterId, dateEpochDay)
        val next = db.readingDao().getNextForMeter(meterId, dateEpochDay)

        // Daca NU e reset, mentinem ordinea non-descrescatoare a valorilor in timp.
        if (!isReset) {
            if (prev != null && value < prev.value) {
                return "Citirea nu poate fi mai mica decat citirea precedenta (${prev.value}). Daca ai schimbat contorul, bifeaza Reset/Inlocuit."
            }
            if (next != null && !next.isReset && value > next.value) {
                return "Citirea nu poate fi mai mare decat citirea urmatoare (${next.value})."
            }
        }

        db.readingDao().insert(
            ReadingEntity(
                meterId = meterId,
                value = value,
                dateEpochDay = dateEpochDay,
                isReset = isReset
            )
        )
        return null
    }

    suspend fun updateReadingValidated(
        id: Long,
        meterId: Long,
        value: Double,
        dateEpochDay: Long,
        isReset: Boolean
    ): String? {
        val duplicateId = db.readingDao().findIdByMeterAndDateExcluding(meterId, dateEpochDay, id)
        if (duplicateId != null) {
            return "Exista deja o citire in aceeasi zi pentru acest contor."
        }

        val prev = db.readingDao().getPreviousForMeterExcluding(meterId, dateEpochDay, id)
        val next = db.readingDao().getNextForMeterExcluding(meterId, dateEpochDay, id)

        if (!isReset) {
            if (prev != null && value < prev.value) {
                return "Citirea nu poate fi mai mica decat citirea precedenta (${prev.value}). Daca ai schimbat contorul, bifeaza Reset/Inlocuit."
            }
            if (next != null && !next.isReset && value > next.value) {
                return "Citirea nu poate fi mai mare decat citirea urmatoare (${next.value})."
            }
        }

        db.readingDao().update(
            ReadingEntity(
                id = id,
                meterId = meterId,
                value = value,
                dateEpochDay = dateEpochDay,
                isReset = isReset
            )
        )
        return null
    }

    suspend fun deleteReading(id: Long): String? {
        return try {
            db.readingDao().deleteById(id)
            null
        } catch (e: Exception) {
            e.message ?: "Eroare la stergere."
        }
    }

    suspend fun getReadingById(id: Long): ReadingEntity? = db.readingDao().getById(id)

    suspend fun getMetersOnce() = db.meterDao().getAllOnce()

    suspend fun getAvailableMonths(): List<YearMonth> {
        val all = db.readingDao().getAllReadingsForExport()
        return all
            .map { YearMonth.from(java.time.LocalDate.ofEpochDay(it.dateEpochDay)) }
            .distinct()
            .sortedDescending()
    }



    fun observeMonthAllReadings(startEpochDay: Long, endEpochDay: Long): Flow<List<ReadingListItem>> {
        return db.readingDao().observeMonthAllReadings(startEpochDay, endEpochDay)
    }

}
