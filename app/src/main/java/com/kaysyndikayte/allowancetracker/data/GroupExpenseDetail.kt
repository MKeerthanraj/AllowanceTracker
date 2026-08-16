package com.kaysyndikayte.allowancetracker.data

import java.math.BigDecimal

data class SplitParticipant(
    val userId: String,
    val displayName: String,
    val amountOwed: BigDecimal
)

data class GroupExpenseDetail(
    val id: String,
    val reason: String,
    val category: String,
    val amount: BigDecimal,
    val splitType: String,
    val paidByUserId: String,
    val paidByName: String,
    val createdAt: String,
    val participants: List<SplitParticipant>
)