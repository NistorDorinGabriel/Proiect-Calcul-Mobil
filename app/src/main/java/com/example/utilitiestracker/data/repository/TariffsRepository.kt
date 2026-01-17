package com.example.utilitiestracker.data.repository

import com.example.utilitiestracker.data.local.AppDatabase
import com.example.utilitiestracker.data.local.TariffEntity
import com.example.utilitiestracker.data.remote.bnr.BnrFxClient
import com.example.utilitiestracker.data.remote.eurostat.EurostatTariffClient
import kotlinx.coroutines.flow.Flow


class TariffsRepository(private val db: AppDatabase) {

    private val eurostat = EurostatTariffClient()
    private val bnr = BnrFxClient()

    fun observeTariffs(): Flow<List<TariffEntity>> = db.tariffDao().observeAll()

    private suspend fun saveTariffs(items: List<TariffEntity>) {
        db.tariffDao().deleteAll()
        db.tariffDao().insertAll(items)
    }

    suspend fun refreshFromInternet(): String? {
        return try {
            val now = System.currentTimeMillis()

            val existing = db.tariffDao().getAllOnce()
            fun find(type: String) = existing.firstOrNull { it.meterType == type }

            val eurToRon = bnr.fetchEurToRon()
            val electricityRon = eurostat.fetchLatestEurPerKwh("nrg_pc_204") * eurToRon
            val gasRon = eurostat.fetchLatestEurPerKwh("nrg_pc_202") * eurToRon

            val water = find("WATER") ?: TariffEntity(
                meterType = "WATER",
                pricePerUnit = 0.0,
                fixedMonthly = 0.0,
                currency = "RON",
                updatedAtEpochMs = now
            )

            fun updatedAuto(old: TariffEntity, autoPrice: Double): TariffEntity {
                val newAuto = old.copy(
                    autoPricePerUnit = autoPrice,
                    autoUpdatedAtEpochMs = now,
                    autoSource = "EUROSTAT"
                )
                return if (old.sourceMode == "AUTO") {
                    newAuto.copy(
                        pricePerUnit = autoPrice,
                        currency = "RON",
                        updatedAtEpochMs = now
                    )
                } else {
                    newAuto // manual rămâne activ, dar păstrăm AUTO updatat pentru switch ulterior
                }
            }

            val electricityOld = find("ELECTRICITY") ?: TariffEntity(
                meterType = "ELECTRICITY", pricePerUnit = 0.0, fixedMonthly = 0.0, currency = "RON", updatedAtEpochMs = now
            )
            val gasOld = find("GAS") ?: TariffEntity(
                meterType = "GAS", pricePerUnit = 0.0, fixedMonthly = 0.0, currency = "RON", updatedAtEpochMs = now
            )

            val updated = listOf(
                water,
                updatedAuto(electricityOld, electricityRon),
                updatedAuto(gasOld, gasRon)
            )

            saveTariffs(updated)
            null
        } catch (e: Exception) {
            e.message ?: "Eroare necunoscută la update tarife."
        }
    }

    suspend fun saveManualTariff(
        type: String,
        provider: String,
        pricePerUnit: Double,
        fixedMonthly: Double,
        makeActive: Boolean
    ): String? {
        return try {
            val now = System.currentTimeMillis()
            val list = db.tariffDao().getAllOnce().toMutableList()
            val idx = list.indexOfFirst { it.meterType == type }

            val old = if (idx >= 0) list[idx] else TariffEntity(
                meterType = type,
                pricePerUnit = 0.0,
                fixedMonthly = 0.0,
                currency = "RON",
                updatedAtEpochMs = now
            )

            val withManual = old.copy(
                manualProvider = provider,
                manualPricePerUnit = pricePerUnit,
                manualFixedMonthly = fixedMonthly,
                manualUpdatedAtEpochMs = now
            )

            val final = if (makeActive) {
                withManual.copy(
                    sourceMode = "MANUAL",
                    pricePerUnit = pricePerUnit,
                    fixedMonthly = fixedMonthly,
                    currency = "RON",
                    updatedAtEpochMs = now
                )
            } else withManual

            if (idx >= 0) list[idx] = final else list.add(final)

            db.tariffDao().deleteAll()
            db.tariffDao().insertAll(list)
            null
        } catch (e: Exception) {
            e.message ?: "Eroare la salvare tarif manual."
        }
    }

    suspend fun setModeAuto(type: String): String? {
        return try {
            val list = db.tariffDao().getAllOnce().toMutableList()
            val idx = list.indexOfFirst { it.meterType == type }
            if (idx < 0) return "Tariful nu există încă."

            val old = list[idx]
            val auto = old.autoPricePerUnit ?: return "Nu există tarif AUTO (apasă Update din internet)."
            val ts = old.autoUpdatedAtEpochMs ?: System.currentTimeMillis()

            val updated = old.copy(
                sourceMode = "AUTO",
                pricePerUnit = auto,
                currency = "RON",
                updatedAtEpochMs = ts
            )

            list[idx] = updated
            db.tariffDao().deleteAll()
            db.tariffDao().insertAll(list)
            null
        } catch (e: Exception) {
            e.message ?: "Eroare la comutare pe AUTO."
        }
    }




}
