package com.kaysyndikayte.allowancetracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Upsert
    suspend fun insert(transaction: PersonalTransactionDto)

    @Query("SELECT * FROM transactions WHERE date_range_id = :rangeId ORDER BY timestamp_millis DESC")
    fun getForRange(rangeId: String): Flow<List<PersonalTransactionDto>>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE timestamp_millis = :timestamp)")
    suspend fun existsWithTimestamp(timestamp: Long): Boolean

    @Update
    suspend fun update(transaction: PersonalTransactionDto)

    @Delete
    suspend fun delete(transaction: PersonalTransactionDto)
}
