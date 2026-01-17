package com.example.utilitiestracker

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.local.MeterEntity
import com.example.utilitiestracker.data.repository.ReadingsRepository
import com.example.utilitiestracker.databinding.ActivityAddReadingBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import android.app.DatePickerDialog
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.utilitiestracker.util.DateUtils




class AddReadingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddReadingBinding
    private lateinit var meters: List<MeterEntity>
    private var selectedDate: LocalDate = LocalDate.now()
    private val ro = Locale("ro", "RO")
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", ro)
    private var selectedDateEpochDay: Long = DateUtils.localDateToEpochDay(DateUtils.today())
    private var editReadingId: Long? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddReadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repo = ReadingsRepository(AppDatabase.getInstance(this))
        editReadingId = intent.getLongExtra(EXTRA_READING_ID, -1L).takeIf { it > 0 }

        val today = DateUtils.today()
        selectedDateEpochDay = DateUtils.localDateToEpochDay(today)

        lifecycleScope.launch {
            meters = repo.getMetersOnce()

            val labels = meters.map { "${it.name} (${it.unit})" }
            binding.spMeter.adapter = ArrayAdapter(
                this@AddReadingActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels
            )

            val currentId = editReadingId
            if (currentId != null) {
                val existing = repo.getReadingById(currentId)
                if (existing == null) {
                    Toast.makeText(this@AddReadingActivity, "Citire inexistenta.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                val idx = meters.indexOfFirst { it.id == existing.meterId }
                if (idx >= 0) binding.spMeter.setSelection(idx)

                selectedDateEpochDay = existing.dateEpochDay
                binding.etDate.setText(DateUtils.format(DateUtils.epochDayToLocalDate(existing.dateEpochDay)))
                binding.etValue.setText(existing.value.toString())
                binding.cbReset.isChecked = existing.isReset
                binding.btnSave.text = "Actualizeaza"
            } else {
                binding.etDate.setText(DateUtils.format(today))
            }
        }

        binding.btnSave.setOnClickListener {
            val text = binding.etValue.text?.toString()?.trim().orEmpty()
            val value = text.toDoubleOrNull()

            if (value == null) {
                Toast.makeText(this, "Introdu o valoare numerică validă.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val meter = meters[binding.spMeter.selectedItemPosition]

            val isReset = binding.cbReset.isChecked

            lifecycleScope.launch {
                val currentId = editReadingId
                val err = if (currentId != null) {
                    repo.updateReadingValidated(
                        id = currentId,
                        meterId = meter.id,
                        value = value,
                        dateEpochDay = selectedDateEpochDay,
                        isReset = isReset
                    )
                } else {
                    repo.addReadingValidated(
                        meterId = meter.id,
                        value = value,
                        dateEpochDay = selectedDateEpochDay,
                        isReset = isReset
                    )
                }
                if (err != null) {
                    Toast.makeText(this@AddReadingActivity, err, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val msg = if (currentId != null) "Citire actualizata." else "Citire salvata."
                Toast.makeText(this@AddReadingActivity, msg, Toast.LENGTH_SHORT).show()
                finish()
            }

        }
        val openPicker = {
            val current = DateUtils.epochDayToLocalDate(selectedDateEpochDay)
            val dlg = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val picked = LocalDate.of(year, month + 1, dayOfMonth)
                    selectedDateEpochDay = DateUtils.localDateToEpochDay(picked)
                    binding.etDate.setText(DateUtils.format(picked))
                },
                current.year,
                current.monthValue - 1,
                current.dayOfMonth
            )
            dlg.datePicker.maxDate = System.currentTimeMillis()
            dlg.show()
        }

        binding.etDate.setOnClickListener { openPicker() }
        binding.tilDate.setOnClickListener { openPicker() } // click și pe container


    }
    private fun openDatePicker() {
        val d = selectedDate
        val dlg = DatePickerDialog(
            this,
            { _, year, month, day ->
                // month e 0-based
                selectedDate = LocalDate.of(year, month + 1, day)
                binding.etDate.setText(selectedDate.format(dateFmt))
            },
            d.year,
            d.monthValue - 1,
            d.dayOfMonth
        )

        // opțional: nu permite date din viitor
        dlg.datePicker.maxDate = System.currentTimeMillis()

        dlg.show()
    }

    companion object {
        const val EXTRA_READING_ID = "extra_reading_id"
    }
}
