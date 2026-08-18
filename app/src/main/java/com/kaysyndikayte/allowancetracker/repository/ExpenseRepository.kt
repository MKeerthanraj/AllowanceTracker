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

private const val UPDATE_REFUSED =
    "Couldn't save this expense. The database is missing UPDATE permission on expenses."

class ExpenseRepository {
    private val client = SupabaseClientProvider.client

    suspend fun saveManualSplit(
        groupId: String, paidBy: String, reason: String, category: String,
        totalAmount: BigDecimal, splitType: String, amounts: Map<String, BigDecimal>
    ) {
        val expense = client.postgrest["expenses"].insert(
            ExpenseInsert(groupId, paidBy, reason, category, totalAmount.toDouble(), splitType)
        ) { select() }.decodeSingle<ExpenseRow>()

        insertSplits(expense.id, amounts)
    }

    private suspend fun insertSplits(expenseId: String, amounts: Map<String, BigDecimal>) {
        if (amounts.isEmpty()) return
        client.postgrest["expense_splits"].insert(
            amounts.map { (userId, amount) -> SplitInsert(expenseId, userId, amount.toDouble()) }
        )
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

        try {
            client.postgrest["expense_splits"].insert(
                SplitInsert(expense.id, paidToUserId, amount.toDouble())
            )
        } catch (e: Exception) {
            // The expense row is already committed. A settlement with no split is invisible to
            // group_balances -- so the debt stays -- but visible in group activity, and the
            // detail screen refuses to edit it for want of a recipient. The natural retry would
            // then log a second payment for the same cash. Take the half-written one back out.
            runCatching {
                client.postgrest["expenses"].delete { filter { eq("id", expense.id) } }
            }
            throw e
        }
    }

