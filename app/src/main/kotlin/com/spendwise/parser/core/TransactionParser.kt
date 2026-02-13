package com.spendwise.parser.core

import java.math.BigDecimal

/**
 * Abstract base class for transaction parsers.
 *
 * Each parser implementation handles a specific bank or payment app's
 * SMS/notification format. The parser is responsible for:
 * 1. Detecting if it can handle a given sender ID
 * 2. Extracting transaction details (amount, type, merchant, etc.)
 * 3. Filtering out non-transaction messages (OTPs, promos, etc.)
 */
abstract class TransactionParser {

    /**
     * Returns the name of the bank or payment app this parser handles.
     */
    abstract fun getBankName(): String

    /**
     * Checks if this parser can handle messages from the given sender.
     *
     * @param sender SMS sender ID (e.g., "HDFCBK", "SBIINB") or package name
     * @return true if this parser should handle the message
     */
    abstract fun canHandle(sender: String): Boolean

    /**
     * Returns the default currency for this parser.
     * Override for non-INR banks.
     */
    open fun getCurrency(): String = "INR"

    /**
     * Main parsing method. Extracts transaction details from SMS body.
     *
     * @param body The SMS or notification text
     * @param sender The sender ID or package name
     * @param timestamp When the message was received
     * @return ParsedTransaction if successful, null if not a valid transaction
     */
    fun parse(body: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Step 1: Filter non-transaction messages
        if (!isTransactionMessage(body)) return null

        // Step 2: Extract amount (required)
        val amount = extractAmount(body) ?: return null

        // Step 3: Extract transaction type (required)
        val type = extractTransactionType(body) ?: return null

        // Step 4: Build parsed transaction with optional fields
        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractMerchant(body),
            reference = extractReference(body),
            accountLast4 = extractAccountLast4(body),
            balance = extractBalance(body),
            timestamp = timestamp,
            bankName = getBankName(),
            currency = getCurrency(),
            transactionHash = ParsedTransaction.generateHash(sender, amount, body),
            isCard = detectIsCard(body),
            smsBody = body
        )
    }

    /**
     * Filters out non-transaction messages like OTPs, promotions, etc.
     * Override to add bank-specific exclusions.
     */
    protected open fun isTransactionMessage(body: String): Boolean {
        val lowerBody = body.lowercase()

        // Exclude OTPs (real patterns from SMS)
        val otpPatterns = listOf(
            "otp", "one time password", "one-time password",
            "verification code", "security code",
            "is otp to", "is your otp", "otp for", "otp is",
            "do not share", "don't share"
        )
        if (otpPatterns.any { lowerBody.contains(it) }) return false

        // Exclude promotional messages
        val promoPatterns = listOf(
            "offer", "cashback offer", "discount", "win",
            "congratulations", "lucky", "reward points"
        )
        if (promoPatterns.any { lowerBody.contains(it) && !lowerBody.contains("debited") }) return false

        // Exclude payment requests/reminders
        val requestPatterns = listOf(
            "payment request", "collect request", "pay now",
            "due date", "reminder", "bill due", "emi due",
            "has requested money", "will be debited from your account on approving",
            "upcoming mandate", "mandate set for" // Yes Bank mandate reminders
        )
        if (requestPatterns.any { lowerBody.contains(it) }) return false

        // Exclude validity/expiry messages
        val expiryPatterns = listOf(
            "expire", "valid for", "valid till", "validity"
        )
        if (expiryPatterns.any { lowerBody.contains(it) }) return false

        // Must contain at least one transaction keyword
        val transactionKeywords = listOf(
            "debited", "credited", "paid", "received", "sent",
            "withdrawn", "transferred", "spent", "purchase"
        )
        return transactionKeywords.any { lowerBody.contains(it) }
    }

    /**
     * Extracts the transaction amount from the message body.
     * Must be implemented by each parser.
     */
    protected abstract fun extractAmount(body: String): BigDecimal?

    /**
     * Determines the transaction type (DEBIT, CREDIT, etc.) from the message.
     * Must be implemented by each parser.
     */
    protected abstract fun extractTransactionType(body: String): TransactionType?

    /**
     * Extracts the merchant or recipient name.
     * Override to provide bank-specific extraction logic.
     */
    protected open fun extractMerchant(body: String): String? = null

    /**
     * Extracts the transaction reference number (UPI ref, UTR, etc.).
     * Override to provide bank-specific extraction logic.
     */
    protected open fun extractReference(body: String): String? = null

    /**
     * Extracts the last 4 digits of the account or card.
     * Override to provide bank-specific extraction logic.
     */
    protected open fun extractAccountLast4(body: String): String? = null

    /**
     * Extracts the account balance after the transaction.
     * Override to provide bank-specific extraction logic.
     */
    protected open fun extractBalance(body: String): BigDecimal? = null

    /**
     * Detects if this was a card transaction (vs UPI/NEFT/IMPS).
     * Override to provide bank-specific detection logic.
     */
    protected open fun detectIsCard(body: String): Boolean = false

    /**
     * Utility to clean merchant names.
     */
    protected fun cleanMerchant(raw: String): String {
        return raw.trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""[^\w\s@.\-]"""), "")
            .take(50)
            .trim()
    }
}
