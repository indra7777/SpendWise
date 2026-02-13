package com.spendwise.sms

import android.util.Log
import com.spendwise.agents.categorization.CategorizationAgent
import com.spendwise.data.local.database.TransactionDao
import com.spendwise.data.local.database.TransactionEntity
import com.spendwise.parser.core.ParserRegistry
import com.spendwise.parser.core.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of an SMS import operation.
 */
data class ImportResult(
    val totalSmsRead: Int,
    val transactionsParsed: Int,
    val transactionsImported: Int,
    val duplicatesSkipped: Int,
    val errors: Int
)

/**
 * Progress update during import.
 */
data class ImportProgress(
    val current: Int,
    val total: Int,
    val currentSms: String? = null,
    val status: String = "Importing..."
)

/**
 * Imports transactions from SMS history.
 *
 * Uses the parser registry to parse bank SMS messages
 * and stores them in the transaction database.
 */
@Singleton
class SmsTransactionImporter @Inject constructor(
    private val smsReader: SmsReader,
    private val parserRegistry: ParserRegistry,
    private val transactionDao: TransactionDao,
    private val categorizationAgent: CategorizationAgent
) {
    companion object {
        private const val TAG = "SmsTransactionImporter"
    }

    /**
     * Import all bank SMS as transactions.
     *
     * @param sinceTimestamp Only import SMS after this timestamp (0 = all time)
     * @return ImportResult with statistics
     */
    suspend fun importAll(sinceTimestamp: Long = 0): ImportResult = withContext(Dispatchers.IO) {
        var totalSmsRead = 0
        var transactionsParsed = 0
        var transactionsImported = 0
        var duplicatesSkipped = 0
        var errors = 0

        try {
            val bankSms = smsReader.readBankSms(sinceTimestamp = sinceTimestamp)
            totalSmsRead = bankSms.size

            Log.d(TAG, "Starting import of $totalSmsRead bank SMS messages")

            for (sms in bankSms) {
                try {
                    val parsed = parserRegistry.parse(
                        body = sms.body,
                        sender = sms.address,
                        timestamp = sms.date
                    )

                    if (parsed != null) {
                        transactionsParsed++

                        // Check for duplicates using transaction hash
                        val existing = transactionDao.getById(parsed.transactionHash)
                        if (existing != null) {
                            duplicatesSkipped++
                            continue
                        }

                        // Categorize the transaction
                        val categoryResult = categorizationAgent.categorize(
                            merchantText = parsed.merchant ?: "Unknown",
                            amount = parsed.amount.toDouble()
                        )

                        // Create transaction entity
                        val transaction = TransactionEntity(
                            id = parsed.transactionHash,
                            amount = parsed.getSignedAmount().toDouble(),
                            currency = parsed.currency,
                            merchantName = categoryResult.merchantName ?: parsed.merchant ?: "Unknown",
                            merchantRaw = parsed.merchant ?: "Unknown",
                            category = categoryResult.category,
                            subcategory = categoryResult.subcategory,
                            timestamp = parsed.timestamp,
                            source = "SMS:${parsed.bankName}",
                            categoryConfidence = categoryResult.confidence,
                            categorySource = categoryResult.source,
                            rawNotificationText = null // DPDP: Don't store raw SMS
                        )

                        transactionDao.insert(transaction)
                        transactionsImported++

                        Log.d(TAG, "Imported: ${parsed.type} ${parsed.amount} from ${parsed.bankName}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing SMS: ${sms.address}", e)
                    errors++
                }
            }

            Log.d(TAG, "Import complete: $transactionsImported imported, $duplicatesSkipped duplicates, $errors errors")

        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            errors++
        }

        ImportResult(
            totalSmsRead = totalSmsRead,
            transactionsParsed = transactionsParsed,
            transactionsImported = transactionsImported,
            duplicatesSkipped = duplicatesSkipped,
            errors = errors
        )
    }

    /**
     * Import with progress updates via Flow.
     */
    fun importWithProgress(sinceTimestamp: Long = 0): Flow<ImportProgress> = flow {
        val bankSms = smsReader.readBankSms(sinceTimestamp = sinceTimestamp)
        val total = bankSms.size

        emit(ImportProgress(0, total, status = "Found $total bank SMS messages"))

        var imported = 0

        for ((index, sms) in bankSms.withIndex()) {
            emit(ImportProgress(
                current = index + 1,
                total = total,
                currentSms = "${sms.address}: ${sms.body.take(50)}...",
                status = "Processing ${index + 1}/$total"
            ))

            try {
                val parsed = parserRegistry.parse(
                    body = sms.body,
                    sender = sms.address,
                    timestamp = sms.date
                )

                if (parsed != null) {
                    Log.d(TAG, "Parsed: ${parsed.bankName} | ${parsed.type} | ₹${parsed.amount} | ${parsed.merchant ?: "NO_MERCHANT"}")

                    val existing = transactionDao.getById(parsed.transactionHash)
                    if (existing == null) {
                        val categoryResult = categorizationAgent.categorize(
                            merchantText = parsed.merchant ?: "Unknown",
                            amount = parsed.amount.toDouble()
                        )

                        val transaction = TransactionEntity(
                            id = parsed.transactionHash,
                            amount = parsed.getSignedAmount().toDouble(),
                            currency = parsed.currency,
                            merchantName = categoryResult.merchantName ?: parsed.merchant ?: "Unknown",
                            merchantRaw = parsed.merchant ?: "Unknown",
                            category = categoryResult.category,
                            subcategory = categoryResult.subcategory,
                            timestamp = parsed.timestamp,
                            source = "SMS:${parsed.bankName}",
                            categoryConfidence = categoryResult.confidence,
                            categorySource = categoryResult.source,
                            rawNotificationText = null
                        )

                        transactionDao.insert(transaction)
                        imported++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            }
        }

        emit(ImportProgress(
            current = total,
            total = total,
            status = "Import complete! $imported transactions imported"
        ))
    }

    /**
     * Get count of importable bank SMS.
     */
    suspend fun getImportableCount(sinceTimestamp: Long = 0): Int = withContext(Dispatchers.IO) {
        smsReader.getBankSmsCount(sinceTimestamp)
    }
}
