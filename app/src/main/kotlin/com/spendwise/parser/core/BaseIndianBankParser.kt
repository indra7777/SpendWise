package com.spendwise.parser.core

import java.math.BigDecimal

/**
 * Base class for Indian bank SMS parsers.
 *
 * Provides common extraction logic for:
 * - INR amount formats (Rs., ₹, INR)
 * - Transaction type detection (UPI markers, keywords)
 * - Balance extraction
 * - Account number extraction
 * - Reference number extraction
 * - Investment transaction detection
 */
abstract class BaseIndianBankParser : TransactionParser() {

    override fun getCurrency(): String = "INR"

    /**
     * Keywords that indicate investment transactions.
     * These are treated differently from regular expenses.
     */
    protected val investmentKeywords = setOf(
        "zerodha", "groww", "kite", "upstox", "angel",
        "mutual fund", "mf", "sip", "systematic investment",
        "nse", "bse", "demat", "shares", "stocks", "equity",
        "nifty", "sensex", "trading"
    )

    /**
     * Common amount patterns for Indian banks.
     * Handles: Rs.500, Rs 500, ₹500, INR 500, Rs.1,234.56
     */
    private val amountPatterns = listOf(
        // Rs./Rs/₹/INR followed by amount
        Regex("""(?:Rs\.?|₹|INR)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // Amount followed by Rs./₹/INR (less common)
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:Rs\.?|₹|INR)""", RegexOption.IGNORE_CASE)
    )

