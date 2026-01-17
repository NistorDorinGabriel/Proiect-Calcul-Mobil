package com.example.utilitiestracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Dao
interface TariffDao {

    @Query("SELECT * FROM tariffs")
    fun observeAll(): Flow<List<TariffEntity>>

    @Query("SELECT COUNT(*) FROM tariffs")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TariffEntity>)

    @Query("SELECT * FROM tariffs WHERE meterType = :meterType LIMIT 1")
    suspend fun getByType(meterType: String): TariffEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tariff: TariffEntity)

    @Query("DELETE FROM tariffs")
    suspend fun deleteAll()

    @Query("SELECT * FROM tariffs")
    suspend fun getAllOnce(): List<TariffEntity>

}
