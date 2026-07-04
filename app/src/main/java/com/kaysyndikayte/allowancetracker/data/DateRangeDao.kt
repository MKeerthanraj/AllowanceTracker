package com.kaysyndikayte.allowancetracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DateRangeDao {
    @Insert
    suspend fun insert(range: DateRangeEntity): Long

    @Query("SELECT * FROM date_ranges ORDER BY startEpochDay DESC")
    fun getAll(): Flow<List<DateRangeEntity>>

    @Query("SELECT * FROM date_ranges WHERE id = :id")
    fun getById(id: Long): Flow<DateRangeEntity?>

    @Update
    suspend fun update(range: DateRangeEntity)

    @Delete
    suspend fun delete(range: DateRangeEntity)
}