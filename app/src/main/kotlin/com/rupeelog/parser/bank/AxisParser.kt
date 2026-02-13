package com.rupeelog.parser.bank

import com.rupeelog.parser.core.BaseIndianBankParser

/**
 * Parser for Axis Bank SMS notifications.
 *
 * Sample formats:
 * - "Rs.500.00 debited from A/c no. XX1234 on 29-01-2026. Info: UPI/123456/Payment to merchant"
 * - "Rs.1,000.00 credited to A/c XX1234 on 29-01-26. Info: NEFT-John Doe"
 * - "Txn of Rs.2,500.00 done on Axis Bank Credit Card XX1234 at AMAZON on 29-01-26"
 */
class AxisParser : BaseIndianBankParser() {

    override fun getBankName(): String = "Axis Bank"

    private val senderPatterns = listOf(
        "AXISBK", "AXISBANK", "AXIS",
        "AXISCC" // Credit card
    )

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return senderPatterns.any { upper.contains(it) }
    }

    override fun extractMerchant(body: String): String? {
        val patterns = listOf(
            // "Info: UPI/xxx/merchant" pattern (Axis specific)
            Regex("""Info[:\s]+UPI/\d+/(.+?)(?:\s+Avl|\s*$)""", RegexOption.IGNORE_CASE),
            // "Info: NEFT/IMPS-NAME" pattern
            Regex("""Info[:\s]+(?:NEFT|IMPS|RTGS)[-/](.+?)(?:\s+Avl|\s*$)""", RegexOption.IGNORE_CASE),
            // "at MERCHANT on" pattern
            Regex("""at\s+(.+?)\s+on\s""", RegexOption.IGNORE_CASE),
            // "to MERCHANT" pattern
            Regex("""to\s+([A-Za-z][A-Za-z0-9\s@.\-]+?)(?:\s+on|\s+ref|\s*$)""", RegexOption.IGNORE_CASE),
            // VPA pattern
            Regex("""VPA[:\s]+([A-Za-z0-9@.\-]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.length > 2) {
                    return cleanMerchant(merchant)
                }
            }
        }

        return super.extractMerchant(body)
    }

    override fun isTransactionMessage(body: String): Boolean {
        if (!super.isTransactionMessage(body)) return false

        val lower = body.lowercase()

        // Axis-specific exclusions
        val exclusions = listOf(
            "flipkart axis", "reward points", "statement",
            "emi conversion", "credit limit"
        )

        return exclusions.none { lower.contains(it) }
    }
}
