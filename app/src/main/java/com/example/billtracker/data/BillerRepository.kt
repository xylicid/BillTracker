package com.example.billtracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class BillerRepository(
    private val dao: BillerDao,
    context: Context
) {
    private val prefs = context.getSharedPreferences("billtracker_prefs", Context.MODE_PRIVATE)
    val billers: Flow<List<Biller>> = dao.getAll()
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun addBiller(name: String, amount: Double?, dueDate: LocalDate?, recurrence: Recurrence) {
        val position = dao.nextPosition()
        dao.insert(
            Biller(
                name = name,
                position = position,
                amount = amount,
                dueDate = dueDate?.format(formatter),
                recurrence = recurrence.name
            )
        )
    }

    suspend fun deleteBiller(biller: Biller) = dao.delete(biller)
    suspend fun updateBiller(biller: Biller) = dao.update(biller)

    suspend fun reorder(orderedUnpaid: List<Biller>) {
        dao.updateAll(orderedUnpaid.mapIndexed { index, b -> b.copy(position = index) })
    }

    fun getSortMode(): SortMode =
        if (prefs.getString(KEY_SORT_MODE, SortMode.MANUAL.name) == SortMode.DUE_DATE.name)
            SortMode.DUE_DATE else SortMode.MANUAL

    fun setSortMode(mode: SortMode) { prefs.edit().putString(KEY_SORT_MODE, mode.name).apply() }

    suspend fun restoreBiller(biller: Biller) = dao.restore(biller)

    suspend fun togglePaid(biller: Biller) {
        dao.update(
            if (biller.isPaid) biller.copy(isPaid = false, paidAt = null)
            else biller.copy(isPaid = true, paidAt = System.currentTimeMillis())
        )
    }

    /** Convert old v2 dueDay values to a real date and advance completed recurring occurrences. */
    suspend fun normalizeDates() {
        val today = LocalDate.now()
        billers.first().forEach { bill ->
            var date = parseDate(bill.dueDate)
            if (date == null && bill.dueDay != null) {
                date = safeDate(today.year, today.monthValue, bill.dueDay)
                if (date.isBefore(today) && Recurrence.MONTHLY.name == bill.recurrence) {
                    date = advance(date, Recurrence.MONTHLY)
                }
            }
            if (date != null && bill.isPaid && date.isBefore(today) && recurrenceOf(bill) != Recurrence.NONE) {
                var next = date
                do { next = advance(next, recurrenceOf(bill)) } while (next.isBefore(today))
                dao.update(bill.copy(dueDate = next.format(formatter), isPaid = false, paidAt = null, dueDay = next.dayOfMonth))
            } else if (date != null && bill.dueDate != date.format(formatter)) {
                dao.update(bill.copy(dueDate = date.format(formatter), dueDay = date.dayOfMonth))
            }
        }
    }

    fun recurrenceOf(biller: Biller): Recurrence =
        runCatching { Recurrence.valueOf(biller.recurrence) }.getOrDefault(Recurrence.MONTHLY)

    fun parseDate(value: String?): LocalDate? = runCatching { value?.let(LocalDate::parse) }.getOrNull()

    fun advance(date: LocalDate, recurrence: Recurrence): LocalDate = when (recurrence) {
        Recurrence.WEEKLY -> date.plusWeeks(1)
        Recurrence.BI_MONTHLY -> plusMonthsClamped(date, 2)
        Recurrence.MONTHLY -> plusMonthsClamped(date, 1)
        Recurrence.QUARTERLY -> plusMonthsClamped(date, 3)
        Recurrence.ANNUALLY -> {
            val targetYear = date.year + 1
            if (date.monthValue == 2 && date.dayOfMonth == 29)
                LocalDate.of(targetYear, 2, 28)
            else date.withYear(targetYear)
        }
        Recurrence.NONE -> date
    }

    private fun plusMonthsClamped(date: LocalDate, months: Long): LocalDate {
        val ym = YearMonth.from(date).plusMonths(months)
        return date.withYear(ym.year).withMonth(ym.monthValue)
            .withDayOfMonth(minOf(date.dayOfMonth, ym.lengthOfMonth()))
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val ym = YearMonth.of(year, month)
        return LocalDate.of(year, month, minOf(day, ym.lengthOfMonth()))
    }

    fun getResetDay(): Int = prefs.getInt(KEY_RESET_DAY, 1)
    fun setResetDay(day: Int) { prefs.edit().putInt(KEY_RESET_DAY, day.coerceIn(1, 28)).apply() }
    fun getIncome(): Double = prefs.getFloat(KEY_INCOME, 0f).toDouble()
    fun setIncome(amount: Double) { prefs.edit().putFloat(KEY_INCOME, amount.toFloat()).apply() }

    // Kept as a no-op compatibility method. Paid status now follows each bill's recurrence.
    suspend fun checkAndResetIfNewMonth() = normalizeDates()

    companion object {
        private const val KEY_RESET_DAY = "reset_day"
        private const val KEY_INCOME = "income"
        private const val KEY_SORT_MODE = "sort_mode"
    }
}
