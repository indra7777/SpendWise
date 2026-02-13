package com.rupeelog.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.rupeelog.data.local.database.TransactionEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedTransaction(
    val amount: Double,
    val merchantName: String,
    val date: Long,
    val description: String,
    val isCredit: Boolean
)

data class ImportResult(
    val success: Boolean,
    val transactions: List<ParsedTransaction>,
    val errors: List<String>,
    val format: String
)

@Singleton
class StatementParser @Inject constructor(
    private val pdfParser: PdfStatementParser
) {

    /**
     * Check if the file is a PDF
     */
    fun isPdf(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType == "application/pdf") return true

        // Check file extension
        val fileName = getFileName(context, uri)
        return fileName?.lowercase()?.endsWith(".pdf") == true
    }

    /**
     * Check if PDF is password protected
     */
    fun isPdfPasswordProtected(context: Context, uri: Uri): Boolean {
        return pdfParser.isPdfPasswordProtected(context, uri)
    }

    /**
     * Parse PDF file with optional password
     */
    fun parsePdfFile(context: Context, uri: Uri, password: String? = null): ImportResult {
        return pdfParser.parsePdf(context, uri, password)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    fun parseFile(context: Context, uri: Uri): ImportResult {
        return try {
            val content = readFileContent(context, uri)
            val lines = content.lines().filter { it.isNotBlank() }

            if (lines.isEmpty()) {
                return ImportResult(false, emptyList(), listOf("File is empty"), "unknown")
            }

            // Try to detect format and parse
            when {
                isSBIFormat(lines) -> parseSBIStatement(lines)
                isHDFCFormat(lines) -> parseHDFCStatement(lines)
                isICICIFormat(lines) -> parseICICIStatement(lines)
                isAxisFormat(lines) -> parseAxisStatement(lines)
                isPhonePeFormat(lines) -> parsePhonePeExport(lines)
                isGPayFormat(lines) -> parseGPayExport(lines)
                isGenericCSV(lines) -> parseGenericCSV(lines)
                else -> ImportResult(false, emptyList(), listOf("Unknown file format. Please use CSV with columns: Date, Description, Amount"), "unknown")
            }
        } catch (e: Exception) {
            ImportResult(false, emptyList(), listOf("Error reading file: ${e.message}"), "error")
        }
    }

    private fun readFileContent(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return reader.readText()
            }
        }
        throw IllegalStateException("Could not read file")
    }

    // Format detection
    private fun isSBIFormat(lines: List<String>): Boolean {
        val header = lines.firstOrNull()?.lowercase() ?: return false
        return header.contains("txn date") && header.contains("value date") && header.contains("description")
    }

    private fun isHDFCFormat(lines: List<String>): Boolean {
        val header = lines.firstOrNull()?.lowercase() ?: return false
        return header.contains("date") && header.contains("narration") && (header.contains("withdrawal") || header.contains("deposit"))
    }

    private fun isICICIFormat(lines: List<String>): Boolean {
        val header = lines.firstOrNull()?.lowercase() ?: return false
        return header.contains("transaction date") && header.contains("transaction remarks")
    }

    private fun isAxisFormat(lines: List<String>): Boolean {
        val header = lines.firstOrNull()?.lowercase() ?: return false
        return header.contains("tran date") && header.contains("particulars")
    }

    private fun isPhonePeFormat(lines: List<String>): Boolean {
        val header = lines.firstOrNull()?.lowercase() ?: return false
        return header.contains("date") && header.contains("recipient") && header.contains("amount")
    }

    private fun isGPayFormat(lines: List<String>): Boolean {
        val header = lines.firstOrNull()?.lowercase() ?: return false
        return header.contains("date") && (header.contains("to") || header.contains("from")) && header.contains("amount")
    }

    private fun isGenericCSV(lines: List<String>): Boolean {
        val header = lines.firstOrNull()?.lowercase() ?: return false
        return header.contains(",") && (header.contains("date") || header.contains("amount"))
    }

    // SBI Bank Statement Parser
    private fun parseSBIStatement(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()
        val dateFormats = listOf(
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        )

        // Skip header
        lines.drop(1).forEachIndexed { index, line ->
            try {
                val columns = parseCSVLine(line)
                if (columns.size >= 6) {
                    val dateStr = columns[0].trim()
                    val description = columns[2].trim()
                    val debit = columns[4].trim().replace(",", "").toDoubleOrNull() ?: 0.0
                    val credit = columns[5].trim().replace(",", "").toDoubleOrNull() ?: 0.0

                    val date = parseDate(dateStr, dateFormats)
                    if (date != null && (debit > 0 || credit > 0)) {
                        transactions.add(ParsedTransaction(
                            amount = if (credit > 0) credit else debit,
                            merchantName = extractMerchantFromDescription(description),
                            date = date,
                            description = description,
                            isCredit = credit > 0
                        ))
                    }
                }
            } catch (e: Exception) {
                errors.add("Row ${index + 2}: ${e.message}")
            }
        }

        return ImportResult(transactions.isNotEmpty(), transactions, errors, "SBI Bank Statement")
    }

    // HDFC Bank Statement Parser
    private fun parseHDFCStatement(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()
        val dateFormats = listOf(
            SimpleDateFormat("dd/MM/yy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        )

        lines.drop(1).forEachIndexed { index, line ->
            try {
                val columns = parseCSVLine(line)
                if (columns.size >= 5) {
                    val dateStr = columns[0].trim()
                    val narration = columns[1].trim()
                    val withdrawal = columns[3].trim().replace(",", "").toDoubleOrNull() ?: 0.0
                    val deposit = columns[4].trim().replace(",", "").toDoubleOrNull() ?: 0.0

                    val date = parseDate(dateStr, dateFormats)
                    if (date != null && (withdrawal > 0 || deposit > 0)) {
                        transactions.add(ParsedTransaction(
                            amount = if (deposit > 0) deposit else withdrawal,
                            merchantName = extractMerchantFromDescription(narration),
                            date = date,
                            description = narration,
                            isCredit = deposit > 0
                        ))
                    }
                }
            } catch (e: Exception) {
                errors.add("Row ${index + 2}: ${e.message}")
            }
        }

        return ImportResult(transactions.isNotEmpty(), transactions, errors, "HDFC Bank Statement")
    }

    // ICICI Bank Statement Parser
    private fun parseICICIStatement(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()
        val dateFormats = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        )

        lines.drop(1).forEachIndexed { index, line ->
            try {
                val columns = parseCSVLine(line)
                if (columns.size >= 6) {
                    val dateStr = columns[1].trim()
                    val remarks = columns[2].trim()
                    val debit = columns[4].trim().replace(",", "").toDoubleOrNull() ?: 0.0
                    val credit = columns[5].trim().replace(",", "").toDoubleOrNull() ?: 0.0

                    val date = parseDate(dateStr, dateFormats)
                    if (date != null && (debit > 0 || credit > 0)) {
                        transactions.add(ParsedTransaction(
                            amount = if (credit > 0) credit else debit,
                            merchantName = extractMerchantFromDescription(remarks),
                            date = date,
                            description = remarks,
                            isCredit = credit > 0
                        ))
                    }
                }
            } catch (e: Exception) {
                errors.add("Row ${index + 2}: ${e.message}")
            }
        }

        return ImportResult(transactions.isNotEmpty(), transactions, errors, "ICICI Bank Statement")
    }

    // Axis Bank Statement Parser
    private fun parseAxisStatement(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()
        val dateFormats = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        )

        lines.drop(1).forEachIndexed { index, line ->
            try {
                val columns = parseCSVLine(line)
                if (columns.size >= 5) {
                    val dateStr = columns[0].trim()
                    val particulars = columns[1].trim()
                    val debit = columns[2].trim().replace(",", "").toDoubleOrNull() ?: 0.0
                    val credit = columns[3].trim().replace(",", "").toDoubleOrNull() ?: 0.0

                    val date = parseDate(dateStr, dateFormats)
                    if (date != null && (debit > 0 || credit > 0)) {
                        transactions.add(ParsedTransaction(
                            amount = if (credit > 0) credit else debit,
                            merchantName = extractMerchantFromDescription(particulars),
                            date = date,
                            description = particulars,
                            isCredit = credit > 0
                        ))
                    }
                }
            } catch (e: Exception) {
                errors.add("Row ${index + 2}: ${e.message}")
            }
        }

        return ImportResult(transactions.isNotEmpty(), transactions, errors, "Axis Bank Statement")
    }

    // PhonePe Export Parser
    private fun parsePhonePeExport(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()
        val dateFormats = listOf(
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        )

        val header = lines.first().lowercase().split(",").map { it.trim() }
        val dateIdx = header.indexOfFirst { it.contains("date") }
        val recipientIdx = header.indexOfFirst { it.contains("recipient") || it.contains("to") || it.contains("merchant") }
        val amountIdx = header.indexOfFirst { it.contains("amount") }
        val typeIdx = header.indexOfFirst { it.contains("type") || it.contains("status") }

        lines.drop(1).forEachIndexed { index, line ->
            try {
                val columns = parseCSVLine(line)
                if (columns.size > maxOf(dateIdx, recipientIdx, amountIdx)) {
                    val dateStr = columns.getOrNull(dateIdx)?.trim() ?: ""
                    val recipient = columns.getOrNull(recipientIdx)?.trim() ?: "Unknown"
                    val amountStr = columns.getOrNull(amountIdx)?.trim()?.replace(",", "")?.replace("₹", "")?.replace("Rs", "")?.trim() ?: "0"
                    val type = columns.getOrNull(typeIdx)?.trim()?.lowercase() ?: ""

                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    val date = parseDate(dateStr, dateFormats)
                    val isCredit = type.contains("received") || type.contains("credit") || type.contains("cashback")

                    if (date != null && amount > 0) {
                        transactions.add(ParsedTransaction(
                            amount = amount,
                            merchantName = recipient,
                            date = date,
                            description = "PhonePe: $recipient",
                            isCredit = isCredit
                        ))
                    }
                }
            } catch (e: Exception) {
                errors.add("Row ${index + 2}: ${e.message}")
            }
        }

        return ImportResult(transactions.isNotEmpty(), transactions, errors, "PhonePe Export")
    }

    // Google Pay Export Parser
    private fun parseGPayExport(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()
        val dateFormats = listOf(
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        )

        val header = lines.first().lowercase().split(",").map { it.trim() }
        val dateIdx = header.indexOfFirst { it.contains("date") }
        val toIdx = header.indexOfFirst { it.contains("to") || it.contains("recipient") }
        val fromIdx = header.indexOfFirst { it.contains("from") || it.contains("sender") }
        val amountIdx = header.indexOfFirst { it.contains("amount") }

        lines.drop(1).forEachIndexed { index, line ->
            try {
                val columns = parseCSVLine(line)
                val dateStr = columns.getOrNull(dateIdx)?.trim() ?: ""
                val to = columns.getOrNull(toIdx)?.trim() ?: ""
                val from = columns.getOrNull(fromIdx)?.trim() ?: ""
                val amountStr = columns.getOrNull(amountIdx)?.trim()?.replace(",", "")?.replace("₹", "")?.replace("Rs", "")?.trim() ?: "0"

                val amount = amountStr.toDoubleOrNull() ?: 0.0
                val date = parseDate(dateStr, dateFormats)
                val isCredit = from.isNotBlank() && to.isBlank()
                val merchant = if (isCredit) from else to

                if (date != null && amount > 0 && merchant.isNotBlank()) {
                    transactions.add(ParsedTransaction(
                        amount = amount,
                        merchantName = merchant,
                        date = date,
                        description = "GPay: $merchant",
                        isCredit = isCredit
                    ))
                }
            } catch (e: Exception) {
                errors.add("Row ${index + 2}: ${e.message}")
            }
        }

        return ImportResult(transactions.isNotEmpty(), transactions, errors, "Google Pay Export")
    }

    // Generic CSV Parser
    private fun parseGenericCSV(lines: List<String>): ImportResult {
        val transactions = mutableListOf<ParsedTransaction>()
        val errors = mutableListOf<String>()
        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        )

        val header = lines.first().lowercase().split(",").map { it.trim() }
        val dateIdx = header.indexOfFirst { it.contains("date") }
        val descIdx = header.indexOfFirst { it.contains("description") || it.contains("narration") || it.contains("merchant") || it.contains("name") || it.contains("particulars") }
        val amountIdx = header.indexOfFirst { it.contains("amount") }
        val debitIdx = header.indexOfFirst { it.contains("debit") || it.contains("withdrawal") }
        val creditIdx = header.indexOfFirst { it.contains("credit") || it.contains("deposit") }
        val typeIdx = header.indexOfFirst { it.contains("type") }

        if (dateIdx == -1) {
            return ImportResult(false, emptyList(), listOf("CSV must have a 'Date' column"), "Generic CSV")
        }

        lines.drop(1).forEachIndexed { index, line ->
            try {
                val columns = parseCSVLine(line)
                val dateStr = columns.getOrNull(dateIdx)?.trim() ?: return@forEachIndexed
                val description = columns.getOrNull(descIdx)?.trim() ?: columns.getOrNull(1)?.trim() ?: "Unknown"

                val amount: Double
                val isCredit: Boolean

                when {
                    debitIdx != -1 && creditIdx != -1 -> {
                        val debit = columns.getOrNull(debitIdx)?.trim()?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                        val credit = columns.getOrNull(creditIdx)?.trim()?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                        amount = if (credit > 0) credit else debit
                        isCredit = credit > 0
                    }
                    amountIdx != -1 -> {
                        val amtStr = columns.getOrNull(amountIdx)?.trim()?.replace(",", "") ?: "0"
                        amount = amtStr.replace("-", "").toDoubleOrNull() ?: 0.0
                        val typeStr = columns.getOrNull(typeIdx)?.trim()?.lowercase() ?: ""
                        isCredit = typeStr.contains("credit") || typeStr.contains("cr") || !amtStr.startsWith("-")
                    }
                    else -> return@forEachIndexed
                }

                val date = parseDate(dateStr, dateFormats)
                if (date != null && amount > 0) {
                    transactions.add(ParsedTransaction(
                        amount = amount,
                        merchantName = extractMerchantFromDescription(description),
                        date = date,
                        description = description,
                        isCredit = isCredit
                    ))
                }
            } catch (e: Exception) {
                errors.add("Row ${index + 2}: ${e.message}")
            }
        }

        return ImportResult(transactions.isNotEmpty(), transactions, errors, "Generic CSV")
    }

    // Helper functions
    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }

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

    private fun extractMerchantFromDescription(description: String): String {
        // Common patterns to extract merchant names
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

        // Return cleaned description
        return description
            .replace(Regex("UPI/\\d+/"), "")
            .replace(Regex("IMPS/\\d+/"), "")
            .replace(Regex("NEFT/\\d+/"), "")
            .trim()
            .take(50)
            .ifBlank { "Unknown Merchant" }
    }

    fun convertToTransactionEntity(
        parsed: ParsedTransaction,
        category: String = "OTHER",
        categoryConfidence: Float = 0f,
        categorySource: String = "UNKNOWN"
    ): TransactionEntity {
        return TransactionEntity(
            id = UUID.randomUUID().toString(),
            amount = if (parsed.isCredit) parsed.amount else -parsed.amount,
            currency = "INR",
            merchantName = parsed.merchantName,
            merchantRaw = parsed.description,
            category = category,
            subcategory = null,
            timestamp = parsed.date,
            source = "IMPORT",
            categoryConfidence = categoryConfidence,
            categorySource = categorySource,
            isSynced = false,
            notes = "Imported from statement",
            rawNotificationText = parsed.description
        )
    }
}
