package com.rupeelog.parser.bank

import com.rupeelog.parser.core.BaseIndianBankParser

/**
 * Parser for HDFC Bank SMS notifications.
 *
 * Sample formats:
 * - "Rs.500.00 debited from A/c XX1234 on 29-01-26. VPA swiggy@hdfcbank. Avl bal:Rs.10,000.00"
 * - "Rs.1,000.00 credited to A/c XX1234 on 29-01-26. UPI Ref 123456789012"
 * - "Your A/c XX1234 is credited with Rs.5,000.00 on 29-01-26 by NEFT-John Doe"
 */
class HDFCParser : BaseIndianBankParser() {

    override fun getBankName(): String = "HDFC Bank"

    private val senderPatterns = listOf(
        "HDFCBK", "HDFCBANK", "HDFCB", "HDFC",
        "HDFCCC" // Credit card
    )

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return senderPatterns.any { upper.contains(it) }
    }

    override fun extractMerchant(body: String): String? {
        val patterns = listOf(
            // VPA pattern (most reliable for UPI)
            Regex("""VPA\s+([A-Za-z0-9@.\-]+)""", RegexOption.IGNORE_CASE),
            // "at MERCHANT on" pattern
            Regex("""at\s+(.+?)\s+on\s""", RegexOption.IGNORE_CASE),
            // "to MERCHANT on/ref" pattern
            Regex("""to\s+(.+?)\s+(?:on|ref|upi)""", RegexOption.IGNORE_CASE),
            // "by NEFT-NAME" pattern
            Regex("""by\s+(?:NEFT|IMPS|RTGS)[-\s]+(.+?)(?:\s+on|\s*$)""", RegexOption.IGNORE_CASE),
            // Info field
            Regex("""Info[:\s]+(.+?)(?:\s+Avl|\s+Ref|\s*$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.length > 2 && !merchant.lowercase().startsWith("a/c")) {
                    return cleanMerchant(merchant)
                }
            }
        }

        return super.extractMerchant(body)
    }

    override fun isTransactionMessage(body: String): Boolean {
        if (!super.isTransactionMessage(body)) return false

        val lower = body.lowercase()

        // HDFC-specific exclusions
        val exclusions = listOf(
            "emi due", "bill payment due", "credit card bill",
            "minimum amount due", "statement ready", "e-statement"
        )

        return exclusions.none { lower.contains(it) }
    }
}
