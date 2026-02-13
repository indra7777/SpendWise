package com.spendwise.parser.upi

import com.spendwise.parser.core.BaseIndianBankParser
import com.spendwise.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for WhatsApp Pay notifications.
 *
 * Sample formats:
 * - "John Doe sent you ₹500"
 * - "You sent ₹500 to John Doe"
 * - "Payment of ₹500 received from John"
 * - "₹500 payment to Merchant successful"
 */
class WhatsAppPayParser : BaseIndianBankParser() {

    override fun getBankName(): String = "WhatsApp Pay"

    private val packageName = "com.whatsapp"

    override fun canHandle(sender: String): Boolean {
        return sender.contains(packageName, ignoreCase = true) ||
               sender.contains("whatsapp", ignoreCase = true)
    }

    override fun extractAmount(body: String): BigDecimal? {
        // WhatsApp Pay specific patterns
        val patterns = listOf(
            // "sent you ₹500" or "You sent ₹500"
            Regex("""(?:sent\s+you|you\s+sent|received|paid)\s*(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "₹500 payment"
            Regex("""(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)\s*payment""", RegexOption.IGNORE_CASE),
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
            // "Name sent you" = you received money
            lower.contains("sent you") || lower.contains("received") -> TransactionType.CREDIT
            // "You sent" = you paid
            lower.contains("you sent") || lower.contains("paid") || lower.contains("payment to") -> TransactionType.DEBIT
            else -> super.extractTransactionType(body)
        }
    }

    override fun extractMerchant(body: String): String? {
        val patterns = listOf(
            // "NAME sent you" pattern (sender is the merchant for credits)
            Regex("""^([A-Za-z][A-Za-z\s]+?)\s+sent\s+you""", RegexOption.IGNORE_CASE),
            // "to NAME" pattern
            Regex("""(?:to|sent\s+to)\s+([A-Za-z][A-Za-z0-9\s@.\-]+)""", RegexOption.IGNORE_CASE),
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

        // WhatsApp Pay transaction keywords
        val transactionKeywords = listOf(
            "sent you", "you sent", "payment", "received", "paid"
        )

        // WhatsApp exclusions (general chat messages)
        val exclusions = listOf(
            "request", "missed call", "voice message",
            "photo", "video", "document", "sticker"
        )

        val hasTransaction = transactionKeywords.any { lower.contains(it) }
        val hasExclusion = exclusions.any { lower.contains(it) }

        // Must also contain money indicator
        val hasMoney = lower.contains("₹") || lower.contains("rs") || lower.contains("inr")

        return hasTransaction && hasMoney && !hasExclusion
    }
}
