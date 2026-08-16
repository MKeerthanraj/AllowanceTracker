package com.kaysyndikayte.allowancetracker.logic

import com.kaysyndikayte.allowancetracker.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal

@Serializable
data class ParsedReceiptItem(
    val name: String,
    val quantity: Int = 1,
    val price: Double // always the line TOTAL, not per-unit
)

@Serializable
data class ParsedReceipt(
    val items: List<ParsedReceiptItem>,
    val tax: Double = 0.0,
    val total: Double = 0.0
)

@Serializable
private data class GroqMessage(val role: String, val content: String)

@Serializable
private data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.0,
    val response_format: GroqResponseFormat = GroqResponseFormat()
)

@Serializable
private data class GroqResponseFormat(val type: String = "json_object")

@Serializable
private data class GroqChoice(val message: GroqMessage)

@Serializable
private data class GroqResponse(val choices: List<GroqChoice>)

object GroqReceiptParser {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun parseReceiptText(rawOcrText: String): ParsedReceipt {
        val prompt = """
    You are given raw OCR text from a restaurant/store receipt, extracted line-by-line by
    an OCR engine. Because OCR reads top-to-bottom, a single item's name is sometimes split
    across two or more consecutive lines before its price appears (e.g. "Chicken" then
    "Tikka Masala" then "450.00" are ALL PART OF ONE ITEM: "Chicken Tikka Masala").

    Rules for merging lines into items:
    - If a line has no price/number at the end, and the next line also has no price, and
      together they read as a continuation of the same phrase, merge them into one item name.
    - An item's price is the number that appears at the end of the item's own line, or on the
      first subsequent line that contains only a number (no other item name).
    - If a line shows a quantity pattern like "2 x 150.00", "2 @ 150.00", "QTY 2  150.00", or
      "2  Coffee  300.00" where 300.00 is a total for multiple units:
        - Extract the quantity separately.
        - Report "price" as the TOTAL line cost (e.g. 300.00), not the per-unit cost.
        - Include the per-unit cost too if shown.
    - Ignore lines that are clearly not purchased items: subtotal, tax, GST, VAT, service
      charge, total, change, cash, card, payment method, table number, server name, date,
      time, receipt/order number, "thank you" messages, addresses, phone numbers.
    - If OCR garbled a price (e.g. missing decimal, stray characters), do your best to infer
      the correct numeric value from context; if truly unreadable, omit that item rather than
      guessing wildly.

    Extract:
    - Each purchased line item: name, quantity (default 1 if not shown), unit price if shown,
      and total price for that line
    - The tax amount (0 if none found)
    - The grand total (0 if not found)

    Respond ONLY with JSON in this exact shape, no other text, no markdown fences:
    {
      "items": [
        {"name": "string", "quantity": 1, "price": 12.99}
      ],
      "tax": 2.50,
      "total": 45.00
    }
    Note: "price" in each item must always be the TOTAL for that line (quantity × unit price),
    never just the per-unit price.

    Raw OCR text:
    $rawOcrText
""".trimIndent()

        val httpResponse = client.post("https://api.groq.com/openai/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer ${BuildConfig.GROQ_API_KEY}")
            contentType(ContentType.Application.Json)
            setBody(GroqRequest(messages = listOf(GroqMessage("user", prompt))))
        }

        val rawBody = httpResponse.bodyAsText()
        android.util.Log.d("GroqReceiptParser", "Status: ${httpResponse.status}, Body: $rawBody")

        if (!httpResponse.status.isSuccess()) {
            throw IllegalStateException("Groq API error (${httpResponse.status}): $rawBody")
        }

        val response = json.decodeFromString<GroqResponse>(rawBody)
        val content = response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Empty response from Groq")

        return json.decodeFromString<ParsedReceipt>(content)
    }
    }
