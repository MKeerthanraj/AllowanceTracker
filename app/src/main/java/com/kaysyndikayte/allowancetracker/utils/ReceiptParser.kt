package com.kaysyndikayte.allowancetracker.utils

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ParsedReceipt(val amount: Double?, val reason: String?, val timestampMillis: Long?)

object ReceiptParser {
    private const val TAG = "ReceiptParser"

    suspend fun parse(context: Context, imageUri: Uri): ParsedReceipt =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(context, imageUri)
                val imageWidth = image.width
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val orderedLines = sortLinesByPosition(visionText)
                        android.util.Log.d(TAG, "Position-sorted lines: ${orderedLines.map { "${it.text} (left=${it.left})" }}")
                        val result = extractFromLines(orderedLines, imageWidth)
                        android.util.Log.d(TAG, "Parsed result: amount=${result.amount}, reason=${result.reason}, ts=${result.timestampMillis}")
                        cont.resume(result)
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e(TAG, "ML Kit text recognition failed", e)
                        cont.resume(ParsedReceipt(null, null, null))
                    }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Exception while parsing receipt", e)
                cont.resume(ParsedReceipt(null, null, null))
            }
        }

    data class PositionedLine(val text: String, val top: Int, val left: Int)

    private fun sortLinesByPosition(visionText: Text): List<PositionedLine> {
        val lines = mutableListOf<PositionedLine>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox
                lines.add(PositionedLine(line.text.trim(), box?.top ?: 0, box?.left ?: 0))
            }
        }
        return lines.sortedWith(compareBy({ it.top / 20 }, { it.left }))
    }

    private fun extractFromLines(lines: List<PositionedLine>, imageWidth: Int): ParsedReceipt {
        val textLines = lines.map { it.text }
        return ParsedReceipt(
            amount = extractAmount(lines, imageWidth),
            reason = extractReason(textLines),
            timestampMillis = extractTimestamp(textLines)
        )
    }

    /** Currency amounts in this layout are always right-aligned near the screen edge.
     *  Names, VPAs, transaction IDs, and account numbers are always left-aligned.
     *  Filtering to only right-aligned lines eliminates false-positive digits
     *  picked up from names like "4 COURTS" before we even try to parse a number. */
    private fun extractAmount(lines: List<PositionedLine>, imageWidth: Int): Double? {
        val rightAlignedThreshold = imageWidth * 0.55
        val tokenRegex = Regex("""(\d[\d,]*(?:\.\d{1,2})?)""")
        val excludedHints = listOf("utr", "transaction id", "+91", "xxxx")

        val candidates = mutableListOf<Double>()

        for (line in lines) {
            if (line.left < rightAlignedThreshold) continue // skip left-aligned text entirely
            val lower = line.text.lowercase()
            if (excludedHints.any { lower.contains(it) }) continue

            val match = tokenRegex.find(line.text) ?: continue
            val raw = match.groupValues[1].replace(",", "")
            raw.toDoubleOrNull()?.let { candidates.add(it) }

            // Account for ₹ occasionally being misread as a stray leading digit
            if (raw.length > 2) {
                raw.substring(1).toDoubleOrNull()?.let { candidates.add(it) }
            }
        }

        if (candidates.isEmpty()) return null
        return candidates.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    private fun extractReason(lines: List<String>): String? {
        val index = lines.indexOfFirst { it.equals("Message", ignoreCase = true) }
        if (index != -1 && index + 1 < lines.size) {
            val candidate = lines[index + 1]
            if (!candidate.contains(":") && candidate.isNotBlank()) return candidate
        }
        val paidToIndex = lines.indexOfFirst { it.equals("Paid to", ignoreCase = true) }
        if (paidToIndex != -1 && paidToIndex + 1 < lines.size) {
            return lines[paidToIndex + 1]
        }
        return null
    }

    private fun extractTimestamp(lines: List<String>): Long? {
        val regex = Regex("""(\d{1,2}:\d{2}\s?[ap]m)\s+on\s+(\d{1,2}\s\w{3}\s\d{4})""", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = regex.find(line) ?: continue
            return try {
                val timeStr = match.groupValues[1].replace(" ", "").uppercase()
                val dateStr = match.groupValues[2]
                val formatter = java.time.format.DateTimeFormatterBuilder()
                    .appendPattern("hh:mma 'on' dd MMM yyyy")
                    .toFormatter(java.util.Locale.ENGLISH)
                val combined = "$timeStr on $dateStr"
                val dateTime = java.time.LocalDateTime.parse(combined, formatter)
                dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to parse timestamp", e)
                null
            }
        }
        return null
    }
}