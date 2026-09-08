package com.example.billtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Recurrence(val label: String) {
    NONE("Doesn't repeat"),
    WEEKLY("Weekly"),
    BI_MONTHLY("Every 2 months"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    ANNUALLY("Annually")
}

/** A bill occurrence. dueDate is the next/current due date in yyyy-MM-dd format. */
@Entity(tableName = "billers")
data class Biller(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val isPaid: Boolean = false,
    val paidAt: Long? = null,
    val amount: Double? = null,
    val dueDay: Int? = null, // Kept for migration compatibility with v2.
    val dueDate: String? = null,
    val recurrence: String = Recurrence.MONTHLY.name
)
