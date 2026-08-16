package com.kaysyndikayte.allowancetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaysyndikayte.allowancetracker.data.DateRangeDto
import com.kaysyndikayte.allowancetracker.data.PersonalTransactionDto
import com.kaysyndikayte.allowancetracker.repository.AllowanceRepository
import com.kaysyndikayte.allowancetracker.utils.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategorySpend(val category: String, val total: Double, val percent: Double)

data class RangeAnalytics(
    val totalSpent: Double,
    val perCategory: List<CategorySpend>,
    val topCategory: CategorySpend?,
)

class AllowanceViewModel(private val repository: AllowanceRepository) : ViewModel() {

    // Long -> String: Supabase ids are uuid text, not Room autoincrement longs.
    private val _selectedRangeId = MutableStateFlow<String?>(null)

    val allDateRanges: StateFlow<List<DateRangeDto>> = repository.getAllDateRanges()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Auto-select the most recent range once ranges load, if nothing selected yet
    init {
        viewModelScope.launch {
            allDateRanges.collect { ranges ->
                if ((_selectedRangeId.value == null) && ranges.isNotEmpty()) {
                    _selectedRangeId.value = ranges.first().id
                }
            }
        }
    }

    fun selectRange(id: String) { _selectedRangeId.value = id }

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedRange: StateFlow<DateRangeDto?> = _selectedRangeId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.getDateRange(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactionsForSelectedRange: StateFlow<List<PersonalTransactionDto>> = _selectedRangeId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.getTransactions(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** perDay, earnedOrDebt, remaining */
    data class AllowanceSummary(
        val perDayAllowance: Double,
        val earnedOrDebt: Double,
        val remaining: Double,
        val totalSpent: Double,
        val daysToClearDebt: Int?,  // null when not in debt, or when debt can't be cleared before period ends
        val isEnded: Boolean
    )

    val summary: StateFlow<AllowanceSummary?> = combine(
        selectedRange, transactionsForSelectedRange
    ) { range, transactions ->
        if (range == null) return@combine null
        val totalDays = DateUtils.totalDays(range.start_epoch_day, range.end_epoch_day)
        val perDay = if (totalDays > 0) range.allowance_amount / totalDays else 0.0
        val elapsed = DateUtils.daysElapsed(range.start_epoch_day, range.end_epoch_day)
        val totalSpent = transactions.sumOf { it.amount }
        val earned = (perDay * elapsed) - totalSpent
        val remaining = range.allowance_amount - totalSpent

        val daysToClear: Int? = if (earned < 0 && perDay > 0) {
            val daysNeeded = kotlin.math.ceil(kotlin.math.abs(earned) / perDay).toInt()
            val daysLeftInPeriod = (totalDays - elapsed).toInt()
            if (daysNeeded <= daysLeftInPeriod) daysNeeded else null // can't clear before period ends
        } else null

        val isEnded = range.is_force_ended || java.time.LocalDate.now().toEpochDay() > range.end_epoch_day

        AllowanceSummary(perDay, earned, remaining, totalSpent, daysToClear, isEnded)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val analytics: StateFlow<RangeAnalytics?> = transactionsForSelectedRange.map { transactions ->
        if (transactions.isEmpty()) return@map RangeAnalytics(0.0, emptyList(), null)
        val total = transactions.sumOf { it.amount }
        val grouped = transactions.asSequence().groupBy { it.category }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                CategorySpend(cat, sum, if (total > 0) (sum / total) * 100 else 0.0)
            }
            .toList()
            .sortedByDescending { it.total }
        RangeAnalytics(total, grouped, grouped.firstOrNull())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _eventFlow = MutableSharedFlow<AllowanceEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    sealed class AllowanceEvent {
        data class DuplicateTransaction(val reason: String, val amount: Double) : AllowanceEvent()
    }

    fun addDateRange(name: String, startEpochDay: Long, endEpochDay: Long, amount: Double) {
        viewModelScope.launch {
            val newId = repository.addDateRange(
                name = name,
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay,
                allowanceAmount = amount
            )
            _selectedRangeId.value = newId
        }
    }

    fun forceEndCurrentRange() {
        val range = selectedRange.value ?: return
        viewModelScope.launch {
            repository.updateDateRange(range.copy(is_force_ended = true))
        }
    }

    fun addTransaction(reason: String, category: String, amount: Double, timestampMillis: Long) {
        val rangeId = _selectedRangeId.value ?: return
        viewModelScope.launch {
            if (repository.transactionExistsWithTimestamp(timestampMillis)) {
                _eventFlow.emit(AllowanceEvent.DuplicateTransaction(reason, amount))
                return@launch
            }
            repository.addTransaction(
                dateRangeId = rangeId,
                dateEpochDay = java.time.Instant.ofEpochMilli(timestampMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                timestampMillis = timestampMillis,
                reason = reason,
                category = category,
                amount = amount
            )
        }
    }

    fun deleteTransaction(transaction: PersonalTransactionDto) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun deleteDateRange(range: DateRangeDto) {
        viewModelScope.launch {
            repository.deleteDateRange(range)
            // If the deleted range was selected, clear selection so the app picks a new one
            if (_selectedRangeId.value == range.id) {
                _selectedRangeId.value = null
            }
        }
    }
}