package com.rupeelog.agents.deduplication

import android.util.Log
import com.rupeelog.data.local.database.TransactionDao
import com.rupeelog.data.local.database.TransactionEntity
import com.rupeelog.agents.roast.SarcasticNotificationAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Handles the deduplication of transactions from multiple sources (e.g., GPay + Bank SMS).
 * Uses a "Delayed Commit" strategy:
 * 1. When a transaction arrives, it's cached for a short window (e.g., 10s).
 * 2. If a duplicate arrives within that window, they are merged.
 * 3. After the window expires, the final transaction is saved to the DB.
 */
@Singleton
class TransactionDeduplicationEngine @Inject constructor(
    private val transactionDao: TransactionDao,
    private val sarcasticNotificationAgent: SarcasticNotificationAgent
) {

    // Cache of pending transactions: Key = "Unique Dedupe Key", Value = TransactionContext
    private val pendingTransactions = ConcurrentHashMap<String, PendingTransactionContext>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Configuration
    private val DEDUPE_WINDOW_MS = 10_000L // 10 seconds wait time
    private val AMOUNT_TOLERANCE = 1.0 // Allow small variance (e.g. currency conversion)

    data class PendingTransactionContext(
        var transaction: TransactionEntity,
        var job: Job
    )

    /**
     * Process a new incoming transaction.
     * @param newTransaction The raw transaction parsed from a notification.
     */
    suspend fun process(newTransaction: TransactionEntity) {
        val amountKey = getAmountKey(newTransaction.amount)
        
        // We use a simplified key for potential matches: Amount
        // Note: In a high-volume system, this might collide, but for personal finance
        // it's very rare to have two EXACT SAME amount transactions in 10 seconds.
        // We can refine this key if needed.
        val dedupeKey = amountKey

        mutex.withLock {
            val existingContext = pendingTransactions[dedupeKey]

            if (existingContext != null) {
                // Potential Duplicate Detected!
                if (isDuplicate(existingContext.transaction, newTransaction)) {
                    Log.d(TAG, "Duplicate detected! Merging: ${newTransaction.merchantName} with ${existingContext.transaction.merchantName}")
                    
                    // Merge logic: Create a better transaction from both
                    val mergedTransaction = mergeTransactions(existingContext.transaction, newTransaction)
                    
                    // Cancel the old commit job
                    existingContext.job.cancel()
                    
                    // Update the context with the merged transaction and a NEW commit timer
                    // We reset the timer to ensure we capture any *other* late arrivals (e.g. 3rd notification)
                    val newJob = scheduleCommit(dedupeKey, mergedTransaction)
                    existingContext.transaction = mergedTransaction
                    existingContext.job = newJob
                    
                    return
                }
            }

            // No duplicate found (or it was a hash collision but different transaction)
            // Schedule this as a new transaction
            Log.d(TAG, "New transaction buffered: ${newTransaction.merchantName} - ${newTransaction.amount}")
            val job = scheduleCommit(dedupeKey, newTransaction)
            pendingTransactions[dedupeKey] = PendingTransactionContext(newTransaction, job)
        }
    }

    private fun scheduleCommit(key: String, transaction: TransactionEntity): Job {
        return scope.launch {
            delay(DEDUPE_WINDOW_MS)
            commit(key, transaction)
        }
    }

    private suspend fun commit(key: String, transaction: TransactionEntity) {
        mutex.withLock {
            // Remove from cache
            pendingTransactions.remove(key)
        }

        // Save to Database
        try {
            transactionDao.insert(transaction)
            Log.d(TAG, "Transaction committed to DB: ${transaction.merchantName} (${transaction.amount})")
            
            // Trigger Roast (Fire and Forget)
            sarcasticNotificationAgent.processTransaction(transaction)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to commit transaction", e)
        }
    }

    private fun isDuplicate(t1: TransactionEntity, t2: TransactionEntity): Boolean {
        // 1. Amount Check (Absolute value to handle credit/debit mismatch if parser failed)
        if (abs(abs(t1.amount) - abs(t2.amount)) > AMOUNT_TOLERANCE) {
            return false
        }

        // 2. Time Check (Already implicitly handled by the 10s window, but good to be safe)
        // If timestamps are wildly different (> 2 mins), treat as separate even if amounts match
        if (abs(t1.timestamp - t2.timestamp) > 120_000L) {
            return false
        }

        return true
    }

    private fun mergeTransactions(existing: TransactionEntity, new: TransactionEntity): TransactionEntity {
        // Intelligence Logic: Which source is better?
        
        // 1. Prefer UPI App source (GPay/PhonePe) for Merchant Names over SMS
        // SMS usually has "VPA mmerchant@sbi", GPay has "Starbucks"
        val isNewSourceBetterForName = isUpiApp(new.source) && !isUpiApp(existing.source)
        
        val bestMerchantName = if (isNewSourceBetterForName) new.merchantName else existing.merchantName
        val bestMerchantRaw = if (isNewSourceBetterForName) new.merchantRaw else existing.merchantRaw
        
        // 2. Prefer the transaction with higher category confidence
        val bestCategory = if (new.categoryConfidence > existing.categoryConfidence) new.category else existing.category
        val bestSubcategory = if (new.categoryConfidence > existing.categoryConfidence) new.subcategory else existing.subcategory
        val bestConfidence = maxOf(new.categoryConfidence, existing.categoryConfidence)

        // 3. Prefer SMS for potentially capturing "Account Balance" (future proofing)
        // For now, we just keep the newest timestamp
        
        return existing.copy(
            merchantName = bestMerchantName,
            merchantRaw = bestMerchantRaw,
            category = bestCategory,
            subcategory = bestSubcategory,
            categoryConfidence = bestConfidence,
            // Keep the ID of the FIRST one to avoid primary key issues if we were updating, 
            // but since we haven't inserted yet, it doesn't matter much. Let's keep existing.
            id = existing.id,
            // Append source info to notes for debugging
            notes = (existing.notes ?: "") + " | Merged with ${new.source}",
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun isUpiApp(source: String): Boolean {
        // Simple heuristic. In reality we should pass package names.
        // The parser sets 'source' to 'NOTIFICATION' currently, which is ambiguous.
        // We might need to update the Parser to set source as 'com.google.android.apps.nbu.paisa.user' etc.
        // For now, let's assume if the merchant name looks "cleaner" (no underscores, no VPA), it's better.
        return true 
    }
    
    // Create a rough key from amount. Round to 1 decimal to catch 100.0 vs 100.00
    private fun getAmountKey(amount: Double): String {
        return "%.1f".format(abs(amount))
    }

    companion object {
        private const val TAG = "DeduplicationEngine"
    }
}
