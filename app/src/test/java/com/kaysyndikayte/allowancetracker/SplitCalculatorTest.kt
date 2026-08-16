package com.kaysyndikayte.allowancetracker

import com.kaysyndikayte.allowancetracker.logic.ParticipantAmount
import com.kaysyndikayte.allowancetracker.logic.ReceiptItem
import com.kaysyndikayte.allowancetracker.logic.SplitCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The property that matters for every split: what the participants owe must add up to the
 * bill exactly. A split that reconciles to a paisa off is money quietly created or destroyed.
 */
class SplitCalculatorTest {

    private fun sumOf(amounts: List<ParticipantAmount>): BigDecimal =
        amounts.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }

    private fun assertSums(expected: String, actual: List<ParticipantAmount>) {
        val sum = sumOf(actual)
        assertTrue(
            "expected split to add up to $expected but got $sum (parts: ${actual.map { it.amount }})",
            sum.compareTo(BigDecimal(expected)) == 0
        )
    }

    private fun item(price: String, vararg who: String) =
        ReceiptItem(name = "item", price = BigDecimal(price), participantIds = who.toList())

    // --- equal -------------------------------------------------------------

    @Test
    fun `equal split of an indivisible amount still adds up`() {
        val result = SplitCalculator.equal(BigDecimal("100.00"), listOf("a", "b", "c"))
        assertSums("100.00", result)
        assertEquals(BigDecimal("33.34"), result[0].amount) // first absorbs the leftover paisa
        assertEquals(BigDecimal("33.33"), result[1].amount)
    }

    @Test
    fun `equal split among one person gives them everything`() {
        assertSums("42.50", SplitCalculator.equal(BigDecimal("42.50"), listOf("a")))
    }

    // --- itemized ----------------------------------------------------------

    @Test
    fun `itemized split of an item across three people does not lose a paisa`() {
        // Regression: per-person subtotals were rounded independently, so 3.3333 each
        // became 3.33 each and the bill reconciled to 9.99 instead of 10.00.
        val result = SplitCalculator.itemized(
            items = listOf(item("10.00", "a", "b", "c")),
            taxAmount = BigDecimal.ZERO
        )
        assertSums("10.00", result)
    }

    @Test
    fun `itemized split of an item across six people does not invent paise`() {
        // Regression: 1.6667 each rounded up to 1.67 each, reconciling to 10.02.
        val result = SplitCalculator.itemized(
            items = listOf(item("10.00", "a", "b", "c", "d", "e", "f")),
            taxAmount = BigDecimal.ZERO
        )
        assertSums("10.00", result)
    }

    @Test
    fun `itemized split reconciles items plus tax`() {
        val result = SplitCalculator.itemized(
            items = listOf(
                item("10.00", "a", "b", "c"),
                item("7.00", "a", "b"),
                item("5.55", "c")
            ),
            taxAmount = BigDecimal("3.33")
        )
        assertSums("25.88", result) // 10.00 + 7.00 + 5.55 + 3.33
    }

    @Test
    fun `itemized split charges more tax to whoever ordered more`() {
        val result = SplitCalculator.itemized(
            items = listOf(item("40.00", "a"), item("5.00", "b")),
            taxAmount = BigDecimal("9.00")
        ).associate { it.userId to it.amount }
        assertSums("54.00", result.map { ParticipantAmount(it.key, it.value) })
        assertTrue("a ordered more so should owe more", result["a"]!! > result["b"]!!)
    }

    @Test
    fun `itemized split ignores items nobody claimed`() {
        val result = SplitCalculator.itemized(
            items = listOf(item("10.00", "a", "b"), item("99.00")),
            taxAmount = BigDecimal.ZERO
        )
        assertSums("10.00", result)
    }

    @Test
    fun `itemized split of only zero priced items returns nothing instead of dividing by zero`() {
        // Regression: the guard compared with == against BigDecimal.ZERO, which also compares
        // scale, so a 0.0000 subtotal slipped past it and threw ArithmeticException.
        val result = SplitCalculator.itemized(
            items = listOf(item("0.00", "a", "b")),
            taxAmount = BigDecimal("5.00")
        )
        assertTrue("expected no participants, got $result", result.isEmpty())
    }

    @Test
    fun `itemized split does not double charge someone listed twice on one item`() {
        val result = SplitCalculator.itemized(
            items = listOf(item("10.00", "a", "a", "b")),
            taxAmount = BigDecimal.ZERO
        )
        assertSums("10.00", result)
        assertEquals(2, result.size)
    }

    // --- shares and percentages -------------------------------------------

    @Test
    fun `share split adds up and is proportional`() {
        val result = SplitCalculator.byShares(mapOf("a" to 2, "b" to 1), BigDecimal("100.00"))
        assertSums("100.00", result)
        val byId = result.associate { it.userId to it.amount }
        assertTrue("a holds twice the shares so owes more", byId["a"]!! > byId["b"]!!)
    }

    @Test
    fun `percentage split adds up`() {
        assertSums(
            "100.00",
            SplitCalculator.byPercentage(
                mapOf("a" to BigDecimal("60"), "b" to BigDecimal("40")),
                BigDecimal("100.00")
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `percentages that do not reach a hundred are rejected`() {
        SplitCalculator.byPercentage(
            mapOf("a" to BigDecimal("60"), "b" to BigDecimal("30")),
            BigDecimal("100.00")
        )
    }

    @Test
    fun `failed splits carry a message the UI can actually show`() {
        // SplitConfigScreen surfaces e.message; a bare require() left it null and the
        // Confirm button appeared to do nothing at all.
        val noone = runCatching { SplitCalculator.equal(BigDecimal("10.00"), emptyList()) }
            .exceptionOrNull()
        assertTrue("expected a message, got ${noone?.message}", !noone?.message.isNullOrBlank())

        val noShares = runCatching { SplitCalculator.byShares(mapOf("a" to 0), BigDecimal("10.00")) }
            .exceptionOrNull()
        assertTrue("expected a message, got ${noShares?.message}", !noShares?.message.isNullOrBlank())
    }
}
