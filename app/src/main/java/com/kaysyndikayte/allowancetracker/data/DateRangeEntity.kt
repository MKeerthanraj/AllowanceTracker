package com.kaysyndikayte.allowancetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "date_ranges")
data class DateRangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startEpochDay: Long,   // LocalDate.toEpochDay()
    val endEpochDay: Long,
    val allowanceAmount: Double,
    val isForceEnded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)