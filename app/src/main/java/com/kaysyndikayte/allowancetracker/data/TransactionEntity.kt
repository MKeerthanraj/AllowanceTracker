package com.kaysyndikayte.allowancetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateRangeId: Long,
    val dateEpochDay: Long,
    val timestampMillis: Long, // exact date + time of transaction
    val reason: String,
    val category: String,
    val amount: Double
)