    override fun extractAmount(body: String): BigDecimal? {
        for (pattern in amountPatterns) {
            pattern.find(body)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = amountStr.toBigDecimalOrNull()
                // Sanity check: amount should be positive and reasonable
                if (amount != null && amount > BigDecimal.ZERO && amount < BigDecimal("10000000")) {
                    return amount
                }
            }
        }
        return null
    }

    override fun extractTransactionType(body: String): TransactionType? {
        val lower = body.lowercase()

        // 1. Investment detection (highest priority)
        if (investmentKeywords.any { lower.contains(it) }) {
            return TransactionType.INVESTMENT
        }

        // 2. UPI path markers (most reliable for UPI transactions)
        if (lower.contains("upi/cr") || lower.contains("/cr/") || lower.contains("/c//")) {
            return TransactionType.CREDIT
        }
        if (lower.contains("upi/dr") || lower.contains("/dr/") || lower.contains("/d//")) {
            return TransactionType.DEBIT
        }

        // 3. Bank transfer patterns
        if (lower.contains("neft cr") || lower.contains("imps cr") || lower.contains("rtgs cr")) {
            return TransactionType.CREDIT
        }
        if (lower.contains("neft dr") || lower.contains("imps dr") || lower.contains("rtgs dr")) {
            return TransactionType.DEBIT
        }

        // 4. Self-transfer patterns
        if (lower.contains("transfer from") && lower.contains("transfer to")) {
            return TransactionType.TRANSFER
        }

        // 5. Keyword-based detection
        return when {
            // Credit indicators
            lower.contains("credited") ||
            lower.contains("received") ||
            lower.contains("deposited") ||
            lower.contains("refund") ||
            (lower.contains("from") && !lower.contains("debited from") && !lower.contains("withdrawn from")) -> TransactionType.CREDIT

            // Debit indicators
            lower.contains("debited") ||
            lower.contains("spent") ||
            lower.contains("paid") ||
            lower.contains("withdrawn") ||
            lower.contains("purchase") ||
            lower.contains("sent to") -> TransactionType.DEBIT

            // Transfer indicators
            lower.contains("transferred") -> TransactionType.TRANSFER

            else -> null
        }
    }

    /**
     * Balance extraction patterns for Indian banks.
     * Handles: Avl Bal Rs.10,000, Available Balance: INR 10000.00
     */
    private val balancePatterns = listOf(
        Regex("""(?:avl\.?\s*bal|available\s*(?:bal(?:ance)?)?|bal(?:ance)?)[:\s]*(?:Rs\.?|₹|INR)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:Rs\.?|₹|INR)\s*([\d,]+(?:\.\d{1,2})?)\s*(?:avl|available|bal)""", RegexOption.IGNORE_CASE)
    )

    override fun extractBalance(body: String): BigDecimal? {
        for (pattern in balancePatterns) {
            pattern.find(body)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return balanceStr.toBigDecimalOrNull()
            }
        }
        return null
    }

    /**
     * Account/Card last 4 digits extraction.
     * Handles: A/c XX1234, Card ending 1234, ****1234
     */
    private val accountPatterns = listOf(
        // A/c, Acct, Account with XX or asterisks
        Regex("""(?:a/c|acct?|account)[^\d]*(?:xx+|\*+|x+)(\d{4})""", RegexOption.IGNORE_CASE),
        // Card ending/with
        Regex("""card[^\d]*(?:ending|with)?[^\d]*(\d{4})""", RegexOption.IGNORE_CASE),
        // Just XX or asterisks followed by 4 digits
        Regex("""(?:xx+|\*+)(\d{4})""", RegexOption.IGNORE_CASE)
    )

    override fun extractAccountLast4(body: String): String? {
        for (pattern in accountPatterns) {
            pattern.find(body)?.let { match ->
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Reference number extraction.
     * Handles: Ref No 123456, Txn ID ABC123, UPI Ref 123456789012
     */
    private val referencePatterns = listOf(
        Regex("""(?:upi\s*ref(?:\s*no)?|ref(?:\s*no)?|txn\s*(?:id|no)?|utr)[:\s#]*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:reference)[:\s#]*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    )

    override fun extractReference(body: String): String? {
        for (pattern in referencePatterns) {
            pattern.find(body)?.let { match ->
                val ref = match.groupValues[1]
                // Reference should be at least 6 characters
                if (ref.length >= 6) {
                    return ref
                }
            }
        }
        return null
    }

    /**
     * Card transaction detection.
     * Returns true if the transaction mentions card usage.
     */
    override fun detectIsCard(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("card") ||
               lower.contains(" dc ") ||
               lower.contains(" cc ") ||
               lower.contains("debit card") ||
               lower.contains("credit card") ||
               lower.contains("pos ") ||
               lower.contains("atm ")
    }

    /**
     * Generic merchant extraction for Indian banks.
     * Override in specific bank parsers for better accuracy.
     */
    override fun extractMerchant(body: String): String? {
        val patterns = listOf(
            // "at MERCHANT on" pattern
            Regex("""at\s+(.+?)\s+on\s""", RegexOption.IGNORE_CASE),
            // "to MERCHANT" pattern (not followed by "your")
            Regex("""(?:to|towards)\s+([A-Za-z][A-Za-z0-9\s@.\-]{2,40}?)(?:\s+(?:on|ref|upi|via|$))""", RegexOption.IGNORE_CASE),
            // "from MERCHANT" pattern (for credits)
            Regex("""from\s+([A-Za-z][A-Za-z0-9\s@.\-]{2,40}?)(?:\s+(?:on|ref|upi|via|$))""", RegexOption.IGNORE_CASE),
            // VPA pattern
            Regex("""VPA\s+([A-Za-z0-9@.\-]+)""", RegexOption.IGNORE_CASE),
            // Info pattern (used by some banks)
            Regex("""Info[:\s]+(.+?)(?:\s+(?:Avl|Ref|$))""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let { match ->
                val merchant = match.groupValues[1].trim()
                // Skip common non-merchant words
                val skipWords = setOf("you", "your", "a/c", "account", "bank", "upi")
                if (merchant.length > 2 && merchant.lowercase() !in skipWords) {
                    return cleanMerchant(merchant)
                }
            }
        }
        return null
    }
}
