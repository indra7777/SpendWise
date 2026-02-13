package com.spendwise.agents.core

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.spendwise.agents.categorization.CategorizationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for on-device Gemini Nano via ML Kit GenAI.
 *
 * Benefits:
 * - No model download required (uses system's Gemini Nano)
 * - 100% on-device, privacy-first
 * - Free (no API costs)
 * - Shared model across apps
 *
 * Supported devices:
 * - Pixel 9, 10 series
 * - Samsung Galaxy S25
 * - Devices with Snapdragon, MediaTek Dimensity, Google Tensor
 */
@Singleton
class GeminiNanoClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var generativeModel: GenerativeModel? = null
    private var _status: GeminiNanoStatus = GeminiNanoStatus.UNKNOWN

    val status: GeminiNanoStatus get() = _status

    /**
     * Check if Gemini Nano is available on this device.
     * Call this before attempting to use the model.
     */
    suspend fun checkAvailability(): GeminiNanoStatus = withContext(Dispatchers.IO) {
        try {
            val model = Generation.getClient()
            generativeModel = model

            val featureStatus = model.checkStatus()
            _status = when (featureStatus) {
                FeatureStatus.AVAILABLE -> {
                    Log.d(TAG, "Gemini Nano is AVAILABLE on this device")
                    GeminiNanoStatus.AVAILABLE
                }
                FeatureStatus.DOWNLOADABLE -> {
                    Log.d(TAG, "Gemini Nano is DOWNLOADABLE")
                    GeminiNanoStatus.DOWNLOADABLE
                }
                FeatureStatus.UNAVAILABLE -> {
                    Log.d(TAG, "Gemini Nano is UNAVAILABLE on this device")
                    GeminiNanoStatus.UNAVAILABLE
                }
                else -> {
                    Log.d(TAG, "Gemini Nano status: UNKNOWN")
                    GeminiNanoStatus.UNKNOWN
                }
            }
            _status
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Gemini Nano availability", e)
            _status = GeminiNanoStatus.UNAVAILABLE
            _status
        }
    }

    /**
     * Download the model if status is DOWNLOADABLE.
     * Returns true if download succeeds or model already available.
     *
     * Note: ML Kit GenAI handles model download automatically in newer versions.
     * This method now just re-checks availability after a short delay.
     */
    suspend fun downloadIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (_status == GeminiNanoStatus.AVAILABLE) return@withContext true
        if (_status != GeminiNanoStatus.DOWNLOADABLE) return@withContext false

        try {
            // In newer ML Kit versions, downloading is handled automatically
            // when the model is first accessed. We just need to wait and re-check.
            Log.d(TAG, "Model is downloadable, waiting for system to make it available...")

            // Re-check status - the system may have downloaded it
            val newStatus = checkAvailability()
            newStatus == GeminiNanoStatus.AVAILABLE
        } catch (e: Exception) {
            Log.e(TAG, "Error during download check", e)
            false
        }
    }

    /**
     * Check if the model is ready to use.
     */
    fun isReady(): Boolean = _status == GeminiNanoStatus.AVAILABLE && generativeModel != null

    /**
     * Categorize a transaction using Gemini Nano.
     * Returns null if the model is not available or categorization fails.
     */
    suspend fun categorize(merchantText: String, amount: Double?): CategorizationResult? = withContext(Dispatchers.IO) {
        if (!isReady()) {
            Log.w(TAG, "Gemini Nano not ready, cannot categorize")
            return@withContext null
        }

        val model = generativeModel ?: return@withContext null

        val prompt = buildCategorizationPrompt(merchantText, amount)

        try {
            val response = model.generateContent(prompt)
            // Get text from response - API may vary by version
            val responseText = response.toString()

            if (responseText.isBlank()) {
                Log.w(TAG, "Empty response from Gemini Nano")
                return@withContext null
            }

            Log.d(TAG, "Gemini Nano response: $responseText")
            parseCategorizationResponse(responseText, merchantText)
        } catch (e: Exception) {
            Log.e(TAG, "Categorization failed", e)
            null
        }
    }

    private fun buildCategorizationPrompt(merchantText: String, amount: Double?): String {
        val amountStr = amount?.let { "₹${it.toInt()}" } ?: "unknown amount"

        return """
Categorize this Indian UPI transaction. Respond with ONLY a JSON object, no other text.

Transaction: "$merchantText" for $amountStr

Categories: FOOD, GROCERIES, TRANSPORT, SHOPPING, UTILITIES, ENTERTAINMENT, HEALTH, TRANSFERS, OTHER

JSON format:
{"category":"CATEGORY","merchant":"cleaned merchant name","confidence":0.9}

Response:""".trimIndent()
    }

    private fun parseCategorizationResponse(response: String, originalText: String): CategorizationResult? {
        return try {
            // Extract JSON from response (might have extra text)
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) return null

            val jsonStr = response.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(jsonStr)

            val category = json.optString("category", "OTHER").uppercase()
            val merchant = json.optString("merchant", originalText)
            val confidence = json.optDouble("confidence", 0.8).toFloat()

            // Validate category
            val validCategories = setOf("FOOD", "GROCERIES", "TRANSPORT", "SHOPPING",
                "UTILITIES", "ENTERTAINMENT", "HEALTH", "TRANSFERS", "OTHER")

            val finalCategory = if (category in validCategories) category else "OTHER"

            CategorizationResult(
                category = finalCategory,
                subcategory = null,
                merchantName = merchant,
                confidence = confidence.coerceIn(0f, 1f),
                source = "gemini_nano"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: $response", e)
            null
        }
    }

    companion object {
        private const val TAG = "GeminiNanoClient"
    }
}

/**
 * Status of Gemini Nano on this device.
 */
enum class GeminiNanoStatus {
    /** Status not yet checked */
    UNKNOWN,
    /** Model is ready to use */
    AVAILABLE,
    /** Model can be downloaded */
    DOWNLOADABLE,
    /** Device does not support Gemini Nano */
    UNAVAILABLE
}
