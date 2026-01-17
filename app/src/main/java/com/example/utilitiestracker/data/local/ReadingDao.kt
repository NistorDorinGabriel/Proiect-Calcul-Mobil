package com.example.utilitiestracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ReadingWithMeter(
    val id: Long,
    val meterId: Long,
    val dateEpochDay: Long,
    val value: Double,
    val isReset: Boolean,
    val meterName: String,
    val meterType: String,
    val meterUnit: String
)

@Dao
interface ReadingDao {

    @Insert
    suspend fun insert(reading: ReadingEntity)

    @Query("""
        SELECT r.id, r.meterId, r.dateEpochDay, r.value,
               r.isReset AS isReset,
               m.name AS meterName, m.type AS meterType, m.unit AS meterUnit
        FROM readings r
        INNER JOIN meters m ON r.meterId = m.id
        ORDER BY r.dateEpochDay DESC, r.id DESC
    """)
    fun observeReadingsWithMeter(): Flow<List<ReadingWithMeter>>

    @Query("""
        SELECT * FROM readings
        WHERE meterId = :meterId
        ORDER BY dateEpochDay DESC, id DESC
        LIMIT 1
    """)
    suspend fun getLatestForMeter(meterId: Long): ReadingEntity?

    @Query("""
        SELECT * FROM readings
        WHERE id = :id
        LIMIT 1
    """)
    suspend fun getById(id: Long): ReadingEntity?

    @Query("""
        SELECT id FROM readings
        WHERE meterId = :meterId AND dateEpochDay = :dateEpochDay
        LIMIT 1
    """)
    suspend fun findIdByMeterAndDate(meterId: Long, dateEpochDay: Long): Long?

    @Query("""
        SELECT id FROM readings
        WHERE meterId = :meterId AND dateEpochDay = :dateEpochDay AND id != :excludeId
        LIMIT 1
    """)
    suspend fun findIdByMeterAndDateExcluding(
        meterId: Long,
        dateEpochDay: Long,
        excludeId: Long
    ): Long?

    @Update
    suspend fun update(reading: ReadingEntity)

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        SELECT * FROM readings
        WHERE meterId = :meterId AND dateEpochDay < :dateEpochDay
        ORDER BY dateEpochDay DESC, id DESC
        LIMIT 1
    """)
    suspend fun getPreviousForMeter(meterId: Long, dateEpochDay: Long): ReadingEntity?

    @Query("""
        SELECT * FROM readings
        WHERE meterId = :meterId AND dateEpochDay > :dateEpochDay
        ORDER BY dateEpochDay ASC, id ASC
        LIMIT 1
    """)
    suspend fun getNextForMeter(meterId: Long, dateEpochDay: Long): ReadingEntity?

    @Query("""
        SELECT * FROM readings
        WHERE meterId = :meterId AND dateEpochDay < :dateEpochDay AND id != :excludeId
        ORDER BY dateEpochDay DESC, id DESC
        LIMIT 1
    """)
    suspend fun getPreviousForMeterExcluding(
        meterId: Long,
        dateEpochDay: Long,
        excludeId: Long
    ): ReadingEntity?

    @Query("""
        SELECT * FROM readings
        WHERE meterId = :meterId AND dateEpochDay > :dateEpochDay AND id != :excludeId
        ORDER BY dateEpochDay ASC, id ASC
        LIMIT 1
    """)
    suspend fun getNextForMeterExcluding(
        meterId: Long,
        dateEpochDay: Long,
        excludeId: Long
    ): ReadingEntity?

    @Query("""
        SELECT r.id, r.meterId, r.dateEpochDay, r.value,
               r.isReset AS isReset,
               m.name AS meterName, m.type AS meterType, m.unit AS meterUnit
        FROM readings r
        INNER JOIN meters m ON r.meterId = m.id
        ORDER BY m.sortOrder ASC, r.dateEpochDay ASC, r.id ASC
    """)
    suspend fun getAllReadingsForExport(): List<ReadingWithMeter>

    @Query("""
    SELECT DISTINCT (dateEpochDay / 31) AS bucket
    FROM readings
    ORDER BY bucket DESC
""")
    suspend fun getMonthBuckets(): List<Long>

    @Query("""
    SELECT COUNT(DISTINCT meterId)
    FROM readings
    WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
""")
    suspend fun countMetersWithReadingInRange(startEpochDay: Long, endEpochDay: Long): Int


    @Query("""
    SELECT r.id AS id,
           r.dateEpochDay AS dateEpochDay,
           r.value AS value,
           r.isReset AS isReset,
           m.type AS meterType,
           m.name AS meterName,
           m.unit AS unit
    FROM readings r
    INNER JOIN meters m ON m.id = r.meterId
    WHERE r.dateEpochDay >= :startEpochDay AND r.dateEpochDay < :endEpochDay
    ORDER BY r.dateEpochDay DESC, m.sortOrder ASC
""")
    fun observeMonthAllReadings(
        startEpochDay: Long,
        endEpochDay: Long
    ): kotlinx.coroutines.flow.Flow<List<ReadingListItem>>


}
