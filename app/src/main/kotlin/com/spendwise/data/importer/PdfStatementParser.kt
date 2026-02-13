package com.spendwise.data.importer

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for PDF bank statements.
 * Supports password-protected PDFs from:
 * - SBI YONO (password: DDMM@Last4MobileDigits)
 * - SBI Email Statement (password: Last5Mobile + DDMMYY)
 * - SBI Net Banking Download (password: 11-digit account number)
 * - PhonePe (password: 10-digit mobile number)
 */
@Singleton
class PdfStatementParser @Inject constructor() {

    private var isInitialized = false

    fun initialize(context: Context) {
        if (!isInitialized) {
            PDFBoxResourceLoader.init(context)
            isInitialized = true
        }
    }

    /**
     * Parse a PDF bank statement
     * @param context Android context
     * @param uri URI of the PDF file
     * @param password Optional password for protected PDFs
     * @return ImportResult with parsed transactions
     */
    fun parsePdf(context: Context, uri: Uri, password: String? = null): ImportResult {
        initialize(context)

        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(
                    success = false,
                    transactions = emptyList(),
                    errors = listOf("Could not open PDF file"),
                    format = "PDF Error"
                )

            inputStream.use { stream ->
                val document = try {
                    if (password != null) {
                        PDDocument.load(stream, password)
                    } else {
                        PDDocument.load(stream)
                    }
                } catch (e: Exception) {
                    when {
                        e.message?.contains("password", ignoreCase = true) == true ||
                        e.message?.contains("encrypted", ignoreCase = true) == true -> {
                            return ImportResult(
                                success = false,
                                transactions = emptyList(),
                                errors = listOf("PDF is password protected. Please enter the password."),
                                format = "PDF (Password Required)"
                            )
                        }
                        else -> throw e
                    }
                }

                document.use { doc ->
                    val stripper = PDFTextStripper()
                    val text = stripper.getText(doc)
                    parseExtractedText(text)
                }
            }
        } catch (e: Exception) {
            ImportResult(
                success = false,
                transactions = emptyList(),
                errors = listOf("PDF parsing error: ${e.message}"),
                format = "PDF Error"
            )
        }
    }

    /**
     * Check if PDF is password protected
     */
    fun isPdfPasswordProtected(context: Context, uri: Uri): Boolean {
        initialize(context)

        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            inputStream.use { stream ->
                try {
                    val doc = PDDocument.load(stream)
                    doc.close()
                    false
                } catch (e: Exception) {
                    e.message?.contains("password", ignoreCase = true) == true ||
                    e.message?.contains("encrypted", ignoreCase = true) == true
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseExtractedText(text: String): ImportResult {
        val lines = text.lines().filter { it.isNotBlank() }

        // Detect bank format and parse accordingly
        return when {
            isSBIPdf(lines) -> parseSBIPdf(lines)
            isPhonePePdf(lines) -> parsePhonePePdf(lines)
            isHDFCPdf(lines) -> parseHDFCPdf(lines)
            isICICIPdf(lines) -> parseICICIPdf(lines)
            isAxisPdf(lines) -> parseAxisPdf(lines)
            else -> parseGenericPdf(lines)
        }
    }

    // Bank format detection
    private fun isSBIPdf(lines: List<String>): Boolean {
        val content = lines.take(20).joinToString(" ").lowercase()
        return content.contains("state bank of india") ||
               content.contains("sbi") && (content.contains("account statement") || content.contains("transaction"))
    }

    private fun isPhonePePdf(lines: List<String>): Boolean {
        val content = lines.take(30).joinToString(" ").lowercase()
        return content.contains("phonepe") || 
               content.contains("phone pe") ||
               content.contains("transaction statement for") && content.contains("paid to")
    }

    private fun isHDFCPdf(lines: List<String>): Boolean {
        val content = lines.take(20).joinToString(" ").lowercase()
        return content.contains("hdfc bank")
    }

    private fun isICICIPdf(lines: List<String>): Boolean {
        val content = lines.take(20).joinToString(" ").lowercase()
        return content.contains("icici bank")
    }

    private fun isAxisPdf(lines: List<String>): Boolean {
        val content = lines.take(20).joinToString(" ").lowercase()
        return content.contains("axis bank")
    }

    // SBI PDF Statement Parser
    private fun parseSBIPdf(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()

        // SBI statement transaction pattern
        // Typical format: Date, Description, Cheque No, Debit, Credit, Balance
        val datePattern = Regex("""(\d{2}[/-]\d{2}[/-]\d{4}|\d{2}\s+\w{3}\s+\d{4})""")
        val amountPattern = Regex("""(\d{1,3}(?:,\d{3})*(?:\.\d{2})?)\s*(Cr|Dr)?""", RegexOption.IGNORE_CASE)

        val dateFormats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val dateMatch = datePattern.find(line)

            if (dateMatch != null) {
                try {
                    val dateStr = dateMatch.value
                    val date = parseDate(dateStr, dateFormats)

                    if (date != null) {
                        // Find description and amounts in following text
                        val combinedText = (i until minOf(i + 3, lines.size))
                            .map { lines[it] }
                            .joinToString(" ")

                        val amounts = amountPattern.findAll(combinedText).toList()
                        if (amounts.isNotEmpty()) {
                            // Usually last amount is balance, second-to-last is transaction
                            val txnAmount = if (amounts.size >= 2) amounts[amounts.size - 2] else amounts.last()
                            val amountStr = txnAmount.groupValues[1].replace(",", "")
                            val amount = amountStr.toDoubleOrNull() ?: 0.0
                            val isCredit = txnAmount.groupValues.getOrNull(2)?.equals("Cr", ignoreCase = true) == true

                            if (amount > 0) {
                                val description = combinedText
                                    .replace(datePattern, "")
                                    .replace(amountPattern, "")
                                    .trim()
                                    .take(100)
                                    .ifBlank { "SBI Transaction" }

                                transactions.add(
                                    ParsedTransaction(
                                        amount = amount,
                                        merchantName = extractMerchant(description),
                                        date = date,
                                        description = description,
                                        isCredit = isCredit
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Line ${i + 1}: ${e.message}")
                }
            }
            i++
        }

        return ImportResult(
            success = transactions.isNotEmpty(),
            transactions = transactions,
            errors = errors.take(5),
            format = "SBI PDF Statement"
        )
    }

    // PhonePe PDF Statement Parser
    // Format: Date (MMM dd, yyyy), "Paid to X" or "Received from X", Type (DEBIT/CREDIT), Amount (₹X,XXX)
    private fun parsePhonePePdf(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()
        
        android.util.Log.d("PdfParser", "Parsing PhonePe PDF with ${lines.size} lines")

        // PhonePe date format: "Jan 01, 2026" or "Dec 30, 2025"
        val datePattern = Regex("""(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{1,2},?\s+\d{4}""", RegexOption.IGNORE_CASE)
        
        // Transaction patterns
        val paidToPattern = Regex("""Paid\s+to\s+(.+?)(?:\s+DEBIT|\s+Transaction|$)""", RegexOption.IGNORE_CASE)
        val receivedFromPattern = Regex("""Received\s+from\s+(.+?)(?:\s+CREDIT|\s+Transaction|$)""", RegexOption.IGNORE_CASE)
        
        // Amount pattern: ₹1,000 or ₹83 or ₹3,000
        val amountPattern = Regex("""₹\s*([\d,]+(?:\.\d{2})?)""")
        
        // Type pattern
        val debitPattern = Regex("""\bDEBIT\b""", RegexOption.IGNORE_CASE)
        val creditPattern = Regex("""\bCREDIT\b""", RegexOption.IGNORE_CASE)

        val dateFormats = listOf(
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMM dd yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMM d yyyy", Locale.ENGLISH)
        )

        // Join all lines and split by date to get transaction blocks
        val fullText = lines.joinToString(" ")
        android.util.Log.d("PdfParser", "Full text preview: ${fullText.take(500)}")
        
        // Find all date matches and their positions
        val dateMatches = datePattern.findAll(fullText).toList()
        android.util.Log.d("PdfParser", "Found ${dateMatches.size} date matches")
        
        for (i in dateMatches.indices) {
            try {
                val dateMatch = dateMatches[i]
                val dateStr = dateMatch.value.replace(",", ", ").replace(Regex("\\s+"), " ").trim()
                
                // Get text block from this date to next date (or end)
                val startIdx = dateMatch.range.first
                val endIdx = if (i + 1 < dateMatches.size) dateMatches[i + 1].range.first else fullText.length
                val blockText = fullText.substring(startIdx, endIdx)
                
                android.util.Log.d("PdfParser", "Block $i: ${blockText.take(150)}")
                
                // Parse date
                val date = parseDate(dateStr, dateFormats)
                if (date == null) {
                    android.util.Log.d("PdfParser", "Could not parse date: $dateStr")
                    continue
                }
                
                // Determine transaction type
                val isDebit = debitPattern.containsMatchIn(blockText)
                val isCredit = creditPattern.containsMatchIn(blockText)
                
                // Skip if no clear type (might be header row)
                if (!isDebit && !isCredit) {
                    continue
                }
                
                // Extract merchant name
                var merchantName = "Unknown"
                val paidMatch = paidToPattern.find(blockText)
                val receivedMatch = receivedFromPattern.find(blockText)
                
                if (paidMatch != null) {
                    merchantName = paidMatch.groupValues[1].trim()
                } else if (receivedMatch != null) {
                    merchantName = receivedMatch.groupValues[1].trim()
                }
                
                // Clean merchant name - remove @ handle formatting issues
                merchantName = merchantName
                    .replace(Regex("""@\w+"""), "")  // Remove @handles
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                    .ifBlank { if (isDebit) "Payment" else "Received" }
                
                // Extract amount
                val amountMatch = amountPattern.find(blockText)
                if (amountMatch == null) {
                    android.util.Log.d("PdfParser", "No amount found in block")
                    continue
                }
                
                val amountStr = amountMatch.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                
                if (amount <= 0) {
                    continue
                }
                
                android.util.Log.d("PdfParser", "Transaction: $merchantName, ₹$amount, isCredit=$isCredit")
                
                transactions.add(
                    ParsedTransaction(
                        amount = amount,
                        merchantName = merchantName,
                        date = date,
                        description = "PhonePe: ${if (isCredit) "Received from" else "Paid to"} $merchantName",
                        isCredit = isCredit
                    )
                )
                
            } catch (e: Exception) {
                android.util.Log.e("PdfParser", "Error parsing transaction block $i", e)
                errors.add("Transaction $i: ${e.message}")
            }
        }

        android.util.Log.d("PdfParser", "Parsed ${transactions.size} transactions")
        
        return ImportResult(
            success = transactions.isNotEmpty(),
            transactions = transactions,
            errors = if (transactions.isEmpty()) errors + "Could not extract transactions. Please check if PDF format is supported." else errors.take(5),
            format = "PhonePe PDF Statement"
        )
    }

    // HDFC PDF Statement Parser
    private fun parseHDFCPdf(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()

        val datePattern = Regex("""(\d{2}/\d{2}/\d{2,4})""")
        val amountPattern = Regex("""(\d{1,3}(?:,\d{3})*(?:\.\d{2})?)""")

        val dateFormats = listOf(
            SimpleDateFormat("dd/MM/yy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val dateMatch = datePattern.find(line)

            if (dateMatch != null) {
                try {
                    val dateStr = dateMatch.value
                    val date = parseDate(dateStr, dateFormats)

                    if (date != null) {
                        val combinedText = (i until minOf(i + 2, lines.size))
                            .map { lines[it] }
                            .joinToString(" ")

                        val amounts = amountPattern.findAll(combinedText)
                            .map { it.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0 }
                            .filter { it > 0 }
                            .toList()

                        if (amounts.isNotEmpty()) {
                            val amount = amounts.last()
                            val isCredit = combinedText.contains("Cr", ignoreCase = true) ||
                                          combinedText.contains("credit", ignoreCase = true)

                            val description = combinedText
                                .replace(datePattern, "")
                                .replace(amountPattern, "")
                                .trim()
                                .take(100)
                                .ifBlank { "HDFC Transaction" }

                            transactions.add(
                                ParsedTransaction(
                                    amount = amount,
                                    merchantName = extractMerchant(description),
                                    date = date,
                                    description = description,
                                    isCredit = isCredit
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Line ${i + 1}: ${e.message}")
                }
            }
            i++
        }

        return ImportResult(
            success = transactions.isNotEmpty(),
            transactions = transactions,
            errors = errors.take(5),
            format = "HDFC PDF Statement"
        )
    }

    // ICICI PDF Statement Parser
    private fun parseICICIPdf(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()

        val datePattern = Regex("""(\d{2}[-/]\d{2}[-/]\d{4})""")
        val amountPattern = Regex("""(\d{1,3}(?:,\d{3})*(?:\.\d{2})?)""")

        val dateFormats = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val dateMatch = datePattern.find(line)

            if (dateMatch != null) {
                try {
                    val dateStr = dateMatch.value
                    val date = parseDate(dateStr, dateFormats)

                    if (date != null) {
                        val combinedText = (i until minOf(i + 2, lines.size))
                            .map { lines[it] }
                            .joinToString(" ")

                        val amounts = amountPattern.findAll(combinedText)
                            .map { it.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0 }
                            .filter { it > 0 }
                            .toList()

                        if (amounts.isNotEmpty()) {
                            val amount = amounts.last()
                            val isCredit = combinedText.contains("Cr", ignoreCase = true)

                            val description = combinedText
                                .replace(datePattern, "")
                                .replace(amountPattern, "")
                                .trim()
                                .take(100)
                                .ifBlank { "ICICI Transaction" }

                            transactions.add(
                                ParsedTransaction(
                                    amount = amount,
                                    merchantName = extractMerchant(description),
                                    date = date,
                                    description = description,
                                    isCredit = isCredit
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Line ${i + 1}: ${e.message}")
                }
            }
            i++
        }

        return ImportResult(
            success = transactions.isNotEmpty(),
            transactions = transactions,
            errors = errors.take(5),
            format = "ICICI PDF Statement"
        )
    }

    // Axis PDF Statement Parser
    private fun parseAxisPdf(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()

        val datePattern = Regex("""(\d{2}[-/]\d{2}[-/]\d{4})""")
        val amountPattern = Regex("""(\d{1,3}(?:,\d{3})*(?:\.\d{2})?)""")

        val dateFormats = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val dateMatch = datePattern.find(line)

            if (dateMatch != null) {
                try {
                    val dateStr = dateMatch.value
                    val date = parseDate(dateStr, dateFormats)

                    if (date != null) {
                        val combinedText = (i until minOf(i + 2, lines.size))
                            .map { lines[it] }
                            .joinToString(" ")

                        val amounts = amountPattern.findAll(combinedText)
                            .map { it.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0 }
                            .filter { it > 0 }
                            .toList()

                        if (amounts.isNotEmpty()) {
                            val amount = amounts.last()
                            val isCredit = combinedText.contains("Cr", ignoreCase = true)

                            val description = combinedText
                                .replace(datePattern, "")
                                .replace(amountPattern, "")
                                .trim()
                                .take(100)
                                .ifBlank { "Axis Transaction" }

                            transactions.add(
                                ParsedTransaction(
                                    amount = amount,
                                    merchantName = extractMerchant(description),
                                    date = date,
                                    description = description,
                                    isCredit = isCredit
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Line ${i + 1}: ${e.message}")
                }
            }
            i++
        }

        return ImportResult(
            success = transactions.isNotEmpty(),
            transactions = transactions,
            errors = errors.take(5),
            format = "Axis PDF Statement"
        )
    }

    // Generic PDF Parser (fallback)
    private fun parseGenericPdf(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()

        // Try to find any date + amount patterns
        val datePattern = Regex("""(\d{2}[-/]\d{2}[-/]\d{2,4}|\d{2}\s+\w{3}\s+\d{4})""")
        val amountPattern = Regex("""[₹Rs\s]*(\d{1,3}(?:,\d{3})*(?:\.\d{2})?)""")

        val dateFormats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val dateMatch = datePattern.find(line)

            if (dateMatch != null) {
                try {
                    val dateStr = dateMatch.value
                    val date = parseDate(dateStr, dateFormats)

                    if (date != null) {
                        val combinedText = (i until minOf(i + 2, lines.size))
                            .map { lines[it] }
                            .joinToString(" ")

                        val amounts = amountPattern.findAll(combinedText)
                            .map { it.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0 }
                            .filter { it > 1 } // Filter out small numbers that might not be amounts
                            .toList()

                        if (amounts.isNotEmpty()) {
                            val amount = amounts.first()
                            val isCredit = combinedText.contains("Cr", ignoreCase = true) ||
                                          combinedText.contains("credit", ignoreCase = true) ||
                                          combinedText.contains("received", ignoreCase = true)

                            val description = combinedText
                                .replace(datePattern, "")
                                .replace(amountPattern, "")
                                .replace(Regex("[₹Rs]"), "")
                                .trim()
                                .take(100)
                                .ifBlank { "Transaction" }

                            transactions.add(
                                ParsedTransaction(
                                    amount = amount,
                                    merchantName = extractMerchant(description),
                                    date = date,
                                    description = description,
                                    isCredit = isCredit
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Line ${i + 1}: ${e.message}")
                }
            }
            i++
        }

        if (transactions.isEmpty()) {
            errors.add("Could not extract transactions from PDF. The format may not be supported.")
        }

        return ImportResult(
            success = transactions.isNotEmpty(),
            transactions = transactions,
            errors = errors.take(5),
            format = "PDF Statement"
        )
    }

    // Helper functions
    private fun parseDate(dateStr: String, formats: List<SimpleDateFormat>): Long? {
        for (format in formats) {
            try {
                format.isLenient = false
                return format.parse(dateStr)?.time
            } catch (e: Exception) {
                // Try next format
            }
        }
        return null
    }

    private fun extractMerchant(description: String): String {
        val patterns = listOf(
            Regex("UPI[-/]([^/]+)/", RegexOption.IGNORE_CASE),
            Regex("to ([^/]+)/", RegexOption.IGNORE_CASE),
            Regex("from ([^/]+)/", RegexOption.IGNORE_CASE),
            Regex("paid to (.+)", RegexOption.IGNORE_CASE),
            Regex("received from (.+)", RegexOption.IGNORE_CASE),
            Regex("IMPS[-/]([^/]+)", RegexOption.IGNORE_CASE),
            Regex("NEFT[-/]([^/]+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                return match.groupValues[1].trim().take(50)
            }
        }

        return description
            .replace(Regex("UPI/\\d+/"), "")
            .replace(Regex("IMPS/\\d+/"), "")
            .replace(Regex("NEFT/\\d+/"), "")
            .replace(Regex("\\d{10,}"), "") // Remove long numbers (account numbers, etc.)
            .trim()
            .take(50)
            .ifBlank { "Unknown Merchant" }
    }
}
