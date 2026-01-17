package com.example.utilitiestracker.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.utilitiestracker.data.local.AppDatabase
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class PdfReportExporter(private val context: Context) {

    private val ro = Locale("ro", "RO")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", ro)

    fun exportMonthlyReportPdf(yearMonth: YearMonth = YearMonth.now()): File {
        val db = AppDatabase.getInstance(context)

        val readings = runBlockingIo { db.readingDao().getAllReadingsForExport() }
        val tariffs = runBlockingIo { db.tariffDao().getAllOnce() }
        val tariffMap = tariffs.associateBy { it.meterType }

        // Filtrăm citirile din luna cerută
        val readingsInMonth = readings.filter {
            YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == yearMonth
        }

        // Consum per reading (pe meter), ținând cont de reset
        val grouped = readings.groupBy { it.meterId }
        val consumptionByReadingId = mutableMapOf<Long, Double?>()

        grouped.values.forEach { list ->
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

        // Totaluri (doar pentru citirile din luna selectată)
        data class Totals(var consumption: Double = 0.0, var cost: Double = 0.0)
        val totalsByType = mutableMapOf<String, Totals>()

        readingsInMonth.forEach { r ->
            val consumption = consumptionByReadingId[r.id]
            val tariff = tariffMap[r.meterType]
            if (consumption != null && tariff != null) {
                val safeCons = max(0.0, consumption)
                val cost = safeCons * tariff.pricePerUnit + tariff.fixedMonthly
                val t = totalsByType.getOrPut(r.meterType) { Totals() }
                t.consumption += safeCons
                t.cost += cost
            }
        }

        // PDF setup (A4-ish)
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
        }
        val textPaint = Paint().apply {
            typeface = Typeface.DEFAULT
            textSize = 10f
        }

        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        fun newPage() {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
        }

        var y = margin

        // Titlu
        canvas.drawText("Utilities Tracker – Raport consum", margin.toFloat(), y.toFloat(), titlePaint)
        y += 22

        val monthLabel = yearMonth.toString().replace("-", "/") // ex: 2026/01
        canvas.drawText("Luna: $monthLabel", margin.toFloat(), y.toFloat(), textPaint)
        y += 14
        canvas.drawText("Generat: ${LocalDate.now().format(dateFmt)}", margin.toFloat(), y.toFloat(), textPaint)
        y += 18

        // Sumar
        canvas.drawText("Sumar (unde este posibil):", margin.toFloat(), y.toFloat(), headerPaint)
        y += 14

        fun lineSum(type: String, unit: String, name: String) {
            val t = totalsByType[type]
            val tariff = tariffMap[type]
            val currency = tariff?.currency ?: "RON"
            val consTxt = if (t != null) String.format(ro, "%.2f %s", t.consumption, unit) else "—"
            val costTxt = if (t != null) String.format(ro, "%.2f %s", t.cost, currency) else "—"
            val abonTxt = if (tariff != null && tariff.fixedMonthly > 0 && t != null) {
                String.format(ro, " (+ abon. %.2f %s)", tariff.fixedMonthly, currency)
            } else ""
            canvas.drawText("$name: Consum $consTxt • Cost $costTxt$abonTxt", margin.toFloat(), y.toFloat(), textPaint)
            y += 14
        }

        lineSum("WATER", "m³", "Apă")
        lineSum("ELECTRICITY", "kWh", "Curent")
        lineSum("GAS", "m³", "Gaz")
        y += 10

        canvas.drawText("Detalii citiri (luna selectată):", margin.toFloat(), y.toFloat(), headerPaint)
        y += 16

        // Header tabel
        fun drawTableHeader() {
            canvas.drawText("Data", margin.toFloat(), y.toFloat(), headerPaint)
            canvas.drawText("Utilitate", (margin + 90).toFloat(), y.toFloat(), headerPaint)
            canvas.drawText("Citire", (margin + 220).toFloat(), y.toFloat(), headerPaint)
            canvas.drawText("Consum", (margin + 330).toFloat(), y.toFloat(), headerPaint)
            canvas.drawText("Cost", (margin + 440).toFloat(), y.toFloat(), headerPaint)
            y += 12
        }

        drawTableHeader()
        y += 6

        // Rânduri
        val rowH = 14
        readingsInMonth.forEach { r ->
            if (y > pageHeight - margin) {
                newPage()
                y = margin
                drawTableHeader()
                y += 6
            }

            val date = LocalDate.ofEpochDay(r.dateEpochDay).format(dateFmt)
            val consumption = consumptionByReadingId[r.id]
            val tariff = tariffMap[r.meterType]

            val consTxt = consumption?.let { String.format(ro, "%.2f %s", it, r.meterUnit) } ?: "—"
            val costTxt = if (consumption != null && tariff != null) {
                val safeCons = max(0.0, consumption)
                val cost = safeCons * tariff.pricePerUnit + tariff.fixedMonthly
                String.format(ro, "%.2f %s", cost, tariff.currency)
            } else "—"

            val readingTxt = String.format(ro, "%.2f %s", r.value, r.meterUnit)
            val utilTxt = r.meterName + if (r.isReset) " (reset)" else ""

            canvas.drawText(date, margin.toFloat(), y.toFloat(), textPaint)
            canvas.drawText(utilTxt, (margin + 90).toFloat(), y.toFloat(), textPaint)
            canvas.drawText(readingTxt, (margin + 220).toFloat(), y.toFloat(), textPaint)
            canvas.drawText(consTxt, (margin + 330).toFloat(), y.toFloat(), textPaint)
            canvas.drawText(costTxt, (margin + 440).toFloat(), y.toFloat(), textPaint)

            y += rowH
        }

        // Legendă
        if (y > pageHeight - margin - 30) {
            newPage()
            y = margin
        }
        y += 10
        canvas.drawText("Legendă: „—” = prima citire sau reset (nu se poate calcula consumul).", margin.toFloat(), y.toFloat(), textPaint)

        doc.finishPage(page)

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "raport_${yearMonth.year}_${"%02d".format(yearMonth.monthValue)}.pdf")

        FileOutputStream(file).use { fos ->
            doc.writeTo(fos)
        }
        doc.close()

        return file
    }
}

private fun <T> runBlockingIo(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { block() }
}
