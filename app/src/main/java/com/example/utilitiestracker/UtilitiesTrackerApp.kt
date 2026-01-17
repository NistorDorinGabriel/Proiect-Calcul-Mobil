package com.example.utilitiestracker

import android.app.Application
import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.local.MeterEntity

import com.example.utilitiestracker.data.local.UtilityType
import com.example.utilitiestracker.data.local.TariffEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UtilitiesTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val db = AppDatabase.getInstance(this)

        CoroutineScope(Dispatchers.IO).launch {
            val tariffDao = db.tariffDao()
            if (tariffDao.count() == 0) {
                val now = System.currentTimeMillis()
                tariffDao.insertAll(
                    listOf(
                        TariffEntity(
                            meterType = UtilityType.WATER.name,
                            pricePerUnit = 9.5,      // exemplu; schimbăm ulterior din ecranul Tarife
                            fixedMonthly = 5.0,
                            currency = "RON",
                            updatedAtEpochMs = now
                        ),
                        TariffEntity(
                            meterType = UtilityType.ELECTRICITY.name,
                            pricePerUnit = 0.85,
                            fixedMonthly = 10.0,
                            currency = "RON",
                            updatedAtEpochMs = now
                        ),
                        TariffEntity(
                            meterType = UtilityType.GAS.name,
                            pricePerUnit = 3.2,
                            fixedMonthly = 8.0,
                            currency = "RON",
                            updatedAtEpochMs = now
                        )
                    )
                )
            }
        }
        com.example.utilitiestracker.reminder.ReminderScheduler.scheduleDailyCheck(this)

    }
}
