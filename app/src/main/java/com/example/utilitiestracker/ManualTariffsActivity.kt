package com.example.utilitiestracker

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.local.TariffEntity
import com.example.utilitiestracker.data.repository.TariffsRepository
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class ManualTariffsActivity : AppCompatActivity() {

    private lateinit var repo: TariffsRepository
    private val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    // Views
    private lateinit var tvWaterStatus: TextView
    private lateinit var etWaterProvider: TextInputEditText
    private lateinit var etWaterPrice: TextInputEditText
    private lateinit var etWaterFixed: TextInputEditText
    private lateinit var swWaterUseManual: SwitchCompat

    private lateinit var tvElecStatus: TextView
    private lateinit var etElecProvider: TextInputEditText
    private lateinit var etElecPrice: TextInputEditText
    private lateinit var etElecFixed: TextInputEditText
    private lateinit var swElecUseManual: SwitchCompat

    private lateinit var tvGasStatus: TextView
    private lateinit var etGasProvider: TextInputEditText
    private lateinit var etGasPrice: TextInputEditText
    private lateinit var etGasFixed: TextInputEditText
    private lateinit var swGasUseManual: SwitchCompat

    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_tariffs)

        repo = TariffsRepository(AppDatabase.getInstance(this))

        bindViews()

        btnSave.setOnClickListener { saveAll() }

        loadAndFill()
    }

    private fun bindViews() {
        tvWaterStatus = findViewById(R.id.tvWaterStatus)
        etWaterProvider = findViewById(R.id.etWaterProvider)
        etWaterPrice = findViewById(R.id.etWaterPrice)
        etWaterFixed = findViewById(R.id.etWaterFixed)
        swWaterUseManual = findViewById(R.id.swWaterUseManual)

        tvElecStatus = findViewById(R.id.tvElecStatus)
        etElecProvider = findViewById(R.id.etElecProvider)
        etElecPrice = findViewById(R.id.etElecPrice)
        etElecFixed = findViewById(R.id.etElecFixed)
        swElecUseManual = findViewById(R.id.swElecUseManual)

        tvGasStatus = findViewById(R.id.tvGasStatus)
        etGasProvider = findViewById(R.id.etGasProvider)
        etGasPrice = findViewById(R.id.etGasPrice)
        etGasFixed = findViewById(R.id.etGasFixed)
        swGasUseManual = findViewById(R.id.swGasUseManual)

        btnSave = findViewById(R.id.btnSaveManual)
    }

    private fun loadAndFill() {
        lifecycleScope.launch {
            val items = AppDatabase.getInstance(this@ManualTariffsActivity).tariffDao().getAllOnce()
            fillUi(items)
        }
    }

    private fun fillUi(items: List<TariffEntity>) {
        fillOne(
            item = items.firstOrNull { it.meterType == "WATER" },
            tvStatus = tvWaterStatus,
            etProvider = etWaterProvider,
            etPrice = etWaterPrice,
            etFixed = etWaterFixed,
            swUseManual = swWaterUseManual,
            label = "Apă"
        )

        fillOne(
            item = items.firstOrNull { it.meterType == "ELECTRICITY" },
            tvStatus = tvElecStatus,
            etProvider = etElecProvider,
            etPrice = etElecPrice,
            etFixed = etElecFixed,
            swUseManual = swElecUseManual,
            label = "Curent"
        )

        fillOne(
            item = items.firstOrNull { it.meterType == "GAS" },
            tvStatus = tvGasStatus,
            etProvider = etGasProvider,
            etPrice = etGasPrice,
            etFixed = etGasFixed,
            swUseManual = swGasUseManual,
            label = "Gaz"
        )
    }

    private fun fillOne(
        item: TariffEntity?,
        tvStatus: TextView,
        etProvider: TextInputEditText,
        etPrice: TextInputEditText,
        etFixed: TextInputEditText,
        swUseManual: SwitchCompat,
        label: String
    ) {
        if (item == null) {
            tvStatus.text = "Nu există încă în DB (se va crea la salvare)."
            etProvider.setText("")
            etPrice.setText("")
            etFixed.setText("")
            swUseManual.isChecked = false
            return
        }

        val activeTs = formatTs(item.updatedAtEpochMs)
        val active = "Activ: ${fmt2(item.pricePerUnit)} + abon. ${fmt2(item.fixedMonthly)} ${item.currency} • ${item.sourceMode} • $activeTs"

        val autoInfo = if (item.autoPricePerUnit != null && item.autoUpdatedAtEpochMs != null) {
            "AUTO: ${fmt2(item.autoPricePerUnit)} (source=${item.autoSource ?: "?"}, ${formatTs(item.autoUpdatedAtEpochMs)})"
        } else "AUTO: —"

        val manualInfo = if (item.manualPricePerUnit != null && item.manualFixedMonthly != null && item.manualUpdatedAtEpochMs != null) {
            "MANUAL: ${fmt2(item.manualPricePerUnit)} + ${fmt2(item.manualFixedMonthly)} (provider=${item.manualProvider ?: "—"}, ${formatTs(item.manualUpdatedAtEpochMs)})"
        } else "MANUAL: —"

        tvStatus.text = "$label\n$active\n$autoInfo\n$manualInfo"

        etProvider.setText(item.manualProvider ?: "")
        etPrice.setText(item.manualPricePerUnit?.toString() ?: "")
        etFixed.setText(item.manualFixedMonthly?.toString() ?: "")
        swUseManual.isChecked = item.sourceMode == "MANUAL"
    }

    private fun saveAll() {
        lifecycleScope.launch {
            val err1 = saveOne(
                type = "WATER",
                provider = etWaterProvider.text?.toString().orEmpty(),
                priceStr = etWaterPrice.text?.toString().orEmpty(),
                fixedStr = etWaterFixed.text?.toString().orEmpty(),
                useManual = swWaterUseManual.isChecked
            )
            val err2 = saveOne(
                type = "ELECTRICITY",
                provider = etElecProvider.text?.toString().orEmpty(),
                priceStr = etElecPrice.text?.toString().orEmpty(),
                fixedStr = etElecFixed.text?.toString().orEmpty(),
                useManual = swElecUseManual.isChecked
            )
            val err3 = saveOne(
                type = "GAS",
                provider = etGasProvider.text?.toString().orEmpty(),
                priceStr = etGasPrice.text?.toString().orEmpty(),
                fixedStr = etGasFixed.text?.toString().orEmpty(),
                useManual = swGasUseManual.isChecked
            )

            val err = listOfNotNull(err1, err2, err3).firstOrNull()
            if (err != null) {
                Toast.makeText(this@ManualTariffsActivity, err, Toast.LENGTH_LONG).show()
                return@launch
            }

            Toast.makeText(this@ManualTariffsActivity, "Tarife salvate.", Toast.LENGTH_SHORT).show()
            loadAndFill()
        }
    }

    private suspend fun saveOne(
        type: String,
        provider: String,
        priceStr: String,
        fixedStr: String,
        useManual: Boolean
    ): String? {

        val price = priceStr.toDoubleOrNull()
        val fixed = if (fixedStr.isBlank()) 0.0 else fixedStr.toDoubleOrNull()

        if (useManual) {
            if (provider.isBlank()) return "Completează furnizorul pentru $type."
            if (price == null) return "Preț invalid pentru $type."
            if (fixed == null) return "Abonament invalid pentru $type."
        }

        val typedSomething = provider.isNotBlank() || priceStr.isNotBlank() || fixedStr.isNotBlank()

        // salvăm manual ca fallback dacă are valori valide (chiar dacă nu e activ)
        if (typedSomething && price != null && fixed != null) {
            val err = repo.saveManualTariff(
                type = type,
                provider = provider,
                pricePerUnit = price,
                fixedMonthly = fixed,
                makeActive = useManual
            )
            if (err != null) return err
        }

        // dacă user debifează manual: încercăm să revenim pe AUTO
        if (!useManual) {
            val err = repo.setModeAuto(type)
            // nu blocăm dacă încă nu există AUTO; doar informăm
            if (err != null) {
                Toast.makeText(this, "AUTO pentru $type: $err", Toast.LENGTH_SHORT).show()
            }
        }

        return null
    }

    private fun formatTs(epochMs: Long): String {
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(fmt)
    }

    private fun fmt2(v: Double): String = String.format(java.util.Locale("ro", "RO"), "%.2f", v)
}
