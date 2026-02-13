package com.rupeelog.parser.upi

import com.rupeelog.parser.core.BaseIndianBankParser
import com.rupeelog.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Amazon Pay app notifications.
 *
 * Sample formats:
 * - "₹500 paid to Swiggy using Amazon Pay"
 * - "₹1,000 received from John via Amazon Pay"
 * - "Amazon Pay: ₹500 sent to merchant@upi"
 * - "Payment of ₹500 successful. Order #123456"
 */
class AmazonPayParser : BaseIndianBankParser() {

    override fun getBankName(): String = "Amazon Pay"

    private val packageName = "in.amazon.mShop.android.shopping"

    override fun canHandle(sender: String): Boolean {
        return sender.contains(packageName, ignoreCase = true) ||
               sender.contains("amazon", ignoreCase = true) ||
               sender.uppercase().contains("AMZN")
    }

    override fun extractAmount(body: String): BigDecimal? {
        // Amazon Pay specific patterns
        val patterns = listOf(
            // "₹500 paid" or "₹1,000 received"
            Regex("""(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)\s*(?:paid|received|sent)""", RegexOption.IGNORE_CASE),
            // "Payment of ₹500"
            Regex("""Payment\s+of\s*(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
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
            lower.contains("received") || lower.contains("refund") || lower.contains("cashback") -> TransactionType.CREDIT
            lower.contains("paid") || lower.contains("sent") || lower.contains("payment") -> TransactionType.DEBIT
            else -> super.extractTransactionType(body)
        }
    }

    override fun extractMerchant(body: String): String? {
        val patterns = listOf(
            // "to MERCHANT using" pattern
            Regex("""(?:to|paid\s+to)\s+([A-Za-z][A-Za-z0-9\s@.\-]+?)(?:\s+using|\s+via|\s*$)""", RegexOption.IGNORE_CASE),
            // "from NAME" pattern
            Regex("""(?:from|received\s+from)\s+([A-Za-z][A-Za-z0-9\s@.\-]+?)(?:\s+via|\s*$)""", RegexOption.IGNORE_CASE),
            // Order pattern - if it's an Amazon order
            Regex("""Order\s*#?\s*(\d+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let { match ->
                val merchant = match.groupValues[1].trim()
                // If it's an order number, return "Amazon Order"
                if (merchant.all { it.isDigit() }) {
                    return "Amazon Order #$merchant"
                }
                if (merchant.length > 1) {
                    return cleanMerchant(merchant)
                }
            }
        }

        // Default to Amazon for Amazon-specific transactions
        if (body.lowercase().contains("amazon")) {
            return "Amazon"
        }

        return null
    }

    override fun isTransactionMessage(body: String): Boolean {
        val lower = body.lowercase()

        // Amazon Pay transaction keywords
        val transactionKeywords = listOf(
            "paid", "received", "sent", "payment",
            "debited", "credited", "refund"
        )

        // Amazon exclusions
        val exclusions = listOf(
            "offer", "deal", "sale", "discount code",
            "deliver", "shipped", "arriving", "track"
        )

        val hasTransaction = transactionKeywords.any { lower.contains(it) }
        val hasExclusion = exclusions.any { lower.contains(it) } && !lower.contains("paid") && !lower.contains("debited")

        return hasTransaction && !hasExclusion
    }
}
