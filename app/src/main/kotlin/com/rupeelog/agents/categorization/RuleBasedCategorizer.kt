package com.rupeelog.agents.categorization

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleBasedCategorizer @Inject constructor() {

    private val merchantRules: List<MerchantRule> = listOf(
        // Food & Dining
        MerchantRule.Pattern(Regex("(?i)swiggy"), "FOOD", "Food Delivery", 0.95f),
        MerchantRule.Pattern(Regex("(?i)zomato"), "FOOD", "Food Delivery", 0.95f),
        MerchantRule.Pattern(Regex("(?i)dominos|domino's"), "FOOD", "Fast Food", 0.95f),
        MerchantRule.Pattern(Regex("(?i)pizza\\s*hut"), "FOOD", "Fast Food", 0.95f),
        MerchantRule.Pattern(Regex("(?i)mcdonald|mcdonalds|mcd"), "FOOD", "Fast Food", 0.95f),
        MerchantRule.Pattern(Regex("(?i)kfc"), "FOOD", "Fast Food", 0.95f),
        MerchantRule.Pattern(Regex("(?i)burger\\s*king"), "FOOD", "Fast Food", 0.95f),
        MerchantRule.Pattern(Regex("(?i)starbucks|cafe\\s*coffee|ccd"), "FOOD", "Cafe", 0.95f),
        MerchantRule.Pattern(Regex("(?i)restaurant|cafe|dhaba|hotel|food"), "FOOD", null, 0.7f),

        // Groceries
        MerchantRule.Pattern(Regex("(?i)big\\s*bazaar"), "GROCERIES", "Supermarket", 0.95f),
        MerchantRule.Pattern(Regex("(?i)d-?mart|dmart"), "GROCERIES", "Supermarket", 0.95f),
        MerchantRule.Pattern(Regex("(?i)reliance\\s*(fresh|smart)"), "GROCERIES", "Supermarket", 0.95f),
        MerchantRule.Pattern(Regex("(?i)more\\s*supermarket"), "GROCERIES", "Supermarket", 0.95f),
        MerchantRule.Pattern(Regex("(?i)bigbasket|big\\s*basket"), "GROCERIES", "Online Grocery", 0.95f),
        MerchantRule.Pattern(Regex("(?i)blinkit|grofers"), "GROCERIES", "Quick Commerce", 0.95f),
        MerchantRule.Pattern(Regex("(?i)zepto"), "GROCERIES", "Quick Commerce", 0.95f),
        MerchantRule.Pattern(Regex("(?i)instamart"), "GROCERIES", "Quick Commerce", 0.95f),
        MerchantRule.Pattern(Regex("(?i)grocery|supermarket|kirana|provision"), "GROCERIES", null, 0.7f),

        // Transport
        MerchantRule.Pattern(Regex("(?i)uber"), "TRANSPORT", "Rideshare", 0.95f),
        MerchantRule.Pattern(Regex("(?i)ola"), "TRANSPORT", "Rideshare", 0.95f),
        MerchantRule.Pattern(Regex("(?i)rapido"), "TRANSPORT", "Bike Taxi", 0.95f),
        MerchantRule.Pattern(Regex("(?i)metro"), "TRANSPORT", "Metro", 0.85f),
        MerchantRule.Pattern(Regex("(?i)irctc|railway"), "TRANSPORT", "Train", 0.95f),
        MerchantRule.Pattern(Regex("(?i)petrol|fuel|hp|ioc|bpcl|shell"), "TRANSPORT", "Fuel", 0.9f),
        MerchantRule.Pattern(Regex("(?i)parking"), "TRANSPORT", "Parking", 0.85f),
        MerchantRule.Pattern(Regex("(?i)fastag|toll"), "TRANSPORT", "Toll", 0.95f),

        // Shopping
        MerchantRule.Pattern(Regex("(?i)amazon"), "SHOPPING", "Online Shopping", 0.9f),
        MerchantRule.Pattern(Regex("(?i)flipkart"), "SHOPPING", "Online Shopping", 0.95f),
        MerchantRule.Pattern(Regex("(?i)myntra"), "SHOPPING", "Fashion", 0.95f),
        MerchantRule.Pattern(Regex("(?i)ajio"), "SHOPPING", "Fashion", 0.95f),
        MerchantRule.Pattern(Regex("(?i)nykaa"), "SHOPPING", "Beauty", 0.95f),
        MerchantRule.Pattern(Regex("(?i)decathlon"), "SHOPPING", "Sports", 0.95f),
        MerchantRule.Pattern(Regex("(?i)croma|vijay\\s*sales|reliance\\s*digital"), "SHOPPING", "Electronics", 0.95f),
        MerchantRule.Pattern(Regex("(?i)mall|shop|store|mart|retail"), "SHOPPING", null, 0.6f),

        // Utilities
        MerchantRule.Pattern(Regex("(?i)electricity|bescom|tata\\s*power|adani"), "UTILITIES", "Electricity", 0.95f),
        MerchantRule.Pattern(Regex("(?i)gas|indane|bharat\\s*gas|hp\\s*gas"), "UTILITIES", "Gas", 0.9f),
        MerchantRule.Pattern(Regex("(?i)water\\s*bill|bwssb"), "UTILITIES", "Water", 0.95f),
        MerchantRule.Pattern(Regex("(?i)jio|airtel|vodafone|vi|bsnl"), "UTILITIES", "Mobile Recharge", 0.85f),
        MerchantRule.Pattern(Regex("(?i)broadband|wifi|internet"), "UTILITIES", "Internet", 0.85f),
        MerchantRule.Pattern(Regex("(?i)recharge|bill\\s*pay"), "UTILITIES", null, 0.6f),

        // Entertainment
        MerchantRule.Pattern(Regex("(?i)netflix"), "ENTERTAINMENT", "Streaming", 0.95f),
        MerchantRule.Pattern(Regex("(?i)hotstar|disney"), "ENTERTAINMENT", "Streaming", 0.95f),
        MerchantRule.Pattern(Regex("(?i)prime\\s*video|amazon\\s*prime"), "ENTERTAINMENT", "Streaming", 0.9f),
        MerchantRule.Pattern(Regex("(?i)spotify"), "ENTERTAINMENT", "Music", 0.95f),
        MerchantRule.Pattern(Regex("(?i)youtube\\s*premium"), "ENTERTAINMENT", "Streaming", 0.95f),
        MerchantRule.Pattern(Regex("(?i)bookmyshow|pvr|inox|cinema|movie"), "ENTERTAINMENT", "Movies", 0.9f),
        MerchantRule.Pattern(Regex("(?i)game|gaming|steam|playstation|xbox"), "ENTERTAINMENT", "Gaming", 0.85f),

        // Health
        MerchantRule.Pattern(Regex("(?i)apollo|pharmacy|medplus|netmeds|pharmeasy|1mg"), "HEALTH", "Pharmacy", 0.9f),
        MerchantRule.Pattern(Regex("(?i)hospital|clinic|doctor|dr\\."), "HEALTH", "Medical", 0.85f),
        MerchantRule.Pattern(Regex("(?i)gym|fitness|cult\\.?fit"), "HEALTH", "Fitness", 0.9f),
        MerchantRule.Pattern(Regex("(?i)diagnostic|lab|test"), "HEALTH", "Diagnostics", 0.85f),

        // Transfers
        MerchantRule.Pattern(Regex("(?i)transfer|sent\\s*to|paid\\s*to"), "TRANSFERS", null, 0.6f)
    )

    fun categorize(merchantText: String, amount: Double?): CategorizationResult {
        // Try each rule
        for (rule in merchantRules) {
            val match = rule.match(merchantText)
            if (match != null) {
                return CategorizationResult(
                    category = match.category,
                    subcategory = match.subcategory,
                    merchantName = extractMerchantName(merchantText, rule),
                    confidence = match.confidence,
                    source = "RULE_BASED"
                )
            }
        }

        // Use amount-based hints as last resort
        val categoryHint = getAmountBasedHint(amount)

        return CategorizationResult(
            category = categoryHint ?: "OTHER",
            subcategory = null,
            merchantName = cleanMerchantName(merchantText),
            confidence = if (categoryHint != null) 0.3f else 0.1f,
            source = "RULE_BASED"
        )
    }

    private fun extractMerchantName(text: String, rule: MerchantRule): String {
        // Try to extract a clean merchant name
        val match = rule.regex.find(text)
        return if (match != null) {
            cleanMerchantName(match.value)
        } else {
            cleanMerchantName(text)
        }
    }

    private fun cleanMerchantName(text: String): String {
        return text
            .trim()
            .replace(Regex("""[^\w\s-]"""), "")
            .replace(Regex("""\s+"""), " ")
            .split(" ")
            .take(3)
            .joinToString(" ")
            .trim()
            .replaceFirstChar { it.uppercase() }
    }

    private fun getAmountBasedHint(amount: Double?): String? {
        if (amount == null) return null

        return when {
            amount <= 100 -> "FOOD"  // Small amounts likely food/beverages
            amount in 100.0..500.0 -> null  // Too ambiguous
            amount in 500.0..2000.0 -> null  // Could be anything
            amount > 5000 -> "SHOPPING"  // Large amounts often shopping
            else -> null
        }
    }
}

sealed class MerchantRule {
    abstract val regex: Regex
    abstract fun match(text: String): MatchResult?

    data class Pattern(
        override val regex: Regex,
        val category: String,
        val subcategory: String?,
        val confidence: Float
    ) : MerchantRule() {
        override fun match(text: String): MatchResult? {
            return if (regex.containsMatchIn(text)) {
                MatchResult(category, subcategory, confidence)
            } else {
                null
            }
        }
    }

    data class MatchResult(
        val category: String,
        val subcategory: String?,
        val confidence: Float
    )
}