    /**
     * expense_splits is keyed on (expense_id, user_id), so a changed amount is an UPDATE of a
     * row that already exists -- no need to clear the split out and rebuild it. Upserting
     * leaves the expense with a valid set of splits at every moment, and only someone actually
     * removed from the split gets deleted.
     */
    private suspend fun replaceSplits(expenseId: String, amounts: Map<String, BigDecimal>) {
        // Named explicitly rather than left to PostgREST to infer from the primary key: if the
        // table ever grows a surrogate id, an inferred target would stop matching and every
        // edit would either conflict or quietly insert a second row per person, doubling the
        // balances. supabase/edit_delete_split_policies.sql asserts the matching unique index.
        client.postgrest["expense_splits"].upsert(
            amounts.map { (userId, amount) -> SplitInsert(expenseId, userId, amount.toDouble()) }
        ) { onConflict = "expense_id,user_id" }

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
    private suspend fun itemIdsOf(expenseId: String): List<String> =
        client.postgrest["expense_items"]
            .select(Columns.raw("id")) { filter { eq("expense_id", expenseId) } }
            .decodeList<ItemRow>()
            .map { it.id }

    private suspend fun deleteItemRows(expenseId: String) {
        val itemIds = itemIdsOf(expenseId)
        if (itemIds.isEmpty()) return

        client.postgrest["expense_item_participants"].delete {
            filter { isIn("item_id", itemIds) }
        }
        client.postgrest["expense_items"].delete { filter { eq("expense_id", expenseId) } }
    }

    /**
     * Item rows have no natural key -- their id is generated, and a line can be renamed,
     * repriced, added or removed -- so there is no row-to-row mapping to update in place.
     * They are replaced wholesale, which is the one place a DELETE is unavoidable.
     */
    private suspend fun replaceItems(expenseId: String, items: List<ReceiptItem>) {
        deleteItemRows(expenseId)

        // A DELETE no policy permits is not an error in PostgREST: it reports success and
        // removes nothing. Inserting on top of that would leave two copies of every line, so
        // check the rows actually went before writing anything.
        check(itemIdsOf(expenseId).isEmpty()) {
            "Couldn't clear the previous items, so nothing was changed. The database is " +
                "missing DELETE permission on expense_items."
        }

        insertItems(expenseId, items)
    }

    private suspend fun insertItems(expenseId: String, items: List<ReceiptItem>) {
        items.forEach { item ->
            val itemRow = client.postgrest["expense_items"].insert(
                ItemInsert(expenseId, item.name, item.price.toDouble())
            ) { select() }.decodeSingle<ItemRow>()

            // One call per item rather than one per person on it: a shared receipt was
            // otherwise dozens of sequential round trips before the screen came back.
            if (item.participantIds.isNotEmpty()) {
                client.postgrest["expense_item_participants"].insert(
                    item.participantIds.distinct().map { ItemParticipantInsert(itemRow.id, it) }
                )
            }
        }
    }

    private suspend fun expenseExists(expenseId: String): Boolean =
        client.postgrest["expenses"]
            .select(Columns.raw("id")) { filter { eq("id", expenseId) } }
            .decodeList<ExpenseRow>()
            .isNotEmpty()

    /**
     * The expense itself goes first, and nothing else is touched until it is known to be
     * deletable. Clearing the children first risked the worst outcome on offer: where the
     * expenses DELETE policy is missing the delete "succeeds" and removes nothing, so the
     * expense survived with every split already destroyed -- group_balances quietly dropped
     * what members owed for it while the app reported that nothing had changed.
     *
     * The two ways the first delete can fail are distinguishable, which is what makes this
     * safe. A refusal by policy is not an error: it returns success and leaves the row there.
     * A foreign key still pointing at the row *is* an error -- and it can only be raised by a
     * delete the policy allowed, so hitting it proves the parent will go once the children do.
     */
    suspend fun deleteExpense(expenseId: String) {
        val blockedByChildren = try {
            client.postgrest["expenses"].delete { filter { eq("id", expenseId) } }
            false
        } catch (e: Exception) {
            true
        }

        if (!blockedByChildren) {
            check(!expenseExists(expenseId)) {
                "Couldn't delete this expense, so nothing was changed. The database is " +
                    "missing DELETE permission on expenses."
            }
            // Whatever the schema didn't cascade is orphaned now. Clearing it is housekeeping,
            // not part of the delete, so a failure here must not report the delete as failed.
            runCatching { deleteItemRows(expenseId) }
            runCatching {
                client.postgrest["expense_splits"].delete { filter { eq("expense_id", expenseId) } }
            }
            return
        }

        deleteItemRows(expenseId)
        client.postgrest["expense_splits"].delete { filter { eq("expense_id", expenseId) } }
        client.postgrest["expenses"].delete { filter { eq("id", expenseId) } }
        check(!expenseExists(expenseId)) {
            "Couldn't delete this expense. The database is missing DELETE permission " +
                "on expenses."
        }
    }

    /**
     * Replaces the split on an existing expense. The expense row is updated rather than
     * recreated so its id, group and created_at survive, which keeps it in the same place in
     * the activity list and keeps any reference to it intact.
     *
     * Only reachable for non-itemized expenses -- ExpenseDetailScreen sends receipt splits to
     * ItemSplitScreen instead -- so there are no item rows here to keep in step.
     */
    suspend fun updateSplit(
        expenseId: String,
        reason: String,
        totalAmount: BigDecimal,
        splitType: String,
        amounts: Map<String, BigDecimal>
    ) {
        require(amounts.isNotEmpty()) { "A split needs at least one person" }

        // Splits before the expense row. Without a transaction the order decides what a
        // half-applied edit looks like, and the failure worth designing for is the one the
        // guards exist for -- a missing policy, which fails on the first write every time.
        // Leaving the recorded total until last means that failure changes nothing at all.
        replaceSplits(expenseId, amounts)

        val updated = client.postgrest["expenses"].update(
            ExpenseUpdate(
                reason = reason,
                amount = totalAmount.toDouble(),
                split_type = splitType
            )
        ) {
            select()
            filter { eq("id", expenseId) }
        }.decodeList<ExpenseRow>()
        check(updated.isNotEmpty()) { UPDATE_REFUSED }
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

        // replaceItems is the step that can be refused outright, so it runs first and the
        // expense row is written last. Updating the total up front meant a refusal reported
        // "nothing was changed" while the activity list already showed a new amount whose
        // splits still added up to the old one.
        replaceItems(expenseId, items)
        replaceSplits(expenseId, amounts)

        val updated = client.postgrest["expenses"].update(
            ExpenseItemizedUpdate(
                reason = reason,
                amount = total.toDouble(),
                split_type = "itemized",
                subtotal = subtotal.toDouble(),
                tax_amount = taxAmount.toDouble()
            )
        ) {
            select()
            filter { eq("id", expenseId) }
        }.decodeList<ExpenseRow>()
        check(updated.isNotEmpty()) { UPDATE_REFUSED }
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

        // One row, same person: a plain upsert, no delete involved.
        replaceSplits(expenseId, mapOf(paidToUserId to amount))

        val updated = client.postgrest["expenses"].update(
            ExpenseAmountUpdate(amount.toDouble())
        ) {
            select()
            filter { eq("id", expenseId) }
        }.decodeList<ExpenseRow>()
        check(updated.isNotEmpty()) { UPDATE_REFUSED }
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

        insertItems(expense.id, items)
        insertSplits(expense.id, amounts)
    }
}