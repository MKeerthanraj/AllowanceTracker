package com.kaysyndikayte.allowancetracker.userinterface

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Amount fields used to accept any text and hand it straight to BigDecimal(String), which
 * throws on anything that isn't a number. Filtering at the keystroke means a split can never
 * be built from "12." or "abc" in the first place.
 */
fun filterAmountInput(raw: String): String {
    val cleaned = raw.filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot == -1) return cleaned.take(9)
    val whole = cleaned.substring(0, firstDot).take(9)
    val fraction = cleaned.substring(firstDot + 1).filter { it.isDigit() }.take(2)
    return "$whole.$fraction"
}

/** Same idea for share counts, which are whole numbers only. */
fun filterWholeNumberInput(raw: String): String = raw.filter { it.isDigit() }.take(4)

/** null for blank, a lone ".", or anything else that isn't a usable amount. */
fun String.toAmountOrNull(): BigDecimal? {
    val trimmed = trim()
    if (trimmed.isEmpty() || trimmed == ".") return null
    return trimmed.toBigDecimalOrNull()
}

private val rupees: NumberFormat
    get() = NumberFormat.getCurrencyInstance(
        Locale.Builder().setLanguage("en").setRegion("IN").build()
    )

fun formatRupees(value: BigDecimal): String = rupees.format(value)

fun formatRupees(value: Double): String = rupees.format(value)
