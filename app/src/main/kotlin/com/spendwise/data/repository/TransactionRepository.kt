package com.spendwise.data.repository

import com.spendwise.data.local.database.*
import com.spendwise.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val insightDao: InsightDao
) {
    fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return transactionDao.getAllTransactions()
    }

    fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByDateRange(start, end)
    }

    suspend fun getTotalSpent(start: Long, end: Long): Double {
        return transactionDao.getTotalSpent(start, end) ?: 0.0
    }

    suspend fun getTotalIncome(start: Long, end: Long): Double {
        return transactionDao.getTotalIncome(start, end) ?: 0.0
    }

    fun getCategorySummary(start: Long, end: Long): Flow<List<CategorySummaryResult>> {
        return transactionDao.getCategorySummary(start, end)
    }

    suspend fun getRecentTransactions(limit: Int): List<TransactionEntity> {
        return transactionDao.getTransactionsPaged(limit, 0)
    }

    suspend fun getMonthlyBudget(): Double {
        val now = System.currentTimeMillis()
        val overallBudget = budgetDao.getOverallBudget(now)
        return overallBudget?.amount ?: 40000.0 // Default budget
    }

    suspend fun getRecentInsights(limit: Int): List<InsightEntity> {
        return insightDao.getRecentInsights(limit)
    }

    suspend fun insert(transaction: TransactionEntity) {
        transactionDao.insert(transaction)
    }

    suspend fun getPendingReviewCount(): Int {
        // Threshold matching ReviewTransactionsViewModel
        return transactionDao.getLowConfidenceTransactions(0.85f).size
    }

    // Time range helpers
    companion object {
        fun getStartOfToday(): Long {
            return LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        fun getEndOfToday(): Long {
            return LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        fun getStartOfWeek(): Long {
            return LocalDate.now()
                .with(DayOfWeek.MONDAY)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        fun getStartOfMonth(): Long {
            return LocalDate.now()
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        fun getStartOfLastMonth(): Long {
            return LocalDate.now()
                .minusMonths(1)
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        fun getEndOfLastMonth(): Long {
            return LocalDate.now()
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
    }
}
