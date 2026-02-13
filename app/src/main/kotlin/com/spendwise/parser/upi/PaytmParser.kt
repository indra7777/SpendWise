package com.spendwise.parser.upi

import com.spendwise.parser.core.BaseIndianBankParser
import com.spendwise.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Paytm app notifications.
 *
 * Sample formats:
 * - "Paid ₹500 to Swiggy via Paytm"
 * - "Received ₹1,000 from John Doe"
 * - "₹500 debited from Paytm Wallet for payment to Merchant"
 * - "₹500 added to your Paytm Wallet"
 */
class PaytmParser : BaseIndianBankParser() {

    override fun getBankName(): String = "Paytm"

    private val packageName = "net.one97.paytm"

    override fun canHandle(sender: String): Boolean {
        return sender.contains(packageName, ignoreCase = true) ||
               sender.uppercase().contains("PAYTM") ||
               sender.uppercase().contains("ONE97")
    }

    override fun extractAmount(body: String): BigDecimal? {
        // Paytm specific patterns
        val patterns = listOf(
            // "Paid ₹500" or "Received ₹1,000"
            Regex("""(?:Paid|Received|Sent|Added)\s*(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "₹500 debited/credited"
            Regex("""(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)\s*(?:debited|credited|added)""", RegexOption.IGNORE_CASE),
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
            lower.contains("received") ||
            lower.contains("credited") ||
            lower.contains("added") ||
            lower.contains("cashback") -> TransactionType.CREDIT

            lower.contains("paid") ||
            lower.contains("sent") ||
            lower.contains("debited") -> TransactionType.DEBIT

            else -> super.extractTransactionType(body)
        }
    }

    override fun extractMerchant(body: String): String? {
        val patterns = listOf(
            // "to MERCHANT via" pattern
            Regex("""(?:to|paid\s+to|payment\s+to)\s+([A-Za-z][A-Za-z0-9\s@.\-]+?)(?:\s+via|\s+using|\s*$)""", RegexOption.IGNORE_CASE),
            // "from NAME" pattern
            Regex("""(?:from|received\s+from)\s+([A-Za-z][A-Za-z0-9\s@.\-]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.length > 1 && !merchant.lowercase().contains("paytm")) {
                    return cleanMerchant(merchant)
                }
            }
        }

        // Wallet transactions
        if (body.lowercase().contains("wallet")) {
            return "Paytm Wallet"
        }

        return null
    }

    override fun isTransactionMessage(body: String): Boolean {
        val lower = body.lowercase()

        // Paytm transaction keywords
        val transactionKeywords = listOf(
            "paid", "received", "sent", "debited", "credited",
            "added", "payment"
        )

        // Paytm exclusions
        val exclusions = listOf(
            "request", "collect", "remind", "offer",
            "cashback offer", "promo", "recharge due"
        )

        val hasTransaction = transactionKeywords.any { lower.contains(it) }
        val hasExclusion = exclusions.any { lower.contains(it) }

        return hasTransaction && !hasExclusion
    }
}
