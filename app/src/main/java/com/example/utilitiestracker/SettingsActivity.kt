package com.example.utilitiestracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.utilitiestracker.databinding.ActivitySettingsBinding
import com.example.utilitiestracker.reminder.MonthlyReminderWorker
import com.example.utilitiestracker.reminder.ReminderScheduler
import androidx.core.content.edit

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean(MonthlyReminderWorker.KEY_ENABLED, true)
        binding.swReminder.isChecked = enabled

        binding.swReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!ensureNotifPermission()) {
                    binding.swReminder.isChecked = false
                    return@setOnCheckedChangeListener
                }
                prefs.edit { putBoolean(MonthlyReminderWorker.KEY_ENABLED, true) }
                ReminderScheduler.scheduleDailyCheck(this)
            } else {
                prefs.edit { putBoolean(MonthlyReminderWorker.KEY_ENABLED, false) }
                ReminderScheduler.cancel(this)
            }
        }
    }

    private fun ensureNotifPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
            return false
        }
        return true
    }
}
