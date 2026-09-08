package com.example.billtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate
import java.time.YearMonth

@Database(entities = [Biller::class], version = 3, exportSchema = false)
abstract class BillerDatabase : RoomDatabase() {

    abstract fun billerDao(): BillerDao

    companion object {
        /** Adds the full due-date column while preserving all existing bills.
         *  Legacy dueDay values are converted to the current month so existing data keeps working.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE billers ADD COLUMN dueDate INTEGER")
                val today = LocalDate.now()
                val daysInMonth = YearMonth.from(today).lengthOfMonth()
                database.execSQL(
                    "UPDATE billers SET dueDate = ? + (MIN(dueDay, ?) - 1) WHERE dueDay IS NOT NULL",
                    arrayOf(today.withDayOfMonth(1).toEpochDay(), daysInMonth)
                )
            }
        }

        @Volatile
        private var INSTANCE: BillerDatabase? = null

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
