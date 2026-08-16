package com.kaysyndikayte.allowancetracker.logic

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ReceiptOcrHelper {
    suspend fun extractText(context: Context, imageUri: Uri): String =
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(context, imageUri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        continuation.resume(result.text)
                    }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
}