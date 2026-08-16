package com.kaysyndikayte.allowancetracker.repository

import com.kaysyndikayte.allowancetracker.data.GroupExpenseDetail
import com.kaysyndikayte.allowancetracker.data.GroupSummary
import com.kaysyndikayte.allowancetracker.data.SplitParticipant
import com.kaysyndikayte.allowancetracker.data.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

@Serializable
private data class GroupRow(val id: String, val name: String, val invite_code: String, val icon_name: String? = null)

@Serializable
private data class GroupInsert(val name: String, val created_by: String, val icon_name: String? = null)

@Serializable
private data class IconUpdate(val icon_name: String)
@Serializable
private data class MemberInsert(val group_id: String, val user_id: String)

@Serializable
data class MemberProfile(val id: String, val display_name: String)

@Serializable
private data class BalanceRow(
    val group_id: String,
    val owes_user_id: String,
    val owed_to_user_id: String,
    val amount: Double
)

@Serializable
data class ExpenseListRow(
    val id: String, val reason: String, val category: String,
    val amount: Double, val paid_by: String, val created_at: String, val split_type: String
)

@Serializable
private data class InviteCodeRow(val invite_code: String)

@Serializable
private data class ExpenseWithSplitsRow(
    val id: String,
    val reason: String,
    val category: String,
    val amount: Double,
    val split_type: String,
    val paid_by: String,
    val created_at: String
)

@Serializable
private data class SplitWithProfileRow(
    val expense_id: String,
    val user_id: String,
    val amount_owed: Double,
    val profiles: ProfileNameRow? = null
)

@Serializable
private data class ProfileNameRow(val display_name: String)

class GroupRepository {
    private val client = SupabaseClientProvider.client
    private val userId: String get() = client.auth.currentUserOrNull()?.id
        ?: throw IllegalStateException("Not authenticated")

    suspend fun getGroupMembers(groupId: String): List<MemberProfile> {
        val rows = client.postgrest["group_members"]
            .select(Columns.raw("profiles(id, display_name)")) {
                filter { eq("group_id", groupId) }
            }
            .decodeList<kotlinx.serialization.json.JsonObject>()

        return rows.mapNotNull { row ->
            val profile = row["profiles"] as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            MemberProfile(
                id = profile["id"].toString().trim('"'),
                display_name = profile["display_name"].toString().trim('"')
            )
        }
    }

    suspend fun getGroupExpenses(groupId: String): List<ExpenseListRow> {
        return client.postgrest["expenses"]
            .select {
                filter { eq("group_id", groupId) }; order(
                "created_at",
                io.github.jan.supabase.postgrest.query.Order.DESCENDING
            )
            }
            .decodeList()
    }

    /** Net balances: positive = they owe you, negative = you owe them */
    suspend fun getNetBalances(groupId: String, myUserId: String): Map<String, java.math.BigDecimal> {
        val rows = client.postgrest["group_balances"]
            .select { filter { eq("group_id", groupId) } }
            .decodeList<BalanceRow>()

        val net = mutableMapOf<String, java.math.BigDecimal>()
        rows.forEach { row ->
            val amount = row.amount.toBigDecimal()
            when {
                row.owed_to_user_id == myUserId -> {
                    // someone owes me
                    net[row.owes_user_id] = (net[row.owes_user_id] ?: java.math.BigDecimal.ZERO).add(amount)
                }

                row.owes_user_id == myUserId -> {
                    // I owe someone
                    net[row.owed_to_user_id] =
                        (net[row.owed_to_user_id] ?: java.math.BigDecimal.ZERO).subtract(amount)
                }
            }
        }
        return net
    }

    suspend fun getMyGroups(): List<GroupSummary> {
        // Postgrest doesn't do a simple "count members" join in one call easily via kotlin client,
        // so we fetch groups the user belongs to, then count members per group.
        val memberships = client.postgrest["group_members"]
            .select(Columns.raw("group_id, groups(id, name, invite_code, icon_name)")) {
                filter { eq("user_id", userId) }
            }
            .decodeList<kotlinx.serialization.json.JsonObject>()

        return memberships.mapNotNull { row ->
            val groupObj = row["groups"]?.let { it as? kotlinx.serialization.json.JsonObject } ?: return@mapNotNull null
            val id = groupObj["id"]?.toString()?.trim('"') ?: return@mapNotNull null
            val name = groupObj["name"]?.toString()?.trim('"') ?: return@mapNotNull null
            val icon = groupObj["icon_name"]?.toString()?.trim('"')?.takeIf { it != "null" }

            val count = client.postgrest["group_members"]
                .select { filter { eq("group_id", id) } }
                .decodeList<kotlinx.serialization.json.JsonObject>()
                .size

            GroupSummary(id = id, name = name, memberCount = count, iconName = icon)
        }
    }

    suspend fun createGroup(name: String, iconName: String? = null) {
        val inserted = client.postgrest["groups"]
            .insert(GroupInsert(name = name, created_by = userId, icon_name = iconName)) {
                select()
            }
            .decodeSingle<GroupRow>()

        client.postgrest["group_members"].insert(
            MemberInsert(group_id = inserted.id, user_id = userId)
        )
    }

    suspend fun updateGroupIcon(groupId: String, iconName: String) {
        client.postgrest["groups"]
            .update(IconUpdate(icon_name = iconName)) { filter { eq("id", groupId) } }
    }

    suspend fun joinGroupByCode(inviteCode: String) {
        val group = client.postgrest["groups"]
            .select { filter { eq("invite_code", inviteCode) } }
            .decodeSingle<GroupRow>()

        client.postgrest["group_members"].insert(
            MemberInsert(group_id = group.id, user_id = userId)
        )
    }

    suspend fun getInviteCode(groupId: String): String {
        return client.postgrest["groups"]
            .select(Columns.raw("invite_code")) { filter { eq("id", groupId) } }
            .decodeSingle<InviteCodeRow>()
            .invite_code
    }

    suspend fun getGroupExpenseHistory(groupId: String): List<GroupExpenseDetail> {
        val expenses = client.postgrest["expenses"]
            .select {
                filter { eq("group_id", groupId) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList<ExpenseWithSplitsRow>()

        if (expenses.isEmpty()) return emptyList()

        val members = getGroupMembers(groupId)
        val nameById = members.associate { it.id to it.display_name }

        return expenses.map { exp ->
            val splits = client.postgrest["expense_splits"]
                .select(Columns.raw("expense_id, user_id, amount_owed, profiles(display_name)")) {
                    filter { eq("expense_id", exp.id) }
                }
                .decodeList<SplitWithProfileRow>()

            // paid_by isn't necessarily in expense_splits (payer often isn't owed anything if
            // they were part of the split too — but if they weren't in the split at all,
            // we still want their name via nameById fallback).
            val paidByName = nameById[exp.paid_by] ?: "Someone"

            GroupExpenseDetail(
                id = exp.id,
                reason = exp.reason,
                category = exp.category,
                amount = exp.amount.toBigDecimal(),
                splitType = exp.split_type,
                paidByUserId = exp.paid_by,
                paidByName = paidByName,
                createdAt = exp.created_at,
                participants = splits.map {
                    SplitParticipant(
                        userId = it.user_id,
                        displayName = it.profiles?.display_name ?: nameById[it.user_id]
                        ?: "Someone",
                        amountOwed = it.amount_owed.toBigDecimal()
                    )
                }
            )
        }
    }
}