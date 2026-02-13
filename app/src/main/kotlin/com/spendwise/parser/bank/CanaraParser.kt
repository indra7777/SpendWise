package com.spendwise.parser.bank

import com.spendwise.parser.core.BaseIndianBankParser
import com.spendwise.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Canara Bank SMS notifications.
 *
 * Handles formats from CANBNK senders.
 *
 * Real SMS formats:
 * - ATM: "A/C XXX638 linked to card XXXX0695 debited Rs INR 5,000.00 on 11/01/2026 Seq 3456 ATM txn.Avl Bal is Rs INR 517.10"
 * - NEFT Credit: "An amount of INR 10,000.00 has been credited to XXX638 on 08/01/2026 towards NEFT by Sender MAKE A DIFFERENCE, IFSC UTIB0000081"
 * - Generic Debit: "Rs. INR 5,000.00 has been DEBITED to your A/c XXX638 on 23/12/2025. Avl Bal INR 459.10"
 * - Service Charge: "An amount of INR 236.00 has been DEBITED to your account XXX638 on 14/11/2025 towards services charges"
 * - Interest Credit: "An amount of INR 25.00 has been CREDITED to your account XXX638 on 28/09/2025 towards interest"
 */
class CanaraParser : BaseIndianBankParser() {

    override fun getBankName(): String = "Canara Bank"

    private val senderPatterns = listOf(
        "CANBNK", "CANARA", "CANARABANK"
    )

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return senderPatterns.any { upper.contains(it) }
    }

    override fun extractAmount(body: String): BigDecimal? {
        // Canara ATM format: "debited Rs INR 5,000.00"
        val atmDebitPattern = Regex("""debited Rs\.?\s*INR\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        atmDebitPattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Canara format: "An amount of INR 10,000.00"
        val amountOfPattern = Regex("""amount of INR\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        amountOfPattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        // Canara generic: "Rs. INR 5,000.00 has been DEBITED"
        val rsInrPattern = Regex("""Rs\.?\s*INR\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        rsInrPattern.find(body)?.let {
            return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        return super.extractAmount(body)
    }

    override fun extractTransactionType(body: String): TransactionType? {
        val lower = body.lowercase()

        return when {
            lower.contains("has been credited") || lower.contains("credited to") -> TransactionType.CREDIT
            lower.contains("has been debited") || lower.contains("debited rs") -> TransactionType.DEBIT
            lower.contains("atm txn") -> TransactionType.DEBIT
            lower.contains("services charges") -> TransactionType.DEBIT
            lower.contains("towards interest") -> TransactionType.CREDIT
            else -> super.extractTransactionType(body)
        }
    }

    override fun extractMerchant(body: String): String? {
        // ATM transaction: "Seq 3456 ATM txn"
        val atmPattern = Regex("""Seq\s*\d+\s*(ATM\s*txn)""", RegexOption.IGNORE_CASE)
        atmPattern.find(body)?.let {
            return "ATM Withdrawal"
        }

        // NEFT Credit: "by Sender MAKE A DIFFERENCE, IFSC"
        val neftPattern = Regex("""by Sender\s+([A-Za-z][A-Za-z0-9\s]+?),\s*IFSC""", RegexOption.IGNORE_CASE)
        neftPattern.find(body)?.let {
            return cleanMerchant(it.groupValues[1])
        }

        // Service charges
        if (body.lowercase().contains("services charges")) {
            return "Bank Service Charges"
        }

        // Interest
        if (body.lowercase().contains("towards interest")) {
            return "Interest Credit"
        }

        // POS transaction: "POS txn"
        if (body.lowercase().contains("pos txn")) {
            return "POS Transaction"
        }

        return null
    }

    override fun extractReference(body: String): String? {
        // UTR: "UTR AXISP00758573005"
        val utrPattern = Regex("""UTR\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        utrPattern.find(body)?.let {
            return it.groupValues[1]
        }

        // Seq number for ATM: "Seq 3456"
        val seqPattern = Regex("""Seq\s*(\d+)""", RegexOption.IGNORE_CASE)
        seqPattern.find(body)?.let {
            return "SEQ${it.groupValues[1]}"
        }

        return super.extractReference(body)
    }

    override fun extractAccountLast4(body: String): String? {
        // Canara format: "A/C XXX638" or "XXX638" (only 3 digits shown)
        val pattern = Regex("""(?:A/[Cc]|account)\s*XXX?(\d{3,4})""", RegexOption.IGNORE_CASE)
        pattern.find(body)?.let {
            return it.groupValues[1]
        }

        // Just XXX followed by digits
        val simplePattern = Regex("""XXX(\d{3,4})""")
        simplePattern.find(body)?.let {
            return it.groupValues[1]
        }

        return super.extractAccountLast4(body)
    }

    override fun extractBalance(body: String): BigDecimal? {
        // Canara format: "Avl Bal is Rs INR 517.10" or "Avl Bal INR 459.10"
        val patterns = listOf(
            Regex("""Avl\s*Bal\s*(?:is\s*)?(?:Rs\.?\s*)?INR\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Total\s*Avail\.?\s*(?:Bal|bal)\s*INR\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(body)?.let {
                return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
            }
        }

        return super.extractBalance(body)
    }

    override fun isTransactionMessage(body: String): Boolean {
        if (!super.isTransactionMessage(body)) return false

        val lower = body.lowercase()

        // Canara-specific exclusions
        val exclusions = listOf(
            "otp", "password", "pin", "kyc"
        )

        return exclusions.none { lower.contains(it) }
    }

}
