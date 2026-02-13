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

    private val airtelPattern = Regex(
        """(?:credited|debited)\s*with\s*(?:Rs\.?|₹|INR)\s*([\d,]+(?:\.\d{2})?)\s*\.?\s*(?:Txn\s*ID[:\s]*(\w+))?""",
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
            packageName.contains("google.android.apps.nbu") -> parseGooglePay(notificationText)
            packageName.contains("airtel") -> parseAirtel(notificationText)
            packageName.contains("sbi") || packageName.contains("bank") -> parseBankNotification(notificationText)
            else -> parseSmsOrGeneric(notificationText)
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

    private fun parseAirtel(text: String): ParsedTransaction {
        val match = airtelPattern.find(text)
        return if (match != null) {
            val amount = parseAmount(match.groupValues[1])
            val txnId = match.groupValues.getOrNull(2)
            ParsedTransaction(
                isValidTransaction = true,
                amount = amount,
                merchantName = "Airtel Payments Bank",
                merchantRaw = text,
                currency = "INR",
                transactionType = determineTransactionType(text),
                referenceNumber = txnId
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

    private fun parseSmsOrGeneric(text: String): ParsedTransaction {
        // Try Airtel pattern first (for SMS from Airtel bank)
        if (text.contains("Airtel", ignoreCase = true)) {
            val airtelResult = parseAirtel(text)
            if (airtelResult.isValidTransaction) return airtelResult
        }

        // Try bank SMS patterns
        val bankSmsPattern = Regex(
            """(?:a/c|account|A/C).*?(?:credited|debited)\s*(?:with|by)?\s*(?:Rs\.?|₹|INR)\s*([\d,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        val bankMatch = bankSmsPattern.find(text)
        if (bankMatch != null) {
            val amount = parseAmount(bankMatch.groupValues[1])
            val bankName = extractBankName(text)
            return ParsedTransaction(
                isValidTransaction = true,
                amount = amount,
                merchantName = bankName,
                merchantRaw = text,
                currency = "INR",
                transactionType = determineTransactionType(text),
                referenceNumber = extractTxnId(text)
            )
        }

        return parseGeneric(text)
    }

    private fun extractBankName(text: String): String {
        val bankPatterns = listOf(
            "Airtel Payments Bank" to Regex("airtel", RegexOption.IGNORE_CASE),
            "SBI" to Regex("\\bSBI\\b", RegexOption.IGNORE_CASE),
            "HDFC Bank" to Regex("\\bHDFC\\b", RegexOption.IGNORE_CASE),
            "ICICI Bank" to Regex("\\bICICI\\b", RegexOption.IGNORE_CASE),
            "Axis Bank" to Regex("\\bAxis\\b", RegexOption.IGNORE_CASE),
            "Kotak Bank" to Regex("\\bKotak\\b", RegexOption.IGNORE_CASE),
            "Paytm Bank" to Regex("\\bPaytm\\b", RegexOption.IGNORE_CASE),
        )
        for ((name, pattern) in bankPatterns) {
            if (pattern.containsMatchIn(text)) return name
        }
        return "Unknown Bank"
    }

    private fun extractTxnId(text: String): String? {
        val txnPattern = Regex("""(?:Txn\s*ID|Ref\s*No|Reference)[:\s]*(\w+)""", RegexOption.IGNORE_CASE)
        return txnPattern.find(text)?.groupValues?.get(1)
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
        // Skip common non-merchant words
        val skipWords = setOf("you", "your", "me", "my", "the", "a", "an", "to", "from", "for", "at", "of")

        // Pattern 1: UPI path format - UPI/X//MerchantName/BankCode
        val upiPathPattern = Regex("""UPI/[CD]//([^/]+)""", RegexOption.IGNORE_CASE)
        upiPathPattern.find(text)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.length > 1 && merchant.lowercase() !in skipWords) {
                return cleanMerchantName(merchant)
            }
        }

        // Pattern 2: "Name | sent" or "Name: ... sent" (WhatsApp/notification style)
        val senderPattern = Regex("""^([A-Za-z][A-Za-z\s@._-]{1,30})(?:\s*[:|]\s*|\s+)(?:.*?\s+)?sent""", RegexOption.IGNORE_CASE)
        senderPattern.find(text)?.let { match ->
            val sender = match.groupValues[1].trim().split(Regex("""[@:|]""")).firstOrNull()?.trim()
            if (sender != null && sender.length > 1 && sender.lowercase() !in skipWords) {
                return cleanMerchantName(sender)
            }
        }

        // Pattern 3: "Received from Name" or "from Name"
        val fromPattern = Regex("""(?:received\s+)?from\s+([A-Za-z][A-Za-z\s]{1,30})""", RegexOption.IGNORE_CASE)
        fromPattern.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.length > 1 && name.lowercase() !in skipWords) {
                return cleanMerchantName(name)
            }
        }

        // Pattern 4: "Paid to Name" or "to Name" (but not "to you")
        val toPattern = Regex("""(?:paid\s+)?to\s+([A-Za-z][A-Za-z\s]{2,30})""", RegexOption.IGNORE_CASE)
        toPattern.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.length > 1 && name.lowercase() !in skipWords) {
                return cleanMerchantName(name)
            }
        }

        // Pattern 5: Capitalized words (fallback)
        val capitalizedPattern = Regex("""([A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2})""")
        capitalizedPattern.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.length > 2 && name.lowercase() !in skipWords) {
                return cleanMerchantName(name)
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

        // Check for UPI markers first (most reliable)
        if (lowerText.contains("upi/c/") || lowerText.contains("/c//")) return "CREDIT"
        if (lowerText.contains("upi/d/") || lowerText.contains("/d//")) return "DEBIT"

        // Check for bank statement patterns
        if (lowerText.contains("tansfe fom") || lowerText.contains("transfer from") ||
            lowerText.contains("neft cr") || lowerText.contains("imps cr")) return "CREDIT"
        if (lowerText.contains("tansfe to") || lowerText.contains("transfer to") ||
            lowerText.contains("neft dr") || lowerText.contains("imps dr")) return "DEBIT"

        // "sent to you" means someone sent money TO YOU = CREDIT
        if (lowerText.contains("sent") && lowerText.contains("to you")) return "CREDIT"

        // "from" patterns usually indicate incoming money
        if (lowerText.contains("from") && !lowerText.contains("debited from")) return "CREDIT"

        // Standard keywords
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
