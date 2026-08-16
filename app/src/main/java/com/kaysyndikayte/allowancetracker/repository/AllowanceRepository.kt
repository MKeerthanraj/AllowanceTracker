package com.kaysyndikayte.allowancetracker.repository

import com.kaysyndikayte.allowancetracker.data.*
import kotlinx.coroutines.flow.Flow

class AllowanceRepository(
    private val dateRangeDao: DateRangeDao,
    private val transactionDao: TransactionDao
) {
    fun getAllDateRanges(): Flow<List<DateRangeEntity>> = dateRangeDao.getAll()

    fun getDateRange(id: Long): Flow<DateRangeEntity?> = dateRangeDao.getById(id)

    fun getTransactions(rangeId: Long): Flow<List<TransactionEntity>> =
        transactionDao.getForRange(rangeId)

    suspend fun addDateRange(range: DateRangeEntity): Long = dateRangeDao.insert(range)

    suspend fun updateDateRange(range: DateRangeEntity) = dateRangeDao.update(range)

    suspend fun addTransaction(transaction: TransactionEntity) =
        transactionDao.insert(transaction)

    suspend fun transactionExistsWithTimestamp(timestamp: Long): Boolean =
        transactionDao.existsWithTimestamp(timestamp)

    suspend fun deleteDateRange(range: DateRangeEntity) = dateRangeDao.delete(range)

    suspend fun deleteTransaction(transaction: TransactionEntity) =
        transactionDao.delete(transaction)
}