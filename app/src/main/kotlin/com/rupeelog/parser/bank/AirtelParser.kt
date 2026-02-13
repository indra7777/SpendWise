package com.rupeelog.parser.bank

import com.rupeelog.parser.core.BaseIndianBankParser

/**
 * Parser for Airtel Payments Bank SMS notifications.
 *
 * Sample formats:
 * - "Your Airtel Payments Bank A/c XX1234 is credited with Rs.500.00. Txn ID: ABC123"
 * - "Rs.1,000.00 debited from your Airtel Payments Bank A/c XX1234 for payment to Merchant"
 * - "Money received! Rs.500.00 added to your Airtel Payments Bank wallet"
 */
class AirtelParser : BaseIndianBankParser() {

    override fun getBankName(): String = "Airtel Payments Bank"

    private val senderPatterns = listOf(
        "AIRTEL", "APBANK", "APTBNK",
        "AIRPAY", "AIRTELPB"
    )

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return senderPatterns.any { upper.contains(it) }
    }

    override fun extractMerchant(body: String): String? {
        val patterns = listOf(
            // "payment to MERCHANT" pattern
            Regex("""payment\s+to\s+([A-Za-z][A-Za-z0-9\s@.\-]+?)(?:\s+on|\s+ref|\.\s*Txn|\s*$)""", RegexOption.IGNORE_CASE),
            // "from SENDER" pattern (for credits)
            Regex("""from\s+([A-Za-z][A-Za-z0-9\s@.\-]+?)(?:\s+on|\s+ref|\.\s*$)""", RegexOption.IGNORE_CASE),
            // "to MERCHANT" pattern
            Regex("""to\s+([A-Za-z][A-Za-z0-9\s@.\-]+?)(?:\s+on|\s+ref|\s*$)""", RegexOption.IGNORE_CASE),
            // VPA pattern
            Regex("""VPA[:\s]+([A-Za-z0-9@.\-]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let { match ->
                val merchant = match.groupValues[1].trim()
                val skipWords = setOf("your", "airtel", "payments", "bank", "a/c", "wallet")
                if (merchant.length > 2 && merchant.lowercase() !in skipWords) {
                    return cleanMerchant(merchant)
                }
            }
        }

        // Default to Airtel for wallet transactions
        if (body.lowercase().contains("wallet")) {
            return "Airtel Wallet"
        }

        return super.extractMerchant(body)
    }

    override fun extractReference(body: String): String? {
        // Airtel-specific Txn ID pattern
        val txnIdPattern = Regex("""Txn\s*ID[:\s]*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        txnIdPattern.find(body)?.let { return it.groupValues[1] }

        return super.extractReference(body)
    }

    override fun isTransactionMessage(body: String): Boolean {
        if (!super.isTransactionMessage(body)) return false

        val lower = body.lowercase()

        // Airtel-specific exclusions
        val exclusions = listOf(
            "recharge", "plan", "validity", "data pack",
            "talktime", "thanks"
        )

        // Only exclude if it's not a transaction
        return exclusions.none { lower.contains(it) } ||
               lower.contains("debited") ||
               lower.contains("credited") ||
               lower.contains("payment")
    }
}
