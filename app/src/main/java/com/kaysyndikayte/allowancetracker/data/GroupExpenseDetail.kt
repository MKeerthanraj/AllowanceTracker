package com.kaysyndikayte.allowancetracker.data

import java.math.BigDecimal

data class SplitParticipant(
    val userId: String,
    val displayName: String,
    val amountOwed: BigDecimal
)

/** A receipt line, with who shared it, so an itemized split can be reopened as it was saved. */
data class ExpenseItemDetail(
    val id: String,
    val name: String,
    val price: BigDecimal,
    val participantIds: List<String>
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
    val participants: List<SplitParticipant>,
    val taxAmount: BigDecimal = BigDecimal.ZERO,
    val items: List<ExpenseItemDetail> = emptyList()
)