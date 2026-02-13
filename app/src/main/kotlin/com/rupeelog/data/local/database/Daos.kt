package com.rupeelog.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsPaged(limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getTransactionsBetweenSync(startTime: Long, endTime: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY timestamp DESC")
    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM transactions
        WHERE timestamp BETWEEN :startTime AND :endTime
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getCategorySummary(startTime: Long, endTime: Long): Flow<List<CategorySummaryResult>>

    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime AND amount < 0")
    suspend fun getTotalSpent(startTime: Long, endTime: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime AND amount > 0")
    suspend fun getTotalIncome(startTime: Long, endTime: Long): Double?

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime AND amount < 0")
    fun getTotalSpendingBetweenSync(startTime: Long, endTime: Long): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTransactionCount(startTime: Long, endTime: Long): Int

    @Query("""
        SELECT merchant_name, SUM(amount) as total, COUNT(*) as count
        FROM transactions
        WHERE timestamp BETWEEN :startTime AND :endTime
        GROUP BY merchant_name
        ORDER BY total DESC
        LIMIT :limit
    """)
    suspend fun getTopMerchants(startTime: Long, endTime: Long, limit: Int): List<MerchantSummaryResult>

    @Query("SELECT AVG(amount) FROM transactions WHERE merchant_name = :merchantName")
    suspend fun getAverageForMerchant(merchantName: String): Double?

    @Query("SELECT * FROM transactions WHERE is_synced = 0")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Query("UPDATE transactions SET is_synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    @Query("""
        UPDATE transactions
        SET category = :category,
            subcategory = :subcategory,
            category_confidence = :confidence,
            category_source = :source,
            updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateCategory(
        id: String,
        category: String,
        subcategory: String?,
        confidence: Float,
        source: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM transactions WHERE category_confidence < :threshold AND category_source != 'USER_CORRECTED'")
    suspend fun getLowConfidenceTransactions(threshold: Float): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

data class CategorySummaryResult(
    val category: String,
    val total: Double,
    val count: Int
)

data class MerchantSummaryResult(
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,
    val total: Double,
    val count: Int
)

@Dao
interface CategoryRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CategoryRuleEntity)

    @Query("SELECT * FROM category_rules ORDER BY match_count DESC")
    suspend fun getAllRules(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rules WHERE is_user_created = 1")
    suspend fun getUserCreatedRules(): List<CategoryRuleEntity>

    @Query("""
        UPDATE category_rules
        SET match_count = match_count + 1
        WHERE id = :id
    """)
    suspend fun incrementMatchCount(id: Long)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE is_active = 1")
    fun getActiveBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE is_active = 1")
    suspend fun getAllBudgetsSync(): List<BudgetEntity>

    @Query("""
        SELECT * FROM budgets
        WHERE category = :category
        AND is_active = 1
        AND :timestamp BETWEEN period_start AND period_end
    """)
    suspend fun getBudgetForCategory(category: String, timestamp: Long): BudgetEntity?

    @Query("""
        SELECT * FROM budgets
        WHERE category IS NULL
        AND is_active = 1
        AND :timestamp BETWEEN period_start AND period_end
    """)
    suspend fun getOverallBudget(timestamp: Long): BudgetEntity?

    @Query("UPDATE budgets SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}

@Dao
interface InsightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(insight: InsightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(insights: List<InsightEntity>)

    @Query("SELECT * FROM insights WHERE is_dismissed = 0 ORDER BY created_at DESC")
    fun getActiveInsights(): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights WHERE is_dismissed = 0 ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentInsights(limit: Int): List<InsightEntity>

    @Query("UPDATE insights SET is_read = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE insights SET is_dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: String)

    @Query("DELETE FROM insights WHERE expires_at < :now")
    suspend fun deleteExpired(now: Long)
}

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingItems(): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = 'COMPLETED', processed_at = :processedAt WHERE id = :id")
    suspend fun markCompleted(id: String, processedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE sync_queue
        SET status = 'FAILED',
            error_message = :errorMessage,
            retry_count = retry_count + 1
        WHERE id = :id
    """)
    suspend fun markFailed(id: String, errorMessage: String?)

    @Query("UPDATE sync_queue SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetry(id: String)

    @Query("DELETE FROM sync_queue WHERE status = 'COMPLETED' AND processed_at < :before")
    suspend fun deleteOldCompleted(before: Long)
}
