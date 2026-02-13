package com.rupeelog.parser.bank

import com.rupeelog.parser.core.BaseIndianBankParser
import com.rupeelog.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for State Bank of India (SBI) SMS messages.
 *
 * Handles formats from SBIINB, SBIUPI, SBICRD, ATMSBI, SBIPSG, CBSSBI senders.
 *
 * Real SMS formats:
 * - UPI Debit: "Dear UPI user A/C X6495 debited by 250.00 on date 25Jan26 trf to MADURI AMULYA Refno 094148183788"
 * - UPI Credit: "Dear SBI User, your A/c X6495-credited by Rs.7000 on 29Oct25 transfer from GOTTIPATI ANURADHA Ref No 556407596115"
 * - IMPS Credit: "Your a/c no. XXXXXXXX6495 is credited by Rs.197700.00 on 07-01-26 by a/c linked to mobile 9XXXXXX999-NAVI FINSERV LIMITE (IMPS Ref no 600723215965)"
 * - NEFT Credit (SBIPSG): "INR 15,000.00 credited to your A/c No XX6495 on 29/08/2025 through NEFT with UTR... by UNNANU AI TECHNOLOGIES INDIA PVT LTD, INFO:"
 * - Cash Deposit (CBSSBI): "Your A/C XXXXX896495 Credited INR 7,000.00 on 06/07/25 -Deposit of Cash at S5NM019202622 CDM"
 */
class SBIParser : BaseIndianBankParser() {

    override fun getBankName(): String = "SBI"

    private val senderPatterns = listOf(
        "SBIINB", "SBIUPI", "SBICRD", "ATMSBI", "SBIPSG",
        "SBISMS", "SBIBNK", "CBSSBI", "SBI"
    )

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return senderPatterns.any { upper.contains(it) }
    }

    override fun extractAmount(body: String): BigDecimal? {
        // SBI UPI format: "debited by 250.00" or "debited by 50000.00" (no Rs prefix)
        val upiDebitPattern = Regex("""debited by (\d+(?:\.\d{1,2})?)(?:\s|$)""", RegexOption.IGNORE_CASE)
        upiDebitPattern.find(body)?.let {
            return it.groupValues[1].toBigDecimalOrNull()
        }

        // SBI Credit format: "credited by Rs.7000" or "credited by Rs.197700.00"
        val creditPattern = Regex("""credited by Rs\.?(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        creditPattern.find(body)?.let {
            return it.groupValues[1].toBigDecimalOrNull()
        }

        // Fallback to base implementation
        return super.extractAmount(body)
    }

    override fun extractTransactionType(body: String): TransactionType? {
        val lower = body.lowercase()

        return when {
            lower.contains("debited by") -> TransactionType.DEBIT
            lower.contains("credited by") || lower.contains("is credited") -> TransactionType.CREDIT
            lower.contains("withdrawn") -> TransactionType.DEBIT
            else -> super.extractTransactionType(body)
        }
    }

    override fun extractMerchant(body: String): String? {
        // UPI Debit: "trf to MADURI AMULYA Refno" or "trf to Tacobell Forum S Refno"
        val upiDebitPattern = Regex("""trf to ([A-Za-z0-9][A-Za-z0-9\s_\-]+?)(?:\s+Refno|\s+If not)""", RegexOption.IGNORE_CASE)
        upiDebitPattern.find(body)?.let {
            return cleanMerchant(it.groupValues[1])
        }

        // UPI Credit: "transfer from GOTTIPATI ANURADHA Ref"
        val upiCreditPattern = Regex("""transfer from ([A-Za-z0-9][A-Za-z0-9\s_\-]+?)(?:\s+Ref|\s+-SBI)""", RegexOption.IGNORE_CASE)
        upiCreditPattern.find(body)?.let {
            return cleanMerchant(it.groupValues[1])
        }

        // IMPS Credit: "mobile 9XXXXXX999-NAVI FINSERV LIMITE"
        val impsPattern = Regex("""mobile [\dX]+-([A-Za-z][A-Za-z0-9\s]+?)(?:\s*\(IMPS|\s+-SBI)""", RegexOption.IGNORE_CASE)
        impsPattern.find(body)?.let {
            return cleanMerchant(it.groupValues[1])
        }

        // NEFT Credit (SBIPSG): "by UNNANU AI TECHNOLOGIES INDIA PVT LTD, INFO:"
        val neftPattern = Regex("""through NEFT.*?by\s+([A-Za-z][A-Za-z0-9\s]+?),\s*INFO:""", RegexOption.IGNORE_CASE)
        neftPattern.find(body)?.let {
            return cleanMerchant(it.groupValues[1])
        }

        // Cash Deposit (CBSSBI): "Deposit of Cash at S5NM019202622 CDM"
        val cdmPattern = Regex("""Deposit of Cash at\s*([A-Z0-9]+)\s*CDM""", RegexOption.IGNORE_CASE)
        cdmPattern.find(body)?.let {
            return "Cash Deposit CDM ${it.groupValues[1]}"
        }

        // ATM: "SBI ATM S5NM019202622 from AcX6495"
        val atmPattern = Regex("""((?:UBI|SBI|WBT|CBI|BOI|PNB|HDFC|ICICI)\s*ATM\s*[A-Z0-9]+)""", RegexOption.IGNORE_CASE)
        atmPattern.find(body)?.let {
            return it.groupValues[1].trim()
        }

        return null
    }

    override fun extractReference(body: String): String? {
        // "Refno 094148183788" or "Ref No 556407596115"
        val refPattern = Regex("""Ref(?:no|[\s]?No)[\s:]*(\d+)""", RegexOption.IGNORE_CASE)
        refPattern.find(body)?.let {
            return it.groupValues[1]
        }

        // IMPS Ref: "(IMPS Ref no 600723215965)"
        val impsRefPattern = Regex("""IMPS Ref[\s]?no[\s:]*(\d+)""", RegexOption.IGNORE_CASE)
        impsRefPattern.find(body)?.let {
            return it.groupValues[1]
        }

        return super.extractReference(body)
    }

    override fun extractAccountLast4(body: String): String? {
        // SBI UPI format: "A/C X6495" or "A/c X6495"
        val pattern = Regex("""A/[Cc]\s*X(\d{4})""")
        pattern.find(body)?.let {
            return it.groupValues[1]
        }

        // Full account: "a/c no. XXXXXXXX6495"
        val fullPattern = Regex("""a/c\s*no\.?\s*X+(\d{4})""", RegexOption.IGNORE_CASE)
        fullPattern.find(body)?.let {
            return it.groupValues[1]
        }

        return super.extractAccountLast4(body)
    }

    override fun isTransactionMessage(body: String): Boolean {
        if (!super.isTransactionMessage(body)) return false

        val lower = body.lowercase()

        // SBI-specific exclusions
        val exclusions = listOf(
            "cheque book", "passbook", "nomination",
            "kyc", "link aadhaar", "update mobile",
            "otp to login" // Login OTPs
        )

        return exclusions.none { lower.contains(it) }
    }

}
