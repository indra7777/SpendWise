package com.spendwise.parser.bank

import com.spendwise.parser.core.BaseIndianBankParser
import com.spendwise.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Kotak Mahindra Bank SMS notifications.
 *
 * Handles formats from KOTAKB senders.
 *
 * Real SMS formats:
 * - Debit Card Spend: "Rs.54024.08 spent via Kotak Debit Card XX6491 at QNT_PAY_DARBY on 21/01/2026 IST. Avl bal Rs.925.92"
 * - UPI Received: "Received Rs.54950.00 in your Kotak Bank AC X8110 from 6374677231@yescred on 20-01-26.UPI Ref:638610510039."
 * - UPI Sent: "Sent Rs.2430.00 from Kotak Bank AC X8110 to 6374677231@ybl on 16-01-26.UPI Ref 601620965799."
 * - NEFT/IMPS: "Sent Rs.25000.00 from Kotak Bank AC XXXXXX8110 to NAVYA JYOTHY MAKALA on 16-01-26.UTR:KKBKH26016796069."
 */
class KotakParser : BaseIndianBankParser() {

    override fun getBankName(): String = "Kotak Bank"

    private val senderPatterns = listOf(
        "KOTAKB", "KOTAK", "KOTAKBANK",
        "811KOT" // 811 digital bank
    )

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return senderPatterns.any { upper.contains(it) }
    }

    override fun extractAmount(body: String): BigDecimal? {
        // Kotak UPI Received: "Received Rs.54950.00"
        val receivedPattern = Regex("""Received Rs\.?([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        receivedPattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Kotak UPI/NEFT Sent: "Sent Rs.2430.00" or "Sent Rs.25000.00"
        val sentPattern = Regex("""Sent Rs\.?([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        sentPattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Kotak Debit Card: "Rs.54024.08 spent"
        val spentPattern = Regex("""Rs\.?([\d,]+(?:\.\d{2})?)\s+spent""", RegexOption.IGNORE_CASE)
        spentPattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        return super.extractAmount(body)
    }

    override fun extractTransactionType(body: String): TransactionType? {
        val lower = body.lowercase()

        return when {
            lower.contains("received rs") -> TransactionType.CREDIT
            lower.contains("sent rs") -> TransactionType.DEBIT
            lower.contains("spent via") || lower.contains("spent at") -> TransactionType.DEBIT
            lower.contains("has requested money") -> null // Payment request, not transaction
            lower.contains("refund") && lower.contains("initiated") -> TransactionType.CREDIT
            else -> super.extractTransactionType(body)
        }
    }

    override fun extractMerchant(body: String): String? {
        // UPI Received: "from 6374677231@yescred on"
        val upiReceivedPattern = Regex("""from\s+([A-Za-z0-9@._\-¡]+)\s+on\s""", RegexOption.IGNORE_CASE)
        upiReceivedPattern.find(body)?.let {
            val upiId = it.groupValues[1].trim()
            // Clean up garbled @ symbols
            return cleanUpiId(upiId)
        }

        // UPI Sent: "to 6374677231@ybl on" or "to NAVYA JYOTHY MAKALA on"
        val upiSentPattern = Regex("""Sent.*?to\s+([A-Za-z0-9@._\-¡\s]+?)\s+on\s""", RegexOption.IGNORE_CASE)
        upiSentPattern.find(body)?.let {
            val merchant = it.groupValues[1].trim()
            // If it looks like a UPI ID (contains @ or numbers followed by @)
            return if (merchant.contains("@") || merchant.contains("¡")) {
                cleanUpiId(merchant)
            } else {
                cleanMerchant(merchant)
            }
        }

        // Debit Card: "at QNT_PAY_DARBY on"
        val cardSpentPattern = Regex("""at\s+([A-Za-z0-9_\-\s]+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
        cardSpentPattern.find(body)?.let {
            return cleanMerchant(it.groupValues[1])
        }

        return null
    }

    override fun extractReference(body: String): String? {
        // UPI Ref: "UPI Ref:638610510039" or "UPI Ref 601620965799"
        val upiRefPattern = Regex("""UPI Ref[:\s]*(\d+)""", RegexOption.IGNORE_CASE)
        upiRefPattern.find(body)?.let {
            return it.groupValues[1]
        }

        // UTR: "UTR:KKBKH26016796069"
        val utrPattern = Regex("""UTR[:\s]*([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        utrPattern.find(body)?.let {
            return it.groupValues[1]
        }

        return super.extractReference(body)
    }

    override fun extractAccountLast4(body: String): String? {
        // Kotak format: "AC X8110" or "AC XXXXXX8110" or "Card XX6491"
        val patterns = listOf(
            Regex("""AC\s*X+(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""Card\s*XX(\d{4})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let {
                return it.groupValues[1]
            }
        }

        return super.extractAccountLast4(body)
    }

    override fun extractBalance(body: String): BigDecimal? {
        // Kotak format: "Avl bal Rs.925.92"
        val pattern = Regex("""Avl\s*bal\s*Rs\.?([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        pattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        return super.extractBalance(body)
    }

    override fun detectIsCard(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("debit card") || lower.contains("card xx")
    }

    override fun isTransactionMessage(body: String): Boolean {
        if (!super.isTransactionMessage(body)) return false

        val lower = body.lowercase()

        // Kotak-specific exclusions
        val exclusions = listOf(
            "privy league", "reward points", "statement ready",
            "minimum due", "otp", "pin generation", "net banking password",
            "has requested money", // Payment requests
            "welcome kit", "authentication process", "email id",
            "activated", "modified", "enabled", "blocked", "unblocked",
            "initial funding", // Initial account funding notification
            "refund initiated", // Refund processing notification (not actual credit yet)
            "refund has been initiated" // Another refund format
        )

        // Allow if it's clearly a transaction
        if (lower.contains("received rs") || lower.contains("sent rs") || lower.contains("spent")) {
            return true
        }

        return exclusions.none { lower.contains(it) }
    }

    private fun cleanUpiId(raw: String): String {
        // Replace garbled @ symbol (¡) with @
        return raw.trim()
            .replace("¡", "@")
            .take(50)
    }
}
