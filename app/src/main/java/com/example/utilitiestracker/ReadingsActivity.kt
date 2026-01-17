package com.example.utilitiestracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.repository.ReadingsRepository
import com.example.utilitiestracker.databinding.ActivityReadingsBinding
import kotlinx.coroutines.launch
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.util.concurrent.Executors
import com.example.utilitiestracker.export.CsvExporter
import java.time.YearMonth
import com.example.utilitiestracker.export.PdfReportExporter
import android.widget.ArrayAdapter
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job







class ReadingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadingsBinding
    private lateinit var adapter: ReadingsAdapter
    private lateinit var repo: ReadingsRepository

    private val exportExecutor = Executors.newSingleThreadExecutor()
    private var months: List<YearMonth> = emptyList()
    private var selectedMonth: YearMonth = YearMonth.now()

    private var monthJob: Job? = null




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ReadingsAdapter(emptyList()) { row -> showReadingActions(row) }
        binding.rvReadings.layoutManager = LinearLayoutManager(this)
        binding.rvReadings.adapter = adapter

        repo = ReadingsRepository(AppDatabase.getInstance(this))

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddReadingActivity::class.java))
        }
        binding.btnExportCsv.setOnClickListener {
            Toast.makeText(this, "Se generează CSV…", Toast.LENGTH_SHORT).show()

            exportExecutor.execute {
                try {
                    val file = CsvExporter(this).exportReadingsToCsv()
                    val uri: Uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )

                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    runOnUiThread {
                        startActivity(Intent.createChooser(share, "Trimite CSV"))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Eroare export: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        binding.btnExportPdf.setOnClickListener {
            Toast.makeText(this, "Se generează PDF…", Toast.LENGTH_SHORT).show()

            exportExecutor.execute {
                try {
                    val file = PdfReportExporter(this).exportMonthlyReportPdf(selectedMonth)
                    val uri: Uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )

                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    runOnUiThread {
                        startActivity(Intent.createChooser(share, "Trimite PDF"))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Eroare PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }


        months = (0..12).map { YearMonth.now().minusMonths(it.toLong()) }
        selectedMonth = months.first()
        val labels = months.map { ym ->
            ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ro", "RO")))
        }

        val spAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spMonth.adapter = spAdapter
        binding.spMonth.setSelection(0)

        binding.spMonth.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                selectedMonth = months[position]
                val ym = selectedMonth
                val start = ym.atDay(1).toEpochDay()
                val end = ym.plusMonths(1).atDay(1).toEpochDay()

                monthJob?.cancel()
                monthJob = lifecycleScope.launch {
                    repo.observeMonthAllReadings(start, end).collect { list ->
                        val rows = list.map { it.toReadingRow() }
                        adapter.updateItems(rows)
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }



    }
    private fun showReadingActions(row: ReadingRow) {
        val options = arrayOf("Editeaza", "Sterge")
        AlertDialog.Builder(this)
            .setTitle("Citire")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openEdit(row.id)
                    1 -> confirmDelete(row.id)
                }
            }
            .show()
    }

    private fun openEdit(readingId: Long) {
        val intent = Intent(this, AddReadingActivity::class.java).apply {
            putExtra(AddReadingActivity.EXTRA_READING_ID, readingId)
        }
        startActivity(intent)
    }

    private fun confirmDelete(readingId: Long) {
        AlertDialog.Builder(this)
            .setTitle("Sterge citirea")
            .setMessage("Sigur vrei sa stergi aceasta citire?")
            .setPositiveButton("Sterge") { _, _ -> deleteReading(readingId) }
            .setNegativeButton("Renunta", null)
            .show()
    }

    private fun deleteReading(readingId: Long) {
        lifecycleScope.launch {
            val err = repo.deleteReading(readingId)
            if (err != null) {
                Toast.makeText(this@ReadingsActivity, err, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ReadingsActivity, "Citire stearsa.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onDestroy() {
        exportExecutor.shutdown()
        super.onDestroy()
    }
    private fun com.example.utilitiestracker.data.local.ReadingListItem.toReadingRow(): ReadingRow {
        val date = java.time.LocalDate.ofEpochDay(dateEpochDay)
        val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        val resetTxt = if (isReset == 1) " (reset)" else ""
        return ReadingRow(
            id = id,
            title = "$dateStr • $meterName",
            details = "Citire: $value $unit$resetTxt"
        )
    }



}
