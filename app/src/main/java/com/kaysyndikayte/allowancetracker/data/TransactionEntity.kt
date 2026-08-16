package com.kaysyndikayte.allowancetracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = DateRangeEntity::class,
            parentColumns = ["id"],
            childColumns = ["dateRangeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Without this every lookup by range is a full table scan, and so is the cascade Room
    // runs when a date range is deleted. See AppDatabase.MIGRATION_4_5.
    indices = [Index("dateRangeId")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateRangeId: Long,
    val dateEpochDay: Long,
    val timestampMillis: Long, // exact date + time of transaction
    val reason: String,
    val category: String,
    val amount: Double
)