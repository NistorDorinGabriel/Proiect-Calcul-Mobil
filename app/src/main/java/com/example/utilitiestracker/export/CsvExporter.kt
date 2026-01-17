package com.example.utilitiestracker.export

import android.content.Context
import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.repository.ReadingsRepository
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CsvExporter(
    private val context: Context
) {
    private val ro = Locale("ro", "RO")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", ro)

    /**
     * Generează un CSV în /files/exports/ și returnează fișierul.
     */
    fun exportReadingsToCsv(): File {
        val db = AppDatabase.getInstance(context)


        // Citim datele sincron (dar asta va fi apelată de pe thread separat)
        val readings = runBlockingIo { db.readingDao().getAllReadingsForExport() }
        val tariffs = runBlockingIo { db.tariffDao().getAllOnce() }
        val tariffMap = tariffs.associateBy { it.meterType }

        // Calcul consum per meter, ținând cont de reset
        val grouped = readings.groupBy { it.meterId }
        val consumptionByReadingId = mutableMapOf<Long, Double?>()

        grouped.values.forEach { list ->
            // sort crescător în timp pentru a calcula diferențe corect
            val sorted = list.sortedWith(
                compareBy<com.example.utilitiestracker.data.local.ReadingWithMeter> { it.dateEpochDay }
                    .thenBy { it.id }
            )
            for (i in sorted.indices) {
                val current = sorted[i]
                val prev = sorted.getOrNull(i - 1)
                val consumption = when {
                    current.isReset -> null
                    prev == null -> null
                    else -> current.value - prev.value
                }
                consumptionByReadingId[current.id] = consumption
            }
        }

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "utilities_export_${System.currentTimeMillis()}.csv")

        file.bufferedWriter().use { out ->
            out.write("Utility,Date,Reading,Unit,Consumption,EstimatedCost,Currency,FixedMonthly,CostNote,Reset\n")

            readings.forEach { r ->
                val date = LocalDate.ofEpochDay(r.dateEpochDay).format(dateFmt)
                val consumption = consumptionByReadingId[r.id]
                val tariff = tariffMap[r.meterType]
                val cost = if (consumption != null && tariff != null) {
                    (if (consumption < 0) 0.0 else consumption) * tariff.pricePerUnit + tariff.fixedMonthly
                } else null

                val consumptionTxt = consumption?.let { String.format(ro, "%.2f", it) } ?: ""
                val costTxt = cost?.let { String.format(ro, "%.2f", it) } ?: ""
                val currency = tariff?.currency ?: "RON"
                val fixedMonthly = tariff?.fixedMonthly ?: 0.0
                val fixedTxt = if (tariff != null) String.format(ro, "%.2f", fixedMonthly) else ""
                val costNote = if (tariff != null && fixedMonthly > 0.0) {
                    String.format(ro, "+ abon. %.2f %s", fixedMonthly, currency)
                } else ""
                val resetTxt = if (r.isReset) "YES" else "NO"

                // Escape simplu: înlocuim virgulele din nume (puțin probabil) cu spații
                val utility = r.meterName.replace(",", " ")

                out.write(
                    listOf(
                        utility,
                        date,
                        String.format(ro, "%.2f", r.value),
                        r.meterUnit,
                        consumptionTxt,
                        costTxt,
                        currency,
                        fixedTxt,
                        costNote,
                        resetTxt
                    ).joinToString(",")
                )
                out.write("\n")
            }
        }

        return file
    }
}

/**
 * Helper: rulează un block suspend din context non-coroutine, pentru export thread-based.
 */
private fun <T> runBlockingIo(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { block() }
}
