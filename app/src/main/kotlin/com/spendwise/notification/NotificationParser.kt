package com.spendwise.notification

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationParser @Inject constructor() {

    // Patterns for different UPI apps
    private val phonepePattern = Regex(
        """(?:Paid|Received|Sent)\s*(?:Rs\.?|₹)\s*([\d,]+(?:\.\d{2})?)\s*(?:to|from|for)?\s*(.+?)(?:\s*(?:UPI|via|Ref))?""",
        RegexOption.IGNORE_CASE
    )

    private val amazonPayPattern = Regex(
        """(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{2})?)\s*(?:paid|sent|received)?\s*(?:to|from|for)?\s*(.+?)(?:\s*(?:Order|UPI))?""",
        RegexOption.IGNORE_CASE
    )

    private val googlePayPattern = Regex(
        """(?:You paid|Paid|Received|Sent)\s*(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{2})?)\s*(?:to|from)?\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val bankDebitPattern = Regex(
        """(?:debited|withdrawn|spent|paid)\s*(?:Rs\.?|₹|INR)\s*([\d,]+(?:\.\d{2})?)\s*(?:from|at|to)?\s*(?:A/c|Acct|Account)?\s*(?:XX)?\d*\s*(?:at|to|for)?\s*(.+?)(?:\s*(?:on|Ref|UPI))?""",
        RegexOption.IGNORE_CASE
    )

    private val amountPattern = Regex(
        """(?:Rs\.?|₹|INR)\s*([\d,]+(?:\.\d{2})?)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(notificationText: String, packageName: String): ParsedTransaction {
        return when {
            packageName.contains("phonepe") -> parsePhonePe(notificationText)
            packageName.contains("amazon") -> parseAmazonPay(notificationText)
            packageName.contains("google") -> parseGooglePay(notificationText)
            packageName.contains("sbi") || packageName.contains("bank") -> parseBankNotification(notificationText)
            else -> parseGeneric(notificationText)
        }
    }

    private fun parsePhonePe(text: String): ParsedTransaction {
        val match = phonepePattern.find(text)
        return if (match != null) {
            val amount = parseAmount(match.groupValues[1])
            val merchant = cleanMerchantName(match.groupValues[2])
            ParsedTransaction(
                isValidTransaction = true,
                amount = amount,
                merchantName = merchant,
                merchantRaw = match.groupValues[2],
                currency = "INR",
                transactionType = determineTransactionType(text)
            )
        } else {
            parseGeneric(text)
        }
    }

    private fun parseAmazonPay(text: String): ParsedTransaction {
        val match = amazonPayPattern.find(text)
        return if (match != null) {
            val amount = parseAmount(match.groupValues[1])
            val merchant = cleanMerchantName(match.groupValues[2])
            ParsedTransaction(
                isValidTransaction = true,
                amount = amount,
                merchantName = merchant,
                merchantRaw = match.groupValues[2],
                currency = "INR",
                transactionType = determineTransactionType(text)
            )
        } else {
            parseGeneric(text)
        }
    }

    private fun parseGooglePay(text: String): ParsedTransaction {
        val match = googlePayPattern.find(text)
        return if (match != null) {
            val amount = parseAmount(match.groupValues[1])
            val merchant = cleanMerchantName(match.groupValues[2])
            ParsedTransaction(
                isValidTransaction = true,
                amount = amount,
                merchantName = merchant,
                merchantRaw = match.groupValues[2],
                currency = "INR",
                transactionType = determineTransactionType(text)
            )
        } else {
            parseGeneric(text)
        }
    }

    private fun parseBankNotification(text: String): ParsedTransaction {
        val match = bankDebitPattern.find(text)
        return if (match != null) {
            val amount = parseAmount(match.groupValues[1])
            val merchant = cleanMerchantName(match.groupValues[2])
            ParsedTransaction(
                isValidTransaction = true,
                amount = amount,
                merchantName = merchant,
                merchantRaw = match.groupValues[2],
                currency = "INR",
                transactionType = "DEBIT"
            )
        } else {
            parseGeneric(text)
        }
    }

    private fun parseGeneric(text: String): ParsedTransaction {
        // Try to extract amount from any format
        val amountMatch = amountPattern.find(text)
        val amount = amountMatch?.let { parseAmount(it.groupValues[1]) }

        // Check if this looks like a transaction
        val isTransaction = amount != null && containsTransactionKeywords(text)

        return ParsedTransaction(
            isValidTransaction = isTransaction,
            amount = amount,
            merchantName = if (isTransaction) extractMerchantGeneric(text) else null,
            merchantRaw = text,
            currency = "INR",
            transactionType = determineTransactionType(text)
        )
    }

    private fun parseAmount(amountStr: String): Double {
        return amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
    }

    private fun cleanMerchantName(rawName: String): String {
        return rawName
            .trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""[^\w\s-]"""), "")
            .split(" ")
            .take(4)  // Limit to first 4 words
            .joinToString(" ")
            .trim()
    }

    private fun extractMerchantGeneric(text: String): String? {
        // Look for patterns like "to X", "at X", "for X"
        val patterns = listOf(
            Regex("""(?:to|at|for)\s+([A-Za-z][\w\s-]{2,30})""", RegexOption.IGNORE_CASE),
            Regex("""([A-Z][A-Za-z]+(?:\s+[A-Z][A-Za-z]+){0,2})""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return cleanMerchantName(match.groupValues[1])
            }
        }

        return null
    }

    private fun containsTransactionKeywords(text: String): Boolean {
        val keywords = listOf(
            "paid", "debited", "credited", "sent", "received",
            "withdrawn", "transferred", "payment", "transaction",
            "spent", "purchase"
        )
        val lowerText = text.lowercase()
        return keywords.any { lowerText.contains(it) }
    }

    private fun determineTransactionType(text: String): String {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("received") || lowerText.contains("credited") -> "CREDIT"
            lowerText.contains("paid") || lowerText.contains("debited") ||
            lowerText.contains("sent") || lowerText.contains("spent") -> "DEBIT"
            else -> "UNKNOWN"
        }
    }
}

data class ParsedTransaction(
    val isValidTransaction: Boolean,
    val amount: Double?,
    val merchantName: String?,
    val merchantRaw: String?,
    val currency: String?,
    val transactionType: String,
    val timestamp: Long? = System.currentTimeMillis(),
    val referenceNumber: String? = null
)
