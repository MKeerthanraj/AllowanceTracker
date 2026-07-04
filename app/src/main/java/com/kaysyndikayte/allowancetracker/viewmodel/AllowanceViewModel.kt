package com.kaysyndikayte.allowancetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaysyndikayte.allowancetracker.data.DateRangeEntity
import com.kaysyndikayte.allowancetracker.data.TransactionEntity
import com.kaysyndikayte.allowancetracker.repository.AllowanceRepository
import com.kaysyndikayte.allowancetracker.utils.DateUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategorySpend(val category: String, val total: Double, val percent: Double)

data class RangeAnalytics(
    val totalSpent: Double,
    val perCategory: List<CategorySpend>,
    val topCategory: CategorySpend?
)

class AllowanceViewModel(private val repository: AllowanceRepository) : ViewModel() {

    private val _selectedRangeId = MutableStateFlow<Long?>(null)
    val selectedRangeId: StateFlow<Long?> = _selectedRangeId

    val allDateRanges: StateFlow<List<DateRangeEntity>> = repository.getAllDateRanges()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    // Auto-select the most recent range once ranges load, if nothing selected yet
    init {
        viewModelScope.launch {
            allDateRanges.collect { ranges ->
                if (_selectedRangeId.value == null && ranges.isNotEmpty()) {
                    _selectedRangeId.value = ranges.first().id
                }
            }
        }
    }

    fun selectRange(id: Long) { _selectedRangeId.value = id }

    val selectedRange: StateFlow<DateRangeEntity?> = _selectedRangeId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.getDateRange(id) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    val transactionsForSelectedRange: StateFlow<List<TransactionEntity>> = _selectedRangeId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.getTransactions(id) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** perDay, earnedOrDebt, remaining */
    data class AllowanceSummary(
        val perDayAllowance: Double,
        val earnedOrDebt: Double,
        val remaining: Double,
        val totalSpent: Double
    )

    val summary: StateFlow<AllowanceSummary?> = combine(
        selectedRange, transactionsForSelectedRange
    ) { range, transactions ->
        if (range == null) return@combine null
        val totalDays = DateUtils.totalDays(range.startEpochDay, range.endEpochDay)
        val perDay = if (totalDays > 0) range.allowanceAmount / totalDays else 0.0
        val elapsed = DateUtils.daysElapsed(range.startEpochDay, range.endEpochDay)
        val totalSpent = transactions.sumOf { it.amount }
        val earned = (perDay * elapsed) - totalSpent
        val remaining = range.allowanceAmount - totalSpent
        AllowanceSummary(perDay, earned, remaining, totalSpent)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    val analytics: StateFlow<RangeAnalytics?> = transactionsForSelectedRange.map { transactions ->
        if (transactions.isEmpty()) return@map RangeAnalytics(0.0, emptyList(), null)
        val total = transactions.sumOf { it.amount }
        val grouped = transactions.groupBy { it.category }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                CategorySpend(cat, sum, if (total > 0) (sum / total) * 100 else 0.0)
            }
            .sortedByDescending { it.total }
        RangeAnalytics(total, grouped, grouped.firstOrNull())
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    fun addDateRange(startEpochDay: Long, endEpochDay: Long, amount: Double) {
        viewModelScope.launch {
            val newId = repository.addDateRange(
                DateRangeEntity(startEpochDay = startEpochDay, endEpochDay = endEpochDay, allowanceAmount = amount)
            )
            _selectedRangeId.value = newId
        }
    }

    fun addTransaction(reason: String, category: String, amount: Double) {
        val rangeId = _selectedRangeId.value ?: return
        viewModelScope.launch {
            repository.addTransaction(
                TransactionEntity(
                    dateRangeId = rangeId,
                    dateEpochDay = java.time.LocalDate.now().toEpochDay(),
                    reason = reason,
                    category = category,
                    amount = amount
                )
            )
        }
    }
}