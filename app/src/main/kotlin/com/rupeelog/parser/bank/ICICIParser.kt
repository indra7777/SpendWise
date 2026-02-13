package com.rupeelog.parser.bank

import com.rupeelog.parser.core.BaseIndianBankParser
import com.rupeelog.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for ICICI Bank SMS notifications.
 *
 * Handles formats from ICICIT, ICICIO senders.
 *
 * Real SMS formats:
 * - Credit Card Spend: "INR 1,406.00 spent using ICICI Bank Card XX1008 on 24-Jan-26 on AMAZON PAY IN G. Avl Limit: INR 63,686.41"
 * - Credit Card Spend: "Rs 11,839.54 spent on ICICI Bank Card XX1008 on 23-Jan-26 at DISCOVER YOUR T. Avl Lmt: Rs 65,092.41"
 * - Payment Received: "Payment of Rs 7,000.00 has been received on your ICICI Bank Credit Card XX1008 through Bharat Bill Payment System on 22-JAN-26"
 * - USD Transaction: "USD 5.90 spent using ICICI Bank Card XX1008 on 05-Nov-25 on OPENAI"
 */
class ICICIParser : BaseIndianBankParser() {

    override fun getBankName(): String = "ICICI Bank"

    private val senderPatterns = listOf(
        "ICICIT", "ICICIO", "ICICIB", "ICICIBANK", "ICICI",
        "ICICIC" // Credit card
    )

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return senderPatterns.any { upper.contains(it) }
    }

    override fun extractAmount(body: String): BigDecimal? {
        // ICICI format: "INR 1,406.00 spent" or "Rs 11,839.54 spent"
        val spentPattern = Regex("""(?:INR|Rs\.?)\s*([\d,]+(?:\.\d{2})?)\s+spent""", RegexOption.IGNORE_CASE)
        spentPattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Payment received: "Payment of Rs 7,000.00"
        val paymentPattern = Regex("""Payment of Rs\.?\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        paymentPattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // USD transactions: "USD 5.90 spent"
        val usdPattern = Regex("""USD\s*([\d,]+(?:\.\d{2})?)\s+spent""", RegexOption.IGNORE_CASE)
        usdPattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        return super.extractAmount(body)
    }

    override fun extractTransactionType(body: String): TransactionType? {
        val lower = body.lowercase()

        return when {
            lower.contains("spent using") || lower.contains("spent on") -> TransactionType.DEBIT
            lower.contains("payment") && lower.contains("received") -> TransactionType.CREDIT
            lower.contains("standing instructions") -> null // Not a transaction, skip
            else -> super.extractTransactionType(body)
        }
    }

    override fun extractMerchant(body: String): String? {
        // Format 1: "on 24-Jan-26 on AMAZON PAY IN G. Avl"
        // Pattern: date on MERCHANT. Avl
        val onMerchantPattern = Regex("""on\s+\d{1,2}-[A-Za-z]{3}-\d{2}\s+(?:on|at)\s+([A-Za-z0-9][A-Za-z0-9\s_\-*]+?)\.?\s*(?:Avl|If not|To dispute)""", RegexOption.IGNORE_CASE)
        onMerchantPattern.find(body)?.let {
            return cleanMerchant(it.groupValues[1])
        }

        // Format 2: "at DISCOVER YOUR T. Avl"
        val atMerchantPattern = Regex("""at\s+([A-Za-z0-9][A-Za-z0-9\s_\-*]+?)\.?\s*(?:Avl|If not|To dispute)""", RegexOption.IGNORE_CASE)
        atMerchantPattern.find(body)?.let {
            return cleanMerchant(it.groupValues[1])
        }

        // Payment received: "through Bharat Bill Payment System"
        val throughPattern = Regex("""through\s+([A-Za-z][A-Za-z0-9\s]+?)\s+on\s""", RegexOption.IGNORE_CASE)
        throughPattern.find(body)?.let {
            return cleanMerchant(it.groupValues[1])
        }

        return null
    }

    override fun extractAccountLast4(body: String): String? {
        // ICICI format: "Card XX1008"
        val pattern = Regex("""Card\s*XX(\d{4})""", RegexOption.IGNORE_CASE)
        pattern.find(body)?.let {
            return it.groupValues[1]
        }

        return super.extractAccountLast4(body)
    }

    override fun extractBalance(body: String): BigDecimal? {
        // ICICI format: "Avl Limit: INR 63,686.41" or "Avl Lmt: Rs 65,092.41"
        val pattern = Regex("""Avl\s*(?:Limit|Lmt)[:\s]*(?:INR|Rs\.?)\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        pattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        return super.extractBalance(body)
    }

    override fun detectIsCard(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("card xx") || lower.contains("credit card") || lower.contains("debit card")
    }

    override fun getCurrency(): String {
        // Could be INR or USD based on message, but default to INR
        return "INR"
    }

    override fun isTransactionMessage(body: String): Boolean {
        if (!super.isTransactionMessage(body)) return false

        val lower = body.lowercase()

        // ICICI-specific exclusions
        val exclusions = listOf(
            "amazon pay later", "emi scheduled", "statement generated",
            "credit limit", "payment reminder", "standing instructions",
            "one-time password", "otp", "emi conversion",
            "statement is sent", // Statement emails
            "is due by", // Due reminder: "Total of Rs 10,066.05 or minimum of Rs 510.00 is due by"
            "pay total due", // Payment reminder: "Pay Total Due of Rs 2,338.00"
            "minimum due" // Due reminder
        )

        return exclusions.none { lower.contains(it) }
    }

}
