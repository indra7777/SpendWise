package com.rupeelog.parser.core

/**
 * Generic fallback parser for unknown banks/senders.
 *
 * This parser handles any message that wasn't matched by a specific bank parser.
 * It uses the common patterns from BaseIndianBankParser.
 */
class GenericParser : BaseIndianBankParser() {

    override fun getBankName(): String = "Unknown"

    /**
     * Generic parser accepts all senders as a fallback.
     * Should be added last in the parser list.
     */
    override fun canHandle(sender: String): Boolean = true

    override fun isTransactionMessage(body: String): Boolean {
        // For generic parser, be more strict about what we accept
        val lower = body.lowercase()

        // Must have a clear transaction indicator
        val strongIndicators = listOf(
            "debited", "credited", "paid", "received",
            "withdrawn", "transferred"
        )

        // Must have a currency indicator
        val hasCurrency = lower.contains("₹") ||
                          lower.contains("rs.") ||
                          lower.contains("rs ") ||
                          lower.contains("inr")

        // Must have a transaction keyword
        val hasIndicator = strongIndicators.any { lower.contains(it) }

        // Exclude common non-transaction patterns
        val exclusions = listOf(
            "otp", "one time", "password", "verification",
            "offer", "cashback offer", "win", "lucky",
            "expire", "valid for", "valid till"
        )
        val hasExclusion = exclusions.any { lower.contains(it) }

        return hasCurrency && hasIndicator && !hasExclusion
    }
}
