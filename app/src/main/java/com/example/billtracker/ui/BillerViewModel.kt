package com.example.billtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.billtracker.data.Biller
import com.example.billtracker.data.BillerDatabase
import com.example.billtracker.data.BillerRepository
import com.example.billtracker.data.Recurrence
import com.example.billtracker.data.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class BillerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BillerRepository
    private val notificationScheduler = BillNotificationScheduler(application)

    val billers: StateFlow<List<Biller>>
    private val _income = MutableStateFlow(0.0)
    val income: StateFlow<Double> = _income.asStateFlow()
    private val _sortMode = MutableStateFlow(SortMode.DUE_DATE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    init {
        val dao = BillerDatabase.getInstance(application).billerDao()
        repository = BillerRepository(dao, application)
        _income.value = repository.getIncome()
        _sortMode.value = repository.getSortMode()

        billers = combine(repository.billers, _sortMode) { list, mode ->
            if (mode == SortMode.DUE_DATE) {
                list.sortedWith(compareBy<Biller> {
                    if (it.isPaid) 1 else 0
                }.thenBy { repository.parseDate(it.dueDate) ?: LocalDate.MAX }
                    .thenBy { it.position })
            } else list
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            repository.normalizeDates()
            billers.collect { notificationScheduler.scheduleAll(it) }
        }
    }

    fun addBiller(name: String, amount: Double?, dueDate: LocalDate?, recurrence: Recurrence) {
        if (name.isBlank() || dueDate == null) return
        viewModelScope.launch { repository.addBiller(name.trim(), amount, dueDate, recurrence) }
    }

    fun updateBiller(biller: Biller, name: String, amount: Double?, dueDate: LocalDate?, recurrence: Recurrence) {
        if (name.isBlank() || dueDate == null) return
        viewModelScope.launch {
            repository.updateBiller(
                biller.copy(
                    name = name.trim(),
                    amount = amount,
                    dueDate = dueDate.toString(),
                    dueDay = dueDate.dayOfMonth,
                    recurrence = recurrence.name,
                    isPaid = if (biller.dueDate == dueDate.toString()) biller.isPaid else false,
                    paidAt = if (biller.dueDate == dueDate.toString()) biller.paidAt else null
                )
            )
        }
    }

    fun togglePaid(biller: Biller) {
        viewModelScope.launch { repository.togglePaid(biller) }
    }

    fun deleteBiller(biller: Biller) {
        notificationScheduler.cancel(biller.id)
        viewModelScope.launch { repository.deleteBiller(biller) }
    }

    fun restoreBiller(biller: Biller) {
        viewModelScope.launch { repository.restoreBiller(biller) }
    }

    fun reorder(orderedUnpaid: List<Biller>) {
        viewModelScope.launch { repository.reorder(orderedUnpaid) }
    }

    fun setIncome(amount: Double) { repository.setIncome(amount); _income.value = amount }
    fun setSortMode(mode: SortMode) { repository.setSortMode(mode); _sortMode.value = mode }
}
