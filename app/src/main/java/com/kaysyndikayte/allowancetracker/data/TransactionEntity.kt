package com.kaysyndikayte.allowancetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateRangeId: Long,
    val dateEpochDay: Long,
    val reason: String,
    val category: String,   // Category enum name
    val amount: Double
)