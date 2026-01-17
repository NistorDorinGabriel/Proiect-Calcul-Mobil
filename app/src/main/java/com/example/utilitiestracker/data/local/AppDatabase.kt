package com.example.utilitiestracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


import androidx.room.RoomDatabase

@Database(
    entities = [MeterEntity::class, ReadingEntity::class, TariffEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun meterDao(): MeterDao
    abstract fun readingDao(): ReadingDao

    abstract fun tariffDao(): TariffDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS tariffs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    meterType TEXT NOT NULL,
                    pricePerUnit REAL NOT NULL,
                    fixedMonthly REAL NOT NULL,
                    currency TEXT NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL
                )
            """.trimIndent()
                )
            }

        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE readings ADD COLUMN isReset INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // obligatoriu pt TariffEntity (Expected)
                db.execSQL("ALTER TABLE tariffs ADD COLUMN sourceMode TEXT NOT NULL DEFAULT 'AUTO'")

                // AUTO (internet)
                db.execSQL("ALTER TABLE tariffs ADD COLUMN autoPricePerUnit REAL")
                db.execSQL("ALTER TABLE tariffs ADD COLUMN autoUpdatedAtEpochMs INTEGER")
                db.execSQL("ALTER TABLE tariffs ADD COLUMN autoSource TEXT")

                // MANUAL
                db.execSQL("ALTER TABLE tariffs ADD COLUMN manualPricePerUnit REAL")
                db.execSQL("ALTER TABLE tariffs ADD COLUMN manualFixedMonthly REAL")
                db.execSQL("ALTER TABLE tariffs ADD COLUMN manualProvider TEXT")
                db.execSQL("ALTER TABLE tariffs ADD COLUMN manualUpdatedAtEpochMs INTEGER")
            }
        }



        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "utilities_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
