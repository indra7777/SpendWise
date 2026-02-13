package com.spendwise.agents.categorization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.spendwise.agents.core.GeminiClient
import com.spendwise.agents.core.GeminiNanoClient
import com.spendwise.agents.core.GeminiNanoStatus
import com.spendwise.data.local.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategorizationAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleBasedCategorizer: RuleBasedCategorizer,
    private val geminiNanoClient: GeminiNanoClient,
    private val geminiClient: GeminiClient,
    private val userPreferences: UserPreferences
) {
    private var nanoStatusChecked = false

    /**
     * Initialize and check Gemini Nano availability.
     * Call this once at app startup.
     */
    suspend fun initializeOnDeviceAI() {
        if (!nanoStatusChecked) {
            Log.d(TAG, "Checking Gemini Nano availability...")
            val status = geminiNanoClient.checkAvailability()
            nanoStatusChecked = true
            Log.d(TAG, "Gemini Nano status: $status")

            // Auto-download if needed and enabled
            if (status == GeminiNanoStatus.DOWNLOADABLE && userPreferences.isLocalLLMEnabled()) {
                Log.d(TAG, "Attempting to download Gemini Nano...")
                geminiNanoClient.downloadIfNeeded()
            }
        }
    }

    /**
     * Get the current status of on-device AI.
     */
    fun getOnDeviceAIStatus(): GeminiNanoStatus = geminiNanoClient.status

    suspend fun categorize(
        merchantText: String,
        amount: Double?
    ): CategorizationResult {
        Log.d(TAG, "Categorizing: $merchantText")

        // Strategy 1: Try rule-based first (fastest, works offline)
        val ruleResult = ruleBasedCategorizer.categorize(merchantText, amount)

        if (ruleResult.confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Rule-based categorization (high confidence): ${ruleResult.category}")
            return ruleResult
        }

        // Strategy 2: Try Gemini Nano (on-device, privacy-first) if available and enabled
        if (userPreferences.isLocalLLMEnabled() && geminiNanoClient.isReady()) {
            try {
                val nanoResult = geminiNanoClient.categorize(merchantText, amount)
                if (nanoResult != null && nanoResult.confidence >= MEDIUM_CONFIDENCE_THRESHOLD) {
                    Log.d(TAG, "Gemini Nano categorization: ${nanoResult.category}")
                    return nanoResult
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Nano categorization failed", e)
            }
        }

        // Strategy 3: Try cloud API (Gemini) if online and enabled
        if (userPreferences.isCloudAIEnabled() && isOnline() && geminiClient.isConfigured()) {
            try {
                val cloudResult = geminiClient.categorize(merchantText, amount)
                if (cloudResult != null && cloudResult.confidence >= MEDIUM_CONFIDENCE_THRESHOLD) {
                    Log.d(TAG, "Cloud API categorization: ${cloudResult.category}")
                    return cloudResult
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cloud categorization failed, falling back to rules", e)
            }
        }

        // Fallback to rule-based result
        Log.d(TAG, "Using rule-based fallback: ${ruleResult.category}")
        return ruleResult
    }

    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "CategorizationAgent"
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
        private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.7f
    }
}

data class CategorizationResult(
    val category: String,
    val subcategory: String?,
    val merchantName: String?,
    val confidence: Float,
    val source: String
)
