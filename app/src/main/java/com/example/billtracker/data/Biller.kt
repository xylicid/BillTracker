package com.example.billtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single custom biller the user wants to track each month, e.g. "Amex", "Water Bill".
 *
 * @property position stable creation order, used to sort unpaid items.
 * @property isPaid whether this bill has been checked off for the current month.
 * @property paidAt timestamp when it was marked paid, used to order paid items at the bottom.
 * @property amount optional dollar amount, used for the "total due" summary.
 * @property dueDay legacy day-of-month retained for database compatibility.
 * @property dueDate optional full due date stored as epoch-day. For recurring monthly bills,
 *   the repository advances this date when a new billing period begins.
 */
@Entity(tableName = "billers")
data class Biller(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val isPaid: Boolean = false,
    val paidAt: Long? = null,
    val amount: Double? = null,
    val dueDay: Int? = null,
    val dueDate: Long? = null
)
