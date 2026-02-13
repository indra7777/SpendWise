package com.spendwise.parser.upi

import com.spendwise.parser.core.BaseIndianBankParser
import com.spendwise.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Google Pay (GPay) app notifications.
 *
 * Sample formats:
 * - "You paid ₹500 to Swiggy"
 * - "₹1,000 received from John Doe"
 * - "Payment of ₹500 to merchant@okaxis successful"
 * - "Sent ₹500 to merchant"
 */
class GooglePayParser : BaseIndianBankParser() {

    override fun getBankName(): String = "Google Pay"

    private val packageName = "com.google.android.apps.nbu.paisa.user"

    override fun canHandle(sender: String): Boolean {
        return sender.contains(packageName, ignoreCase = true) ||
               sender.contains("google", ignoreCase = true) && sender.contains("pay", ignoreCase = true) ||
               sender.uppercase().contains("GPAY")
    }

    override fun extractAmount(body: String): BigDecimal? {
        // Google Pay specific patterns
        val patterns = listOf(
            // "You paid ₹500" or "₹1,000 received"
            Regex("""(?:You\s+)?(?:paid|received|sent)\s*(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "₹500 received/paid"
            Regex("""(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)\s*(?:received|paid|sent)""", RegexOption.IGNORE_CASE),
            // Standard patterns
            Regex("""(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return amountStr.toBigDecimalOrNull()
            }
        }

        return super.extractAmount(body)
    }

    override fun extractTransactionType(body: String): TransactionType? {
        val lower = body.lowercase()

        return when {
            lower.contains("received") -> TransactionType.CREDIT
            lower.contains("you paid") || lower.contains("sent") || lower.contains("paid to") -> TransactionType.DEBIT
            else -> super.extractTransactionType(body)
        }
    }

    override fun extractMerchant(body: String): String? {
        val patterns = listOf(
            // "to MERCHANT" pattern
            Regex("""(?:to|paid\s+to)\s+([A-Za-z][A-Za-z0-9\s@.\-]+)""", RegexOption.IGNORE_CASE),
            // "from NAME" pattern
            Regex("""(?:from|received\s+from)\s+([A-Za-z][A-Za-z0-9\s@.\-]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let { match ->
                val merchant = match.groupValues[1].trim()
                // Remove "successful" or "failed" from end
                val cleaned = merchant.replace(Regex("""\s*(successful|failed)\s*$""", RegexOption.IGNORE_CASE), "")
                if (cleaned.length > 1) {
                    return cleanMerchant(cleaned)
                }
            }
        }

        return null
    }

    override fun isTransactionMessage(body: String): Boolean {
        val lower = body.lowercase()

        // Google Pay transaction keywords
        val transactionKeywords = listOf(
            "paid", "received", "sent", "payment"
        )

        // Google Pay exclusions
        val exclusions = listOf(
            "request", "collect", "remind", "offer",
            "reward", "scratch", "cashback offer"
        )

        val hasTransaction = transactionKeywords.any { lower.contains(it) }
        val hasExclusion = exclusions.any { lower.contains(it) }

        return hasTransaction && !hasExclusion
    }
}
