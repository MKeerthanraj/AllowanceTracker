package com.kaysyndikayte.allowancetracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * DTOs for DateRange and PersonalTransaction.
 *
 * IDs are String (Postgres uuid) instead of Room's autoGenerate Long.
 */
@Serializable
@Entity(tableName = "date_ranges")
data class DateRangeDto(
    @PrimaryKey val id: String,
    val user_id: String,
    val name: String,
    val start_epoch_day: Long,
    val end_epoch_day: Long,
    val allowance_amount: Double,
    val is_force_ended: Boolean = false,
    val created_at: String? = null
)

@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = DateRangeDto::class,
            parentColumns = ["id"],
            childColumns = ["date_range_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("date_range_id")]
)
data class PersonalTransactionDto(
    @PrimaryKey val id: String,
    val date_range_id: String,
    val user_id: String,
    val date_epoch_day: Long,
    val timestamp_millis: Long,
    val reason: String,
    val category: String,
    val amount: Double,
    val is_from_group_split: Boolean = false,
    val source_expense_id: String? = null,
    val created_at: String? = null
)

// Insert payloads omit fields the DB defaults (id, created_at) — same pattern as
// ExpenseInsert / GroupInsert in the group repositories.

@Serializable
data class DateRangeInsert(
    val user_id: String,
    val name: String,
    val start_epoch_day: Long,
    val end_epoch_day: Long,
    val allowance_amount: Double,
    val is_force_ended: Boolean = false
)

@Serializable
data class DateRangeUpdate(
    val name: String,
    val start_epoch_day: Long,
    val end_epoch_day: Long,
    val allowance_amount: Double,
    val is_force_ended: Boolean
)

@Serializable
data class PersonalTransactionInsert(
    val date_range_id: String,
    val user_id: String,
    val date_epoch_day: Long,
    val timestamp_millis: Long,
    val reason: String,
    val category: String,
    val amount: Double,
    val is_from_group_split: Boolean = false,
    val source_expense_id: String? = null
)