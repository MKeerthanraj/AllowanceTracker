package com.kaysyndikayte.allowancetracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DateRangeDao {
    @Upsert
    suspend fun insert(range: DateRangeDto)

    @Query("SELECT * FROM date_ranges ORDER BY start_epoch_day DESC")
    fun getAll(): Flow<List<DateRangeDto>>

    @Query("SELECT * FROM date_ranges WHERE id = :id")
    fun getById(id: String): Flow<DateRangeDto?>

    @Query("SELECT * FROM date_ranges WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: String): DateRangeDto?

    @Update
    suspend fun update(range: DateRangeDto)

    @Delete
    suspend fun delete(range: DateRangeDto)
}
