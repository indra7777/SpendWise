package com.rupeelog.parser.core

import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Represents a parsed financial transaction extracted from SMS or notification.
 *
 * @property amount Transaction amount (always positive, use [type] to determine direction)
 * @property type Type of transaction (DEBIT, CREDIT, TRANSFER, INVESTMENT, UNKNOWN)
 * @property merchant Name of the merchant or recipient
 * @property reference Transaction reference number (UPI ref, UTR, etc.)
 * @property accountLast4 Last 4 digits of the account/card used
 * @property balance Account balance after transaction (if available)
 * @property timestamp Unix timestamp of when transaction was captured
 * @property bankName Name of the bank or payment app
 * @property currency Currency code (default: INR)
 * @property transactionHash Unique hash for deduplication
 * @property isCard Whether this was a card transaction (vs UPI/NEFT/IMPS)
 * @property smsBody Original SMS body (for debugging, not stored in DB)
 */
data class ParsedTransaction(
    val amount: BigDecimal,
    val type: TransactionType,
    val merchant: String?,
    val reference: String?,
    val accountLast4: String?,
    val balance: BigDecimal?,
    val timestamp: Long,
    val bankName: String,
    val currency: String = "INR",
    val transactionHash: String,
    val isCard: Boolean = false,
    val smsBody: String? = null
) {
    companion object {
        /**
         * Generates a unique hash for transaction deduplication.
         * Uses sender, amount, and first 50 chars of body to create MD5 hash.
         */
        fun generateHash(sender: String, amount: BigDecimal, body: String): String {
            val input = "$sender|${amount.toPlainString()}|${body.take(50).lowercase()}"
            return MessageDigest.getInstance("MD5")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Returns the signed amount based on transaction type.
     * DEBIT returns negative, CREDIT returns positive.
     */
    fun getSignedAmount(): BigDecimal = when (type) {
        TransactionType.DEBIT -> amount.negate()
        TransactionType.CREDIT -> amount
        TransactionType.TRANSFER -> amount.negate() // Transfers are usually outgoing
        TransactionType.INVESTMENT -> amount.negate() // Investments are usually outgoing
        TransactionType.UNKNOWN -> amount.negate() // Default to expense
    }
}
