package com.kaysyndikayte.allowancetracker.repository

import com.kaysyndikayte.allowancetracker.data.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Cloud version of the old Room-backed AllowanceRepository.
 *
 * Room's DAOs gave us live-updating Flow<List<T>> for free on every write. Postgrest calls
 * are one-shot suspend functions, so reactivity here comes from Supabase Realtime: we
 * subscribe to postgres_changes on date_ranges / personal_transactions filtered to the
 * current user, and refetch on any INSERT/UPDATE/DELETE. This also means changes made on
 * another device show up here without the user doing anything — Room never had that.
 *
 * This repository owns its own CoroutineScope rather than borrowing viewModelScope, because
 * the realtime subscription is cheap to keep alive for the app's lifetime and — unlike
 * Room's AppDatabase — there's no per-Activity/Context instance to tie it to. If you'd rather
 * tear it down with the ViewModel, pass viewModelScope in from AllowanceViewModel's init
 * block instead (viewModelScope isn't available yet at ViewModel-construction time, which is
 * why the factory can't just hand it in directly).
 */
class AllowanceRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val client = SupabaseClientProvider.client
    private val userId: String
        get() = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not authenticated")

    private val _dateRanges = MutableStateFlow<List<DateRangeDto>>(emptyList())
    private val _transactionsByRange = MutableStateFlow<Map<String, List<PersonalTransactionDto>>>(emptyMap())

    private var started = false

    /** Lazily kicks off the initial load + realtime subscriptions on first use. */
    private fun ensureStarted() {
        if (started) return
        started = true

        scope.launch {
            refreshDateRanges()
        }

        // date_ranges realtime channel
        scope.launch {
            val channel = client.realtime.channel("date_ranges-${userId}")
            val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "date_ranges"
                filter("user_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, userId)
            }
            channel.subscribe()
            changeFlow.collect { refreshDateRanges() }
        }

        // personal_transactions realtime channel — refresh whichever range the change belongs to
        scope.launch {
            val channel = client.realtime.channel("personal_transactions-${userId}")
            val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "personal_transactions"
                filter("user_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, userId)
            }
            channel.subscribe()
            changeFlow.collect { action ->
                val rangeId = extractDateRangeId(action)
                if (rangeId != null) refreshTransactions(rangeId) else refreshAllLoadedTransactionRanges()
            }
        }
    }

    private fun extractDateRangeId(action: PostgresAction): String? {
        val record = when (action) {
            is PostgresAction.Insert -> action.record
            is PostgresAction.Update -> action.record
            is PostgresAction.Delete -> action.oldRecord
            else -> null
        } ?: return null
        return record["date_range_id"]?.toString()?.trim('"')
    }

    private suspend fun refreshAllLoadedTransactionRanges() {
        _transactionsByRange.value.keys.forEach { refreshTransactions(it) }
    }

    private suspend fun refreshDateRanges() {
        val rows = client.postgrest["date_ranges"]
            .select {
                filter { eq("user_id", userId) }
                order("start_epoch_day", Order.DESCENDING)
            }
            .decodeList<DateRangeDto>()
        _dateRanges.value = rows
    }

    private suspend fun refreshTransactions(rangeId: String) {
        val rows = client.postgrest["personal_transactions"]
            .select {
                filter { eq("date_range_id", rangeId) }
                order("timestamp_millis", Order.DESCENDING)
            }
            .decodeList<PersonalTransactionDto>()
        _transactionsByRange.value = _transactionsByRange.value + (rangeId to rows)
    }

    fun getAllDateRanges(): Flow<List<DateRangeDto>> {
        ensureStarted()
        return _dateRanges.asStateFlow()
    }

    fun getDateRange(id: String): Flow<DateRangeDto?> {
        ensureStarted()
        return _dateRanges.map { list -> list.firstOrNull { it.id == id } }
    }

    fun getTransactions(rangeId: String): Flow<List<PersonalTransactionDto>> {
        ensureStarted()
        // Kick off a fetch for this range if we haven't loaded it yet.
        if (rangeId !in _transactionsByRange.value) {
            scope.launch { refreshTransactions(rangeId) }
        }
        return _transactionsByRange.map { it[rangeId] ?: emptyList() }
    }

    suspend fun addDateRange(
        name: String,
        startEpochDay: Long,
        endEpochDay: Long,
        allowanceAmount: Double
    ): String {
        val inserted = client.postgrest["date_ranges"].insert(
            DateRangeInsert(
                user_id = userId,
                name = name,
                start_epoch_day = startEpochDay,
                end_epoch_day = endEpochDay,
                allowance_amount = allowanceAmount
            )
        ) { select() }.decodeSingle<DateRangeDto>()
        refreshDateRanges()
        return inserted.id
    }

    suspend fun updateDateRange(range: DateRangeDto) {
        client.postgrest["date_ranges"].update(
            DateRangeUpdate(
                name = range.name,
                start_epoch_day = range.start_epoch_day,
                end_epoch_day = range.end_epoch_day,
                allowance_amount = range.allowance_amount,
                is_force_ended = range.is_force_ended
            )
        ) { filter { eq("id", range.id) } }
        refreshDateRanges()
    }

    suspend fun deleteDateRange(range: DateRangeDto) {
        // personal_transactions.date_range_id has no ON DELETE CASCADE in the schema you
        // shared, so delete children first or the FK constraint will reject this.
        client.postgrest["personal_transactions"].delete { filter { eq("date_range_id", range.id) } }
        client.postgrest["date_ranges"].delete { filter { eq("id", range.id) } }
        refreshDateRanges()
        _transactionsByRange.value = _transactionsByRange.value - range.id
    }

    suspend fun transactionExistsWithTimestamp(timestamp: Long): Boolean {
        val rows = client.postgrest["personal_transactions"]
            .select {
                filter {
                    eq("user_id", userId)
                    eq("timestamp_millis", timestamp)
                }
            }
            .decodeList<PersonalTransactionDto>()
        return rows.isNotEmpty()
    }

    suspend fun addTransaction(
        dateRangeId: String,
        dateEpochDay: Long,
        timestampMillis: Long,
        reason: String,
        category: String,
        amount: Double
    ) {
        client.postgrest["personal_transactions"].insert(
            PersonalTransactionInsert(
                date_range_id = dateRangeId,
                user_id = userId,
                date_epoch_day = dateEpochDay,
                timestamp_millis = timestampMillis,
                reason = reason,
                category = category,
                amount = amount
            )
        )
        refreshTransactions(dateRangeId)
    }

    suspend fun deleteTransaction(transaction: PersonalTransactionDto) {
        client.postgrest["personal_transactions"].delete { filter { eq("id", transaction.id) } }
        refreshTransactions(transaction.date_range_id)
    }
}