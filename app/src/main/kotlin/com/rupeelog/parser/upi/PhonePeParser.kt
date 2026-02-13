package com.rupeelog.parser.upi

import com.rupeelog.parser.core.BaseIndianBankParser
import com.rupeelog.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for PhonePe app notifications.
 *
 * Sample formats:
 * - "Paid ₹500 to Swiggy"
 * - "Received ₹1,000 from John Doe"
 * - "Sent ₹500 to merchant@upi"
 * - "Payment of ₹500 to Swiggy successful"
 */
class PhonePeParser : BaseIndianBankParser() {

    override fun getBankName(): String = "PhonePe"

    private val packageName = "com.phonepe.app"

    override fun canHandle(sender: String): Boolean {
        return sender.contains(packageName, ignoreCase = true) ||
               sender.uppercase().contains("PHONEPE")
    }

    override fun extractAmount(body: String): BigDecimal? {
        // PhonePe specific patterns
        val patterns = listOf(
            // "Paid ₹500" or "Received ₹1,000"
            Regex("""(?:Paid|Received|Sent|Payment\s+of)\s*(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
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
            lower.contains("received") || lower.contains("got") -> TransactionType.CREDIT
            lower.contains("paid") || lower.contains("sent") || lower.contains("payment") -> TransactionType.DEBIT
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
                if (merchant.length > 1) {
                    return cleanMerchant(merchant)
                }
            }
        }

        return null
    }

    override fun isTransactionMessage(body: String): Boolean {
        val lower = body.lowercase()

        // PhonePe payment requests are NOT transactions
        // Real format: "Brilvoice India has requested money from you on PhonePe.Rs.34100 will be debited"
        if (lower.contains("has requested money") || lower.contains("requested money from you")) {
            return false
        }

        // PhonePe transaction keywords
        val transactionKeywords = listOf(
            "paid", "received", "sent", "payment successful",
            "debited", "credited", "transferred"
        )

        // PhonePe exclusions
        val exclusions = listOf(
            "request", "collect", "remind", "cashback offer",
            "reward", "scratch card", "will be debited from your account on approving"
        )

        val hasTransaction = transactionKeywords.any { lower.contains(it) }
        val hasExclusion = exclusions.any { lower.contains(it) }

        return hasTransaction && !hasExclusion
    }
}
