package com.kaysyndikayte.allowancetracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE dateRangeId = :rangeId ORDER BY dateEpochDay DESC, id DESC")
    fun getForRange(rangeId: Long): Flow<List<TransactionEntity>>

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}