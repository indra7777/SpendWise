package com.spendwise.agents.categorization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.spendwise.agents.core.GeminiClient
import com.spendwise.data.local.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategorizationAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleBasedCategorizer: RuleBasedCategorizer,
    private val geminiClient: GeminiClient,
    private val userPreferences: UserPreferences
) {
    suspend fun categorize(
        merchantText: String,
        amount: Double?
    ): CategorizationResult {
        Log.d(TAG, "Categorizing: $merchantText")

        // Strategy 1: Try rule-based first (fastest)
        val ruleResult = ruleBasedCategorizer.categorize(merchantText, amount)

        if (ruleResult.confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Rule-based categorization (high confidence): ${ruleResult.category}")
            return ruleResult
        }

        // Strategy 2: Try local LLM (Gemma 3n) if available and enabled
        if (userPreferences.isLocalLLMEnabled()) {
            // TODO: Implement LocalLLMClient for Gemma 3n
            // val localLLMResult = localLLMClient.categorize(merchantText, amount)
            // if (localLLMResult != null && localLLMResult.confidence >= MEDIUM_CONFIDENCE_THRESHOLD) {
            //     Log.d(TAG, "Local LLM categorization: ${localLLMResult.category}")
            //     return localLLMResult
            // }
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
