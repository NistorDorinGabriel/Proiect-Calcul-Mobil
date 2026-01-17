package com.example.utilitiestracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.local.MeterEntity
import com.example.utilitiestracker.data.repository.DashboardRepository
import com.example.utilitiestracker.databinding.ActivityMainBinding
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var months: List<YearMonth> = emptyList()
    private var selectedMonth: YearMonth = YearMonth.now()

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var dashRepo: DashboardRepository
    private var suppressSpinnerCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnReadings.setOnClickListener {
            startActivity(Intent(this, ReadingsActivity::class.java))
        }

        binding.btnTariffs.setOnClickListener {
            startActivity(Intent(this, TariffsActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        db = AppDatabase.getInstance(this)
        dashRepo = DashboardRepository(db)

        binding.btnToggleDashboard.setOnClickListener { toggleDashboard() }
        binding.spDashMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressSpinnerCallback) return
                selectedMonth = months[position]
                refreshDashboard(dashRepo, selectedMonth)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            if (db.meterDao().count() == 0) {
                db.meterDao().insertAll(
                    listOf(
                        MeterEntity(type = "WATER", name = "Apa", unit = "m3", sortOrder = 1),
                        MeterEntity(
                            type = "ELECTRICITY",
                            name = "Curent",
                            unit = "kWh",
                            sortOrder = 2
                        ),
                        MeterEntity(type = "GAS", name = "Gaz", unit = "m3", sortOrder = 3)
                    )
                )
            }
            months = dashRepo.getAvailableMonths()
            if (months.isEmpty()) months = listOf(YearMonth.now())

            val ro = Locale("ro", "RO")
            val fmt = DateTimeFormatter.ofPattern("MMMM yyyy", ro)
            val labels = months.map { it.format(fmt) }

            val keepMonth = selectedMonth
            val idx = months.indexOf(keepMonth).let { if (it >= 0) it else 0 }

            suppressSpinnerCallback = true
            binding.spDashMonth.adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels
            )
            binding.spDashMonth.setSelection(idx)
            selectedMonth = months[idx]
            suppressSpinnerCallback = false

            refreshDashboard(dashRepo, selectedMonth)
        }
    }

    private fun toggleDashboard() {
        val visible = binding.layoutDashboard.visibility == View.VISIBLE
        binding.layoutDashboard.visibility = if (visible) View.GONE else View.VISIBLE
        binding.btnToggleDashboard.text = getString(
            if (visible) R.string.btn_toggle_dashboard_show else R.string.btn_toggle_dashboard_hide
        )
    }

    private fun refreshDashboard(repo: DashboardRepository, ym: YearMonth) {
        lifecycleScope.launch {
            val cards = repo.loadMonthlyCards(ym)

            fun mainText(c: com.example.utilitiestracker.data.repository.UtilityCard): String {
                val cons = c.consumption?.let { String.format(Locale("ro", "RO"), "%.2f %s", it, c.unit) } ?: "-"
                val cost = c.cost?.let { String.format(Locale("ro", "RO"), "%.2f %s", it, c.currency) } ?: "-"
                val abonText = if (c.cost != null && c.fixedMonthly > 0.0) {
                    String.format(Locale("ro", "RO"), " (+ abon. %.2f %s)", c.fixedMonthly, c.currency)
                } else ""
                return "Consum: $cons - Cost: $cost$abonText"
            }

            val water = cards.first { it.meterType == "WATER" }
            val elec = cards.first { it.meterType == "ELECTRICITY" }
            val gas = cards.first { it.meterType == "GAS" }

            binding.tvWaterMain.text = mainText(water)
            binding.tvWaterLast.text = water.lastReadingLabel

            binding.tvElecMain.text = mainText(elec)
            binding.tvElecLast.text = elec.lastReadingLabel

            binding.tvGasMain.text = mainText(gas)
            binding.tvGasLast.text = gas.lastReadingLabel
        }
    }
}
