package com.example.billtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.billtracker.data.Biller
import com.example.billtracker.data.BillerDatabase
import com.example.billtracker.data.BillerRepository
import com.example.billtracker.data.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BillerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BillerRepository

    /** Bills ordered for display: manual (position-based) or auto-sorted by due date. */
    val billers: StateFlow<List<Biller>>

    private val _resetDay = MutableStateFlow(1)
    val resetDay: StateFlow<Int> = _resetDay.asStateFlow()

    private val _income = MutableStateFlow(0.0)
    val income: StateFlow<Double> = _income.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.MANUAL)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    init {
        val dao = BillerDatabase.getInstance(application).billerDao()
        repository = BillerRepository(dao, application)

        _resetDay.value = repository.getResetDay()
        _income.value = repository.getIncome()
        _sortMode.value = repository.getSortMode()

        billers = combine(repository.billers, _sortMode) { list, mode ->
            when (mode) {
                SortMode.MANUAL -> list // DAO already orders unpaid-by-position, paid-by-paidAt
                SortMode.DUE_DATE -> {
                    val (paid, unpaid) = list.partition { it.isPaid }
                    val sortedUnpaid = unpaid.sortedWith(
                        compareBy({ it.dueDate ?: Long.MAX_VALUE }, { it.position })
                    )
                    sortedUnpaid + paid
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch {
            repository.checkAndResetIfNewMonth()
        }
    }

    fun addBiller(name: String, amount: Double?, dueDate: Long?) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addBiller(name.trim(), amount, dueDate) }
    }

    fun updateBiller(biller: Biller, name: String, amount: Double?, dueDate: Long?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.updateBiller(biller.copy(name = name.trim(), amount = amount, dueDate = dueDate))
        }
    }

    fun togglePaid(biller: Biller) {
        viewModelScope.launch { repository.togglePaid(biller) }
    }

    fun deleteBiller(biller: Biller) {
        viewModelScope.launch { repository.deleteBiller(biller) }
    }

    fun restoreBiller(biller: Biller) {
        viewModelScope.launch { repository.restoreBiller(biller) }
    }

    /** Called after a drag-to-reorder gesture settles. Only meaningful in MANUAL sort mode. */
    fun reorder(orderedUnpaid: List<Biller>) {
        viewModelScope.launch { repository.reorder(orderedUnpaid) }
    }

    fun setResetDay(day: Int) {
        repository.setResetDay(day)
        _resetDay.value = day
    }

    fun setIncome(amount: Double) {
        repository.setIncome(amount)
        _income.value = amount
    }

    fun setSortMode(mode: SortMode) {
        repository.setSortMode(mode)
        _sortMode.value = mode
    }
}
