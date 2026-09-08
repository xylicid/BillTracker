package com.example.billtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Biller::class], version = 3, exportSchema = false)
abstract class BillerDatabase : RoomDatabase() {
    abstract fun billerDao(): BillerDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE billers ADD COLUMN dueDate TEXT")
                db.execSQL("ALTER TABLE billers ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'MONTHLY'")
            }
        }

        @Volatile private var INSTANCE: BillerDatabase? = null

        fun getInstance(context: Context): BillerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BillerDatabase::class.java,
                    "billtracker.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
