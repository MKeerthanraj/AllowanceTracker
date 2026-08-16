package com.kaysyndikayte.allowancetracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE dateRangeId = :rangeId ORDER BY timestampMillis DESC")
    fun getForRange(rangeId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE timestampMillis = :timestamp)")
    suspend fun existsWithTimestamp(timestamp: Long): Boolean

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}