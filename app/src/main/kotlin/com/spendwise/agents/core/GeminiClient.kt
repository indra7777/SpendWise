package com.spendwise.agents.core

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.generationConfig
import com.spendwise.agents.categorization.CategorizationResult
import com.spendwise.data.local.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiClient @Inject constructor(
    private val userPreferences: UserPreferences
) {
    private var model: GenerativeModel? = null

    private fun getModel(): GenerativeModel? {
        if (model != null) return model

        val apiKey = userPreferences.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "Gemini API key not configured")
            return null
        }

        model = GenerativeModel(
            modelName = "gemini-pro",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.3f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 1024
            }
        )

        return model
    }

    suspend fun categorize(
        merchantText: String,
        amount: Double?
    ): CategorizationResult? = withContext(Dispatchers.IO) {
        val gemini = getModel() ?: return@withContext null

        try {
            val prompt = buildCategorizationPrompt(merchantText, amount)
            val response = gemini.generateContent(prompt)
            parseCategorizationResponse(response.text ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Gemini categorization failed", e)
            null
        }
    }

    suspend fun generateInsights(
        totalSpent: Double,
        categoryBreakdown: Map<String, Double>,
        previousPeriodTotal: Double?,
        topMerchants: List<Pair<String, Double>>
    ): List<InsightResult>? = withContext(Dispatchers.IO) {
        val gemini = getModel() ?: return@withContext null

        try {
            val prompt = buildInsightsPrompt(
                totalSpent,
                categoryBreakdown,
                previousPeriodTotal,
                topMerchants
            )
            val response = gemini.generateContent(prompt)
            parseInsightsResponse(response.text ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Gemini insights generation failed", e)
            null
        }
    }

    suspend fun generateReportNarrative(
        reportType: String,
        totalSpent: Double,
        transactionCount: Int,
        categoryBreakdown: Map<String, Double>,
        topMerchants: List<Pair<String, Double>>,
        comparisonPercent: Float?
    ): String? = withContext(Dispatchers.IO) {
        val gemini = getModel() ?: return@withContext null

        try {
            val prompt = buildReportPrompt(
                reportType,
                totalSpent,
                transactionCount,
                categoryBreakdown,
                topMerchants,
                comparisonPercent
            )
            val response = gemini.generateContent(prompt)
            response.text?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Gemini report generation failed", e)
            null
        }
    }

    private fun buildCategorizationPrompt(merchantText: String, amount: Double?): String {
        return """
            |You are a financial transaction categorizer for Indian users.
            |
            |<task>
            |Categorize this transaction into exactly one category.
            |</task>
            |
            |<transaction>
            |Merchant/Description: "$merchantText"
            |${amount?.let { "Amount: ₹$it" } ?: ""}
            |</transaction>
            |
            |<categories>
            |1. FOOD - Restaurants, food delivery (Swiggy, Zomato), cafes, fast food
            |2. GROCERIES - Supermarkets, grocery stores, BigBasket, Blinkit, DMart
            |3. TRANSPORT - Uber, Ola, Metro, Petrol, Auto, Railways
            |4. SHOPPING - Amazon, Flipkart, Myntra, clothing, electronics
            |5. UTILITIES - Electricity, water, gas, mobile recharge, internet
            |6. ENTERTAINMENT - Netflix, Hotstar, movies, games, Spotify
            |7. HEALTH - Pharmacy, hospitals, doctors, gym, fitness
            |8. TRANSFERS - Person-to-person transfers, money sent to individuals
            |9. OTHER - Anything that doesn't fit above categories
            |</categories>
            |
            |<format>
            |Respond ONLY with valid JSON:
            |{
            |  "category": "CATEGORY_NAME",
            |  "subcategory": "optional subcategory or null",
            |  "merchant_name": "cleaned merchant name",
            |  "confidence": 0.0 to 1.0,
            |  "reasoning": "brief explanation"
            |}
            |</format>
        """.trimMargin()
    }

    private fun buildInsightsPrompt(
        totalSpent: Double,
        categoryBreakdown: Map<String, Double>,
        previousPeriodTotal: Double?,
        topMerchants: List<Pair<String, Double>>
    ): String {
        val categoryText = categoryBreakdown.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { "- ${it.key}: ₹${it.value}" }

        val merchantText = topMerchants
            .take(5)
            .joinToString("\n") { "- ${it.first}: ₹${it.second}" }

        val comparison = previousPeriodTotal?.let {
            val change = ((totalSpent - it) / it * 100)
            "Compared to last period: ${if (change >= 0) "+" else ""}${change.toInt()}%"
        } ?: ""

        return """
            |You are a helpful financial advisor analyzing spending data for an Indian user.
            |
            |<spending_data>
            |Total Spent This Month: ₹$totalSpent
            |$comparison
            |
            |By Category:
            |$categoryText
            |
            |Top Merchants:
            |$merchantText
            |</spending_data>
            |
            |<task>
            |Generate 3-5 actionable insights. Each insight should:
            |1. Be specific with numbers from the data
            |2. Be encouraging, not judgmental
            |3. Suggest a concrete action when applicable
            |4. Be relevant to Indian users
            |</task>
            |
            |<format>
            |Respond ONLY with valid JSON:
            |{
            |  "insights": [
            |    {
            |      "type": "trend|warning|achievement|tip",
            |      "title": "Short title (max 50 chars)",
            |      "description": "Detailed insight with specific numbers",
            |      "action": "Suggested action or null",
            |      "priority": "high|medium|low",
            |      "category": "related category or null"
            |    }
            |  ]
            |}
            |</format>
        """.trimMargin()
    }

    private fun buildReportPrompt(
        reportType: String,
        totalSpent: Double,
        transactionCount: Int,
        categoryBreakdown: Map<String, Double>,
        topMerchants: List<Pair<String, Double>>,
        comparisonPercent: Float?
    ): String {
        val categoryText = categoryBreakdown.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString("\n") { "- ${it.key}: ₹${it.value}" }

        val merchantText = topMerchants
            .take(3)
            .joinToString("\n") { "- ${it.first}: ₹${it.second}" }

        return """
            |Generate a friendly, concise $reportType spending summary for an Indian user.
            |
            |<data>
            |Total Spent: ₹$totalSpent
            |Transactions: $transactionCount
            |${comparisonPercent?.let { "Change from previous: ${if (it >= 0) "+" else ""}${it.toInt()}%" } ?: ""}
            |
            |Top Categories:
            |$categoryText
            |
            |Top Merchants:
            |$merchantText
            |</data>
            |
            |<instructions>
            |Write a 2-3 sentence summary that:
            |1. Highlights key spending patterns
            |2. Notes anything unusual (good or concerning)
            |3. Is conversational and encouraging
            |4. Uses ₹ for currency
            |
            |Keep it brief and friendly. No bullet points, just flowing text.
            |</instructions>
        """.trimMargin()
    }

    private fun parseCategorizationResponse(text: String): CategorizationResult? {
        return try {
            val json = extractJson(text)
            val obj = JSONObject(json)

            CategorizationResult(
                category = obj.getString("category"),
                subcategory = obj.optString("subcategory").takeIf { it.isNotBlank() },
                merchantName = obj.optString("merchant_name").takeIf { it.isNotBlank() },
                confidence = obj.optDouble("confidence", 0.8).toFloat(),
                source = "CLOUD_API"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse categorization response: $text", e)
            null
        }
    }

    private fun parseInsightsResponse(text: String): List<InsightResult>? {
        return try {
            val json = extractJson(text)
            val obj = JSONObject(json)
            val insightsArray = obj.getJSONArray("insights")

            (0 until insightsArray.length()).map { i ->
                val insight = insightsArray.getJSONObject(i)
                InsightResult(
                    type = insight.getString("type"),
                    title = insight.getString("title"),
                    description = insight.getString("description"),
                    action = insight.optString("action").takeIf { it.isNotBlank() },
                    priority = insight.optString("priority", "medium"),
                    category = insight.optString("category").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse insights response: $text", e)
            null
        }
    }

    private fun extractJson(text: String): String {
        // Handle markdown code blocks
        val jsonPattern = Regex("""```json\s*([\s\S]*?)\s*```""")
        val match = jsonPattern.find(text)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Try to find JSON object directly
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1)
        }

        return text.trim()
    }

    fun isConfigured(): Boolean {
        return !userPreferences.getGeminiApiKey().isNullOrBlank()
    }

    fun clearModel() {
        model = null
    }

    companion object {
        private const val TAG = "GeminiClient"
    }
}

data class InsightResult(
    val type: String,
    val title: String,
    val description: String,
    val action: String?,
    val priority: String,
    val category: String?
)
