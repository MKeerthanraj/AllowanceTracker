package com.kaysyndikayte.allowancetracker.repository

import com.kaysyndikayte.allowancetracker.data.SupabaseClientProvider
import com.kaysyndikayte.allowancetracker.logic.ReceiptItem
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
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
private data class ExpenseUpdate(val reason: String, val amount: Double, val split_type: String)

@Serializable
private data class ExpenseItemizedUpdate(
    val reason: String, val amount: Double, val split_type: String,
    val subtotal: Double, val tax_amount: Double
)

@Serializable
private data class ExpenseAmountUpdate(val amount: Double)

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

    /**
     * A cash settlement is recorded as an ordinary expense paid by whoever handed over the
     * money, with a single split against whoever received it. group_balances is a read-only
     * view over expenses + expense_splits -- nothing in the app writes it -- so a settlement
     * shaped this way nets straight off the payer's debt without any schema change.
     *
     * Deliberately not a delete or an edit of the original expenses: the history stays
     * intact and the payment shows up in the group activity like Splitwise does it.
     */
    suspend fun recordSettlement(
        groupId: String,
        paidByUserId: String,
        paidToUserId: String,
        amount: BigDecimal,
        note: String
    ) {
        require(paidByUserId != paidToUserId) { "Can't settle up with yourself" }
        require(amount.signum() > 0) { "Settlement amount must be more than zero" }

        val expense = client.postgrest["expenses"].insert(
            ExpenseInsert(
                group_id = groupId,
                paid_by = paidByUserId,
                reason = note,
                category = "SETTLEMENT",
                amount = amount.toDouble(),
                split_type = "settlement"
            )
        ) { select() }.decodeSingle<ExpenseRow>()

        client.postgrest["expense_splits"].insert(
            SplitInsert(expense.id, paidToUserId, amount.toDouble())
        )
    }

    /**
     * expense_splits is keyed on (expense_id, user_id), so a changed amount is an UPDATE of a
     * row that already exists -- no need to clear the split out and rebuild it. Upserting
     * leaves the expense with a valid set of splits at every moment, and only someone actually
     * removed from the split gets deleted.
     */
    private suspend fun replaceSplits(expenseId: String, amounts: Map<String, BigDecimal>) {
        client.postgrest["expense_splits"].upsert(
            amounts.map { (userId, amount) -> SplitInsert(expenseId, userId, amount.toDouble()) }
        )

        val existing = client.postgrest["expense_splits"]
            .select(Columns.raw("expense_id, user_id, amount_owed")) {
                filter { eq("expense_id", expenseId) }
            }
            .decodeList<SplitInsert>()

        existing.map { it.user_id }.filterNot { amounts.containsKey(it) }.forEach { goneUserId ->
            client.postgrest["expense_splits"].delete {
                filter {
                    eq("expense_id", expenseId)
                    eq("user_id", goneUserId)
                }
            }
        }
    }

    /**
     * expense_item_participants rows point at expense_items rows and the schema has no
     * ON DELETE CASCADE, so the participants must go first or the item delete is rejected
     * with a foreign-key violation.
     */
    private suspend fun deleteItems(expenseId: String) {
        val itemIds = client.postgrest["expense_items"]
            .select(Columns.raw("id")) { filter { eq("expense_id", expenseId) } }
            .decodeList<ItemRow>()
            .map { it.id }
        if (itemIds.isEmpty()) return

        client.postgrest["expense_item_participants"].delete {
            filter { isIn("item_id", itemIds) }
        }
        client.postgrest["expense_items"].delete { filter { eq("expense_id", expenseId) } }

        // A DELETE no policy permits is not an error in PostgREST: it reports success and
        // removes nothing. Inserting on top of that would leave two copies of every line, so
        // check the rows actually went before writing anything.
        val leftover = client.postgrest["expense_items"]
            .select(Columns.raw("id")) { filter { eq("expense_id", expenseId) } }
            .decodeList<ItemRow>()
        check(leftover.isEmpty()) {
            "Couldn't clear the previous items, so nothing was changed. The database is " +
                "missing DELETE permission on expense_items."
        }
    }

    /**
     * Item rows have no natural key -- their id is generated, and a line can be renamed,
     * repriced, added or removed -- so there is no row-to-row mapping to update in place.
     * They are replaced wholesale, which is the one place a DELETE is unavoidable.
     */
    private suspend fun replaceItems(expenseId: String, items: List<ReceiptItem>) {
        deleteItems(expenseId)

        items.forEach { item ->
            val itemRow = client.postgrest["expense_items"].insert(
                ItemInsert(expenseId, item.name, item.price.toDouble())
            ) { select() }.decodeSingle<ItemRow>()

            item.participantIds.forEach { userId ->
                client.postgrest["expense_item_participants"].insert(
                    ItemParticipantInsert(itemRow.id, userId)
                )
            }
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        // Children go explicitly, deepest first, because the schema has no ON DELETE CASCADE.
        deleteItems(expenseId)
        client.postgrest["expense_splits"].delete { filter { eq("expense_id", expenseId) } }
        client.postgrest["expenses"].delete { filter { eq("id", expenseId) } }

        // Same silent-no-op trap as deleteItems: a delete no policy permits "succeeds" and
        // removes nothing, and the expense would pop back on the next refresh.
        val remaining = client.postgrest["expenses"]
            .select(Columns.raw("id")) { filter { eq("id", expenseId) } }
            .decodeList<ExpenseRow>()
        check(remaining.isEmpty()) {
            "Couldn't delete this expense. The database is missing DELETE permission " +
                "on expenses."
        }
    }

    /**
     * Replaces the split on an existing expense. The expense row is updated rather than
     * recreated so its id, group and created_at survive, which keeps it in the same place in
     * the activity list and keeps any reference to it intact.
     *
     * Item rows are dropped when an itemized expense is re-split by hand: they describe a
     * per-item breakdown that no longer matches the amounts, and leaving them would misreport
     * who had what.
     */
    suspend fun updateSplit(
        expenseId: String,
        reason: String,
        totalAmount: BigDecimal,
        splitType: String,
        amounts: Map<String, BigDecimal>
    ) {
        require(amounts.isNotEmpty()) { "A split needs at least one person" }

        client.postgrest["expenses"].update(
            ExpenseUpdate(
                reason = reason,
                amount = totalAmount.toDouble(),
                split_type = splitType
            )
        ) { filter { eq("id", expenseId) } }

        replaceSplits(expenseId, amounts)
    }

    /**
     * Re-saves a receipt split with its items intact. The item rows are replaced rather than
     * patched because people can be added to or removed from any line, and an item itself can
     * be added or deleted, so there is no stable row-to-row mapping to update in place.
     */
    suspend fun updateItemizedSplit(
        expenseId: String,
        reason: String,
        subtotal: BigDecimal,
        taxAmount: BigDecimal,
        items: List<ReceiptItem>,
        amounts: Map<String, BigDecimal>
    ) {
        require(items.isNotEmpty()) { "A receipt split needs at least one item" }

        val total = subtotal.add(taxAmount)
        client.postgrest["expenses"].update(
            ExpenseItemizedUpdate(
                reason = reason,
                amount = total.toDouble(),
                split_type = "itemized",
                subtotal = subtotal.toDouble(),
                tax_amount = taxAmount.toDouble()
            )
        ) { filter { eq("id", expenseId) } }

        replaceItems(expenseId, items)
        replaceSplits(expenseId, amounts)
    }

    /**
     * A settlement is one person handing cash to another, so the only thing that can sensibly
     * change is how much. Who paid whom stays as recorded.
     */
    suspend fun updateSettlementAmount(
        expenseId: String,
        paidToUserId: String,
        amount: BigDecimal
    ) {
        require(amount.signum() > 0) { "Settlement amount must be more than zero" }

        client.postgrest["expenses"].update(
            ExpenseAmountUpdate(amount.toDouble())
        ) { filter { eq("id", expenseId) } }

        // One row, same person: a plain upsert, no delete involved.
        replaceSplits(expenseId, mapOf(paidToUserId to amount))
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