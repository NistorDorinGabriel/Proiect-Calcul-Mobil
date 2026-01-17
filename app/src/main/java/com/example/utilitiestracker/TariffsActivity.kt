package com.example.utilitiestracker

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.repository.TariffsRepository
import com.example.utilitiestracker.databinding.ActivityTariffsBinding
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.example.utilitiestracker.service.TariffSyncService

class TariffsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTariffsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTariffsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repo = TariffsRepository(AppDatabase.getInstance(this))

        binding.rvTariffs.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            repo.observeTariffs().collect { items ->
                binding.rvTariffs.adapter = TariffsAdapter(items)
            }
        }

        binding.btnUpdateInternet.setOnClickListener {
            ensureNotificationPermissionThenStartService()
        }
        binding.btnManualTariffs.setOnClickListener {
            startActivity(Intent(this, ManualTariffsActivity::class.java))
        }

    }
    private fun ensureNotificationPermissionThenStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIF
                )
                return
            }
        }
        startTariffSyncService()
    }

    private fun startTariffSyncService() {
        val intent = Intent(this, TariffSyncService::class.java).apply {
            action = TariffSyncService.ACTION_START_SYNC
        }
        // pe API 26+ e recomandat startForegroundService
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            // dacă utilizatorul a acordat, pornești sync-ul
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) startTariffSyncService()
        }
    }

    companion object {
        private const val REQ_NOTIF = 2001
    }
}