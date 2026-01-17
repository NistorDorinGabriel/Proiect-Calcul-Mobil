package com.example.utilitiestracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MeterDao {

    @Query("SELECT * FROM meters ORDER BY sortOrder")
    suspend fun getAllOnce(): List<MeterEntity>

    @Query("SELECT COUNT(*) FROM meters")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MeterEntity>)

    @Query("SELECT unit FROM meters WHERE type = :type LIMIT 1")
    suspend fun getUnitForType(type: String): String?

}
