package com.kaysyndikayte.allowancetracker.repository

import com.kaysyndikayte.allowancetracker.data.SupabaseClientProvider
import com.kaysyndikayte.allowancetracker.logic.ReceiptItem
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
private data class ExpenseInsert(
    val group_id: String, val paid_by: String, val reason: String,
    val category: String, val amount: Double, val split_type: String,
    val subtotal: Double? = null, val tax_amount: Double = 0.0
)

@Serializable
private data class ExpenseRow(val id: String)

@Serializable
private data class SplitInsert(val expense_id: String, val user_id: String, val amount_owed: Double)

@Serializable
private data class ItemInsert(val expense_id: String, val name: String, val price: Double)

@Serializable
private data class ItemRow(val id: String)

@Serializable
private data class ItemParticipantInsert(val item_id: String, val user_id: String)

class ExpenseRepository {
    private val client = SupabaseClientProvider.client

    suspend fun saveManualSplit(
        groupId: String, paidBy: String, reason: String, category: String,
        totalAmount: BigDecimal, splitType: String, amounts: Map<String, BigDecimal>
    ) {
        val expense = client.postgrest["expenses"].insert(
            ExpenseInsert(groupId, paidBy, reason, category, totalAmount.toDouble(), splitType)
        ) { select() }.decodeSingle<ExpenseRow>()

        amounts.forEach { (userId, amount) ->
            client.postgrest["expense_splits"].insert(
                SplitInsert(expense.id, userId, amount.toDouble())
            )
        }
    }

    suspend fun saveItemizedSplit(
        groupId: String, paidBy: String, reason: String, category: String,
        subtotal: BigDecimal, taxAmount: BigDecimal,
        items: List<ReceiptItem>, amounts: Map<String, BigDecimal>
    ) {
        val total = subtotal.add(taxAmount)
        val expense = client.postgrest["expenses"].insert(
            ExpenseInsert(groupId, paidBy, reason, category, total.toDouble(), "itemized", subtotal.toDouble(), taxAmount.toDouble())
        ) { select() }.decodeSingle<ExpenseRow>()

        items.forEach { item ->
            val itemRow = client.postgrest["expense_items"].insert(
                ItemInsert(expense.id, item.name, item.price.toDouble())
            ) { select() }.decodeSingle<ItemRow>()

            item.participantIds.forEach { userId ->
                client.postgrest["expense_item_participants"].insert(
                    ItemParticipantInsert(itemRow.id, userId)
                )
            }
        }

        amounts.forEach { (userId, amount) ->
            client.postgrest["expense_splits"].insert(
                SplitInsert(expense.id, userId, amount.toDouble())
            )
        }
    }
}