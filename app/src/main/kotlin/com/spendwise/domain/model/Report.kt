package com.spendwise.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class DateRange(
    val start: LocalDate,
    val end: LocalDate
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun dayCount(): Long = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
}

sealed class FinancialReport {
    abstract val generatedAt: Instant
    abstract val period: DateRange
    abstract val narrativeSummary: String

    data class DailyReport(
        override val generatedAt: Instant,
        override val period: DateRange,
        override val narrativeSummary: String,
        val totalSpent: BigDecimal,
        val transactionCount: Int,
        val transactions: List<Transaction>,
        val topCategories: List<CategorySummary>,
        val comparisonToAverage: ComparisonMetrics?
    ) : FinancialReport()

    data class WeeklyReport(
        override val generatedAt: Instant,
        override val period: DateRange,
        override val narrativeSummary: String,
        val totalSpent: BigDecimal,
        val dailyBreakdown: List<DailyTotal>,
        val categoryBreakdown: List<CategorySummary>,
        val topMerchants: List<MerchantSummary>,
        val weekOverWeekComparison: ComparisonMetrics?,
        val insights: List<SpendingInsight>
    ) : FinancialReport()

    data class MonthlyReport(
        override val generatedAt: Instant,
        override val period: DateRange,
        override val narrativeSummary: String,
        val totalSpent: BigDecimal,
        val totalIncome: BigDecimal?,
        val netAmount: BigDecimal?,
        val savingsRate: Float?,
        val weeklyBreakdown: List<WeeklyTotal>,
        val categoryBreakdown: List<CategorySummary>,
        val trends: List<SpendingTrend>,
        val budgetPerformance: List<BudgetPerformance>,
        val recommendations: List<Recommendation>
    ) : FinancialReport()

    data class QuarterlyReport(
        override val generatedAt: Instant,
        override val period: DateRange,
        override val narrativeSummary: String,
        val totalSpent: BigDecimal,
        val monthlyBreakdown: List<MonthlyTotal>,
        val categoryTrends: List<CategoryTrend>,
        val quarterOverQuarterComparison: ComparisonMetrics?,
        val insights: List<SpendingInsight>
    ) : FinancialReport()

    data class HalfYearlyReport(
        override val generatedAt: Instant,
        override val period: DateRange,
        override val narrativeSummary: String,
        val totalSpent: BigDecimal,
        val monthlyBreakdown: List<MonthlyTotal>,
        val categoryAnalysis: List<CategoryAnalysis>,
        val savingsAnalysis: SavingsAnalysis?,
        val recommendations: List<Recommendation>
    ) : FinancialReport()

    data class AnnualReport(
        override val generatedAt: Instant,
        override val period: DateRange,
        override val narrativeSummary: String,
        val totalSpent: BigDecimal,
        val totalIncome: BigDecimal?,
        val netSavings: BigDecimal?,
        val monthlyBreakdown: List<MonthlyTotal>,
        val categoryBreakdown: List<CategorySummary>,
        val yearOverYearComparison: ComparisonMetrics?,
        val topMerchants: List<MerchantSummary>,
        val achievements: List<Achievement>,
        val yearInReview: YearInReview
    ) : FinancialReport()
}

data class CategorySummary(
    val category: Category,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int,
    val changeFromPrevious: Float?
)

data class MerchantSummary(
    val merchantName: String,
    val totalAmount: BigDecimal,
    val transactionCount: Int,
    val category: Category
)

data class DailyTotal(
    val date: String,
    val amount: BigDecimal,
    val transactionCount: Int
)

data class WeeklyTotal(
    val weekNumber: Int,
    val startDate: String,
    val amount: BigDecimal
)

data class MonthlyTotal(
    val month: String,
    val amount: BigDecimal,
    val transactionCount: Int
)

data class ComparisonMetrics(
    val previousAmount: BigDecimal,
    val currentAmount: BigDecimal,
    val changePercent: Float,
    val direction: TrendDirection
)

enum class TrendDirection {
    INCREASING,
    DECREASING,
    STABLE
}

data class SpendingTrend(
    val category: Category,
    val direction: TrendDirection,
    val percentChange: Float,
    val periodCount: Int,
    val description: String
)

data class CategoryTrend(
    val category: Category,
    val monthlyAmounts: List<BigDecimal>,
    val trend: TrendDirection,
    val percentChange: Float
)

data class CategoryAnalysis(
    val category: Category,
    val totalAmount: BigDecimal,
    val averagePerMonth: BigDecimal,
    val trend: TrendDirection,
    val recommendations: List<String>
)

data class SpendingInsight(
    val type: InsightType,
    val title: String,
    val description: String,
    val action: String?,
    val priority: Priority,
    val relatedCategory: Category?
)

enum class InsightType {
    TREND,
    ANOMALY,
    OPPORTUNITY,
    ACHIEVEMENT,
    WARNING
}

enum class Priority {
    HIGH,
    MEDIUM,
    LOW
}

data class BudgetPerformance(
    val category: Category,
    val budgetAmount: BigDecimal,
    val spentAmount: BigDecimal,
    val percentUsed: Float,
    val status: BudgetStatus
)

enum class BudgetStatus {
    UNDER_BUDGET,
    ON_TRACK,
    WARNING,
    EXCEEDED
}

data class Recommendation(
    val area: String,
    val suggestion: String,
    val potentialImpact: String,
    val priority: Priority
)

data class SavingsAnalysis(
    val totalSaved: BigDecimal,
    val savingsRate: Float,
    val monthlyAverage: BigDecimal,
    val trend: TrendDirection
)

data class Achievement(
    val title: String,
    val description: String,
    val icon: String,
    val earnedAt: Instant
)

data class YearInReview(
    val headline: String,
    val topCategory: Category,
    val biggestExpense: Transaction,
    val mostFrequentMerchant: String,
    val totalTransactions: Int,
    val averagePerDay: BigDecimal
)
