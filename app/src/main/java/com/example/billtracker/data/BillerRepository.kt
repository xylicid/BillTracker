package com.example.billtracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * Wraps the DAO and owns the "new billing period" reset logic. Everything is local —
 * no accounts, no network — a SharedPreferences flag remembers which period we last
 * reset on, so re-opening the app after the reset day passes clears every checkbox.
 */
class BillerRepository(
    private val dao: BillerDao,
    context: Context
) {
    private val prefs = context.getSharedPreferences("billtracker_prefs", Context.MODE_PRIVATE)

    val billers: Flow<List<Biller>> = dao.getAll()

    suspend fun addBiller(name: String, amount: Double?, dueDate: Long?) {
        val position = dao.nextPosition()
        dao.insert(
            Biller(name = name, position = position, amount = amount, dueDate = dueDate)
        )
    }

    suspend fun deleteBiller(biller: Biller) = dao.delete(biller)

    suspend fun updateBiller(biller: Biller) = dao.update(biller)

    /** Persists a new manual order after a drag-to-reorder gesture. */
    suspend fun reorder(orderedUnpaid: List<Biller>) {
        val withNewPositions = orderedUnpaid.mapIndexed { index, biller -> biller.copy(position = index) }
        dao.updateAll(withNewPositions)
    }

    fun getSortMode(): SortMode =
        if (prefs.getString(KEY_SORT_MODE, SortMode.MANUAL.name) == SortMode.DUE_DATE.name) {
            SortMode.DUE_DATE
        } else {
            SortMode.MANUAL
        }

    fun setSortMode(mode: SortMode) {
        prefs.edit().putString(KEY_SORT_MODE, mode.name).apply()
    }

    suspend fun restoreBiller(biller: Biller) = dao.restore(biller)

    suspend fun togglePaid(biller: Biller) {
        val updated = if (biller.isPaid) {
            biller.copy(isPaid = false, paidAt = null)
        } else {
            biller.copy(isPaid = true, paidAt = System.currentTimeMillis())
        }
        dao.update(updated)
    }

    /** Day of month (1-28) that triggers the monthly reset. Defaults to the 1st. */
    fun getResetDay(): Int = prefs.getInt(KEY_RESET_DAY, 1)

    fun setResetDay(day: Int) {
        prefs.edit().putInt(KEY_RESET_DAY, day.coerceIn(1, 28)).apply()
    }

    /** User-entered monthly income, used for the "remaining after bills" summary. Defaults to 0. */
    fun getIncome(): Double = prefs.getFloat(KEY_INCOME, 0f).toDouble()

    fun setIncome(amount: Double) {
        prefs.edit().putFloat(KEY_INCOME, amount.toFloat()).apply()
    }

    /** Call once on app start. If we've crossed into a new billing period, un-check everything
     * and advance recurring due dates by one month. */
    suspend fun checkAndResetIfNewMonth() {
        val currentPeriod = billingPeriodFor(LocalDate.now(), getResetDay())
        val lastResetPeriod = prefs.getString(KEY_LAST_RESET_PERIOD, null)
        if (lastResetPeriod != currentPeriod) {
            val periodStart = LocalDate.now().let { date ->
                val base = YearMonth.from(date)
                if (date.dayOfMonth >= getResetDay()) base.atDay(getResetDay())
                else base.minusMonths(1).atDay(getResetDay())
            }
            val all = dao.getAllOnce()
            all.forEach { biller ->
                val advanced = biller.dueDate?.let { epoch ->
                    runCatching {
                        var date = LocalDate.ofEpochDay(epoch)
                        while (date.isBefore(periodStart)) date = date.plusMonths(1)
                        date.toEpochDay()
                    }.getOrNull()
                }
                dao.update(biller.copy(isPaid = false, paidAt = null, dueDate = advanced))
            }
            prefs.edit().putString(KEY_LAST_RESET_PERIOD, currentPeriod).apply()
        }
    }

    /** e.g. if resetDay = 1, Sept 4 -> "2026-09". If resetDay = 15, Sept 4 -> "2026-08" (still last period). */
    private fun billingPeriodFor(date: LocalDate, resetDay: Int): String {
        val month = if (date.dayOfMonth >= resetDay) {
            YearMonth.from(date)
        } else {
            YearMonth.from(date).minusMonths(1)
        }
        return month.toString()
    }

    companion object {
        private const val KEY_LAST_RESET_PERIOD = "last_reset_period"
        private const val KEY_RESET_DAY = "reset_day"
        private const val KEY_INCOME = "income"
        private const val KEY_SORT_MODE = "sort_mode"
    }
}
