package com.kaysyndikayte.allowancetracker.logic

import java.math.BigDecimal
import java.math.RoundingMode

data class ParticipantAmount(val userId: String, val amount: BigDecimal)

object SplitCalculator {

    /** Equal split among selected participants. Remainder cents go to the first participant
     *  (deterministic, avoids floating point drift across many splits). */
    fun equal(total: BigDecimal, participantIds: List<String>): List<ParticipantAmount> {
        require(participantIds.isNotEmpty())
        val base = total.divide(BigDecimal(participantIds.size), 2, RoundingMode.DOWN)
        val distributed = base.multiply(BigDecimal(participantIds.size))
        val remainder = total.subtract(distributed) // leftover paise/cents

        return participantIds.mapIndexed { index, id ->
            val amount = if (index == 0) base.add(remainder) else base
            ParticipantAmount(id, amount)
        }
    }

    /** Caller provides exact amounts. Validated to sum to total (within 1 paisa tolerance). */
    fun unequal(amounts: Map<String, BigDecimal>, total: BigDecimal): List<ParticipantAmount> {
        val sum = amounts.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        require((sum.subtract(total)).abs() <= BigDecimal("0.01")) {
            "Split amounts ($sum) don't add up to total ($total)"
        }
        return amounts.map { (id, amt) -> ParticipantAmount(id, amt) }
    }

    /** e.g. userA=2 shares, userB=1 share -> A pays 2/3, B pays 1/3 */
    fun byShares(shares: Map<String, Int>, total: BigDecimal): List<ParticipantAmount> {
        val totalShares = shares.values.sum()
        require(totalShares > 0)
        val results = mutableListOf<ParticipantAmount>()
        var distributed = BigDecimal.ZERO
        val entries = shares.entries.toList()

        entries.forEachIndexed { index, (id, shareCount) ->
            val amount = if (index == entries.lastIndex) {
                total.subtract(distributed) // last person absorbs rounding remainder
            } else {
                total.multiply(BigDecimal(shareCount))
                    .divide(BigDecimal(totalShares), 2, RoundingMode.DOWN)
            }
            distributed = distributed.add(amount)
            results.add(ParticipantAmount(id, amount))
        }
        return results
    }

    /** e.g. userA=60%, userB=40% */
    fun byPercentage(percentages: Map<String, BigDecimal>, total: BigDecimal): List<ParticipantAmount> {
        val sum = percentages.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        require((sum.subtract(BigDecimal(100))).abs() <= BigDecimal("0.01")) {
            "Percentages must add up to 100, got $sum"
        }
        val results = mutableListOf<ParticipantAmount>()
        var distributed = BigDecimal.ZERO
        val entries = percentages.entries.toList()

        entries.forEachIndexed { index, (id, pct) ->
            val amount = if (index == entries.lastIndex) {
                total.subtract(distributed)
            } else {
                total.multiply(pct).divide(BigDecimal(100), 2, RoundingMode.DOWN)
            }
            distributed = distributed.add(amount)
            results.add(ParticipantAmount(id, amount))
        }
        return results
    }

    /**
     * Itemized split. Each item's cost is divided equally among the people who shared that item.
     * Tax is divided among the UNION of everyone involved in ANY item — proportional to what
     * each person's pre-tax subtotal was (not equally), which matches how Splitwise does it
     * and how real receipts actually work (someone who ordered a $40 steak should pay more
     * tax-share than someone who had a $5 side).
     */
    fun itemized(
        items: List<ReceiptItem>,
        taxAmount: BigDecimal
    ): List<ParticipantAmount> {
        val perPersonSubtotal = mutableMapOf<String, BigDecimal>()

        for (item in items) {
            if (item.participantIds.isEmpty()) continue
            val share = item.price.divide(BigDecimal(item.participantIds.size), 4, RoundingMode.HALF_UP)
            for (uid in item.participantIds) {
                perPersonSubtotal[uid] = (perPersonSubtotal[uid] ?: BigDecimal.ZERO).add(share)
            }
        }

        val subtotalSum = perPersonSubtotal.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        if (subtotalSum == BigDecimal.ZERO) return emptyList()

        val results = mutableListOf<ParticipantAmount>()
        var distributedTax = BigDecimal.ZERO
        val entries = perPersonSubtotal.entries.toList()

        entries.forEachIndexed { index, (uid, subtotal) ->
            val taxShare = if (index == entries.lastIndex) {
                taxAmount.subtract(distributedTax)
            } else {
                taxAmount.multiply(subtotal).divide(subtotalSum, 2, RoundingMode.DOWN)
            }
            distributedTax = distributedTax.add(taxShare)
            results.add(ParticipantAmount(uid, subtotal.setScale(2, RoundingMode.HALF_UP).add(taxShare)))
        }
        return results
    }
}

data class ReceiptItem(
    val name: String,
    val price: BigDecimal,
    val participantIds: List<String> = emptyList()
)