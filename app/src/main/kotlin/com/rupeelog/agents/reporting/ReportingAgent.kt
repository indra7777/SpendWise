package com.rupeelog.agents.reporting

import com.rupeelog.agents.core.GeminiClient
import com.rupeelog.data.local.database.TransactionDao
import com.rupeelog.data.local.database.BudgetDao
import com.rupeelog.data.local.database.TransactionEntity
import com.rupeelog.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportingAgent @Inject constructor(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val geminiClient: GeminiClient
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    suspend fun generateDailyReport(date: LocalDate = LocalDate.now()): FinancialReport.DailyReport = withContext(Dispatchers.IO) {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val transactions = transactionDao.getTransactionsBetweenSync(startOfDay, endOfDay)
        val totalSpent = transactions.sumOf { it.amount }.toBigDecimal()
        val categories = calculateCategorySummaries(transactions)

        // Get average for comparison
        val avgDaily = calculateAverageDailySpending(30)
        val comparison = if (avgDaily > BigDecimal.ZERO) {
            val changePercent = ((totalSpent - avgDaily) / avgDaily * BigDecimal(100)).toFloat()
            ComparisonMetrics(
                previousAmount = avgDaily,
                currentAmount = totalSpent,
                changePercent = changePercent,
                direction = when {
                    changePercent > 5 -> TrendDirection.INCREASING
                    changePercent < -5 -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                }
            )
        } else null

        val narrative = generateDailyNarrative(totalSpent, transactions.size, categories.firstOrNull())

        FinancialReport.DailyReport(
            generatedAt = Instant.now(),
            period = DateRange(date, date),
            narrativeSummary = narrative,
            totalSpent = totalSpent,
            transactionCount = transactions.size,
            transactions = transactions.map { it.toDomainModel() },
            topCategories = categories.take(5),
            comparisonToAverage = comparison
        )
    }

    suspend fun generateWeeklyReport(weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))): FinancialReport.WeeklyReport = withContext(Dispatchers.IO) {
        val weekEnd = weekStart.plusDays(6)
        val startMillis = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = weekEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val transactions = transactionDao.getTransactionsBetweenSync(startMillis, endMillis)
        val totalSpent = transactions.sumOf { it.amount }.toBigDecimal()

        // Daily breakdown
        val dailyBreakdown = (0..6).map { dayOffset ->
            val day = weekStart.plusDays(dayOffset.toLong())
            val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayTxs = transactions.filter { it.timestamp in dayStart until dayEnd }
            DailyTotal(
                date = day.format(dateFormatter),
                amount = dayTxs.sumOf { it.amount }.toBigDecimal(),
                transactionCount = dayTxs.size
            )
        }

        val categories = calculateCategorySummaries(transactions)
        val merchants = calculateMerchantSummaries(transactions)

        // Week over week comparison
        val prevWeekStart = weekStart.minusWeeks(1)
        val prevWeekEnd = prevWeekStart.plusDays(6)
        val prevStartMillis = prevWeekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val prevEndMillis = prevWeekEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val prevTransactions = transactionDao.getTransactionsBetweenSync(prevStartMillis, prevEndMillis)
        val prevTotal = prevTransactions.sumOf { it.amount }.toBigDecimal()

        val comparison = if (prevTotal > BigDecimal.ZERO) {
            val changePercent = ((totalSpent - prevTotal) / prevTotal * BigDecimal(100)).toFloat()
            ComparisonMetrics(
                previousAmount = prevTotal,
                currentAmount = totalSpent,
                changePercent = changePercent,
                direction = when {
                    changePercent > 5 -> TrendDirection.INCREASING
                    changePercent < -5 -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                }
            )
        } else null

        val insights = generateWeeklyInsights(transactions, categories, comparison)
        val narrative = generateWeeklyNarrative(totalSpent, transactions.size, categories, comparison)

        FinancialReport.WeeklyReport(
            generatedAt = Instant.now(),
            period = DateRange(weekStart, weekEnd),
            narrativeSummary = narrative,
            totalSpent = totalSpent,
            dailyBreakdown = dailyBreakdown,
            categoryBreakdown = categories,
            topMerchants = merchants.take(5),
            weekOverWeekComparison = comparison,
            insights = insights
        )
    }

    suspend fun generateMonthlyReport(yearMonth: YearMonth = YearMonth.now()): FinancialReport.MonthlyReport = withContext(Dispatchers.IO) {
        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()
        val startMillis = monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = monthEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val transactions = transactionDao.getTransactionsBetweenSync(startMillis, endMillis)
        val totalSpent = transactions.sumOf { it.amount }.toBigDecimal()

        // Weekly breakdown
        val weeklyBreakdown = mutableListOf<WeeklyTotal>()
        var weekNum = 1
        var currentStart = monthStart
        while (!currentStart.isAfter(monthEnd)) {
            val weekEnd = minOf(currentStart.plusDays(6), monthEnd)
            val wStart = currentStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val wEnd = weekEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val weekTxs = transactions.filter { it.timestamp in wStart until wEnd }
            weeklyBreakdown.add(
                WeeklyTotal(
                    weekNumber = weekNum++,
                    startDate = currentStart.format(dateFormatter),
                    amount = weekTxs.sumOf { it.amount }.toBigDecimal()
                )
            )
            currentStart = weekEnd.plusDays(1)
        }

        val categories = calculateCategorySummaries(transactions)
        val budgets = budgetDao.getAllBudgetsSync()

        // Budget performance
        val budgetPerformance = budgets.mapNotNull { budget ->
            try {
                val categoryName = budget.category ?: return@mapNotNull null
                val cat = Category.valueOf(categoryName)
                val spent = categories.find { it.category == cat }?.amount ?: BigDecimal.ZERO
                val budgetAmount = budget.amount.toBigDecimal()
                val percentUsed = if (budgetAmount > BigDecimal.ZERO) {
                    (spent / budgetAmount * BigDecimal(100)).toFloat()
                } else 0f
                BudgetPerformance(
                    category = cat,
                    budgetAmount = budgetAmount,
                    spentAmount = spent,
                    percentUsed = percentUsed,
                    status = when {
                        percentUsed > 100 -> BudgetStatus.EXCEEDED
                        percentUsed > 80 -> BudgetStatus.WARNING
                        percentUsed > 50 -> BudgetStatus.ON_TRACK
                        else -> BudgetStatus.UNDER_BUDGET
                    }
                )
            } catch (e: Exception) {
                null
            }
        }

        // Calculate trends compared to previous months
        val trends = calculateSpendingTrends(categories, yearMonth)
        val recommendations = generateRecommendations(categories, budgetPerformance, trends)
        val narrative = generateMonthlyNarrative(totalSpent, categories, budgetPerformance)

        FinancialReport.MonthlyReport(
            generatedAt = Instant.now(),
            period = DateRange(monthStart, monthEnd),
            narrativeSummary = narrative,
            totalSpent = totalSpent,
            totalIncome = null,
            netAmount = null,
            savingsRate = null,
            weeklyBreakdown = weeklyBreakdown,
            categoryBreakdown = categories,
            trends = trends,
            budgetPerformance = budgetPerformance,
            recommendations = recommendations
        )
    }

    suspend fun generateQuarterlyReport(year: Int = LocalDate.now().year, quarter: Int = (LocalDate.now().monthValue - 1) / 3 + 1): FinancialReport.QuarterlyReport = withContext(Dispatchers.IO) {
        val quarterStart = LocalDate.of(year, (quarter - 1) * 3 + 1, 1)
        val quarterEnd = quarterStart.plusMonths(3).minusDays(1)
        val startMillis = quarterStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = quarterEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val transactions = transactionDao.getTransactionsBetweenSync(startMillis, endMillis)
        val totalSpent = transactions.sumOf { it.amount }.toBigDecimal()

        // Monthly breakdown
        val monthlyBreakdown = (0..2).map { monthOffset ->
            val month = YearMonth.from(quarterStart.plusMonths(monthOffset.toLong()))
            val mStart = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val mEnd = month.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val monthTxs = transactions.filter { it.timestamp in mStart until mEnd }
            MonthlyTotal(
                month = month.format(monthFormatter),
                amount = monthTxs.sumOf { it.amount }.toBigDecimal(),
                transactionCount = monthTxs.size
            )
        }

        // Category trends across months
        val categoryTrends = calculateCategoryTrends(quarterStart, quarterEnd)

        // Compare to previous quarter
        val prevQuarterStart = quarterStart.minusMonths(3)
        val prevQuarterEnd = prevQuarterStart.plusMonths(3).minusDays(1)
        val prevStartMillis = prevQuarterStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val prevEndMillis = prevQuarterEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val prevTransactions = transactionDao.getTransactionsBetweenSync(prevStartMillis, prevEndMillis)
        val prevTotal = prevTransactions.sumOf { it.amount }.toBigDecimal()

        val comparison = if (prevTotal > BigDecimal.ZERO) {
            val changePercent = ((totalSpent - prevTotal) / prevTotal * BigDecimal(100)).toFloat()
            ComparisonMetrics(
                previousAmount = prevTotal,
                currentAmount = totalSpent,
                changePercent = changePercent,
                direction = when {
                    changePercent > 5 -> TrendDirection.INCREASING
                    changePercent < -5 -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                }
            )
        } else null

        val insights = generateQuarterlyInsights(categoryTrends, comparison)
        val narrative = "Q$quarter $year: You spent ₹${totalSpent.setScale(0, RoundingMode.HALF_UP)} across ${transactions.size} transactions."

        FinancialReport.QuarterlyReport(
            generatedAt = Instant.now(),
            period = DateRange(quarterStart, quarterEnd),
            narrativeSummary = narrative,
            totalSpent = totalSpent,
            monthlyBreakdown = monthlyBreakdown,
            categoryTrends = categoryTrends,
            quarterOverQuarterComparison = comparison,
            insights = insights
        )
    }

    suspend fun generateHalfYearlyReport(year: Int = LocalDate.now().year, firstHalf: Boolean = LocalDate.now().monthValue <= 6): FinancialReport.HalfYearlyReport = withContext(Dispatchers.IO) {
        val halfStart = if (firstHalf) LocalDate.of(year, 1, 1) else LocalDate.of(year, 7, 1)
        val halfEnd = if (firstHalf) LocalDate.of(year, 6, 30) else LocalDate.of(year, 12, 31)
        val startMillis = halfStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = halfEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val transactions = transactionDao.getTransactionsBetweenSync(startMillis, endMillis)
        val totalSpent = transactions.sumOf { it.amount }.toBigDecimal()

        // Monthly breakdown
        val monthlyBreakdown = (0..5).map { monthOffset ->
            val month = YearMonth.from(halfStart.plusMonths(monthOffset.toLong()))
            val mStart = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val mEnd = month.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val monthTxs = transactions.filter { it.timestamp in mStart until mEnd }
            MonthlyTotal(
                month = month.format(monthFormatter),
                amount = monthTxs.sumOf { it.amount }.toBigDecimal(),
                transactionCount = monthTxs.size
            )
        }

        // Category analysis
        val categoryAnalysis = Category.entries.mapNotNull { category ->
            val catTxs = transactions.filter { it.category == category.name }
            if (catTxs.isEmpty()) return@mapNotNull null
            val total = catTxs.sumOf { it.amount }.toBigDecimal()
            CategoryAnalysis(
                category = category,
                totalAmount = total,
                averagePerMonth = total.divide(BigDecimal(6), 2, RoundingMode.HALF_UP),
                trend = TrendDirection.STABLE,
                recommendations = emptyList()
            )
        }

        val recommendations = generateHalfYearlyRecommendations(categoryAnalysis)
        val halfLabel = if (firstHalf) "H1" else "H2"
        val narrative = "$halfLabel $year: Total spending of ₹${totalSpent.setScale(0, RoundingMode.HALF_UP)} over 6 months."

        FinancialReport.HalfYearlyReport(
            generatedAt = Instant.now(),
            period = DateRange(halfStart, halfEnd),
            narrativeSummary = narrative,
            totalSpent = totalSpent,
            monthlyBreakdown = monthlyBreakdown,
            categoryAnalysis = categoryAnalysis,
            savingsAnalysis = null,
            recommendations = recommendations
        )
    }

    suspend fun generateAnnualReport(year: Int = LocalDate.now().year): FinancialReport.AnnualReport = withContext(Dispatchers.IO) {
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        val startMillis = yearStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = yearEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val transactions = transactionDao.getTransactionsBetweenSync(startMillis, endMillis)
        val totalSpent = transactions.sumOf { it.amount }.toBigDecimal()

        // Monthly breakdown
        val monthlyBreakdown = (1..12).map { month ->
            val ym = YearMonth.of(year, month)
            val mStart = ym.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val mEnd = ym.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val monthTxs = transactions.filter { it.timestamp in mStart until mEnd }
            MonthlyTotal(
                month = ym.format(monthFormatter),
                amount = monthTxs.sumOf { it.amount }.toBigDecimal(),
                transactionCount = monthTxs.size
            )
        }

        val categories = calculateCategorySummaries(transactions)
        val merchants = calculateMerchantSummaries(transactions)

        // Year over year comparison
        val prevYearStart = LocalDate.of(year - 1, 1, 1)
        val prevYearEnd = LocalDate.of(year - 1, 12, 31)
        val prevStartMillis = prevYearStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val prevEndMillis = prevYearEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val prevTransactions = transactionDao.getTransactionsBetweenSync(prevStartMillis, prevEndMillis)
        val prevTotal = prevTransactions.sumOf { it.amount }.toBigDecimal()

        val comparison = if (prevTotal > BigDecimal.ZERO) {
            val changePercent = ((totalSpent - prevTotal) / prevTotal * BigDecimal(100)).toFloat()
            ComparisonMetrics(
                previousAmount = prevTotal,
                currentAmount = totalSpent,
                changePercent = changePercent,
                direction = when {
                    changePercent > 5 -> TrendDirection.INCREASING
                    changePercent < -5 -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                }
            )
        } else null

        val achievements = generateAchievements(transactions, categories)
        val biggestExpense = transactions.maxByOrNull { it.amount }
        val mostFrequentMerchant = merchants.maxByOrNull { it.transactionCount }?.merchantName ?: "N/A"

        val yearInReview = YearInReview(
            headline = "$year: A Year of Financial Insights",
            topCategory = categories.firstOrNull()?.category ?: Category.OTHER,
            biggestExpense = biggestExpense?.toDomainModel() ?: createPlaceholderTransaction(),
            mostFrequentMerchant = mostFrequentMerchant,
            totalTransactions = transactions.size,
            averagePerDay = if (transactions.isNotEmpty()) {
                totalSpent.divide(BigDecimal(365), 2, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
        )

        val narrative = "$year Year in Review: You spent ₹${totalSpent.setScale(0, RoundingMode.HALF_UP)} across ${transactions.size} transactions."

        FinancialReport.AnnualReport(
            generatedAt = Instant.now(),
            period = DateRange(yearStart, yearEnd),
            narrativeSummary = narrative,
            totalSpent = totalSpent,
            totalIncome = null,
            netSavings = null,
            monthlyBreakdown = monthlyBreakdown,
            categoryBreakdown = categories,
            yearOverYearComparison = comparison,
            topMerchants = merchants.take(10),
            achievements = achievements,
            yearInReview = yearInReview
        )
    }

    // Helper functions
    private fun calculateCategorySummaries(transactions: List<TransactionEntity>): List<CategorySummary> {
        val total = transactions.sumOf { it.amount }
        return transactions
            .groupBy { it.category }
            .map { (category, txs) ->
                val amount = txs.sumOf { it.amount }.toBigDecimal()
                CategorySummary(
                    category = try { Category.valueOf(category) } catch (e: Exception) { Category.OTHER },
                    amount = amount,
                    percentage = if (total > 0) (txs.sumOf { it.amount } / total * 100).toFloat() else 0f,
                    transactionCount = txs.size,
                    changeFromPrevious = null
                )
            }
            .sortedByDescending { it.amount }
    }

    private fun calculateMerchantSummaries(transactions: List<TransactionEntity>): List<MerchantSummary> {
        return transactions
            .groupBy { it.merchantName }
            .map { (merchant, txs) ->
                MerchantSummary(
                    merchantName = merchant,
                    totalAmount = txs.sumOf { it.amount }.toBigDecimal(),
                    transactionCount = txs.size,
                    category = try { Category.valueOf(txs.first().category) } catch (e: Exception) { Category.OTHER }
                )
            }
            .sortedByDescending { it.totalAmount }
    }

    private suspend fun calculateAverageDailySpending(days: Int): BigDecimal {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())
        val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val transactions = transactionDao.getTransactionsBetweenSync(startMillis, endMillis)
        val total = transactions.sumOf { it.amount }.toBigDecimal()
        return if (days > 0) total.divide(BigDecimal(days), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
    }

    private suspend fun calculateSpendingTrends(categories: List<CategorySummary>, currentMonth: YearMonth): List<SpendingTrend> {
        return categories.take(5).mapNotNull { cat ->
            val prevMonths = (1..3).map { offset ->
                val month = currentMonth.minusMonths(offset.toLong())
                val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = month.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                transactionDao.getTransactionsBetweenSync(start, end)
                    .filter { it.category == cat.category.name }
                    .sumOf { it.amount }
            }
            val avgPrev = if (prevMonths.isNotEmpty()) prevMonths.average() else 0.0
            if (avgPrev > 0) {
                val changePercent = ((cat.amount.toDouble() - avgPrev) / avgPrev * 100).toFloat()
                SpendingTrend(
                    category = cat.category,
                    direction = when {
                        changePercent > 10 -> TrendDirection.INCREASING
                        changePercent < -10 -> TrendDirection.DECREASING
                        else -> TrendDirection.STABLE
                    },
                    percentChange = changePercent,
                    periodCount = 3,
                    description = when {
                        changePercent > 10 -> "${cat.category.displayName} spending increased by ${changePercent.toInt()}%"
                        changePercent < -10 -> "${cat.category.displayName} spending decreased by ${(-changePercent).toInt()}%"
                        else -> "${cat.category.displayName} spending is stable"
                    }
                )
            } else null
        }
    }

    private suspend fun calculateCategoryTrends(start: LocalDate, end: LocalDate): List<CategoryTrend> {
        val months = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(end)) }
            .toList()

        return Category.entries.mapNotNull { category ->
            val monthlyAmounts = months.map { month ->
                val mStart = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val mEnd = month.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                transactionDao.getTransactionsBetweenSync(mStart, mEnd)
                    .filter { it.category == category.name }
                    .sumOf { it.amount }.toBigDecimal()
            }
            if (monthlyAmounts.all { it == BigDecimal.ZERO }) return@mapNotNull null

            val firstNonZero = monthlyAmounts.firstOrNull { it > BigDecimal.ZERO } ?: BigDecimal.ONE
            val lastNonZero = monthlyAmounts.lastOrNull { it > BigDecimal.ZERO } ?: BigDecimal.ZERO
            val changePercent = ((lastNonZero - firstNonZero) / firstNonZero * BigDecimal(100)).toFloat()

            CategoryTrend(
                category = category,
                monthlyAmounts = monthlyAmounts,
                trend = when {
                    changePercent > 10 -> TrendDirection.INCREASING
                    changePercent < -10 -> TrendDirection.DECREASING
                    else -> TrendDirection.STABLE
                },
                percentChange = changePercent
            )
        }
    }

    private fun generateWeeklyInsights(
        transactions: List<TransactionEntity>,
        categories: List<CategorySummary>,
        comparison: ComparisonMetrics?
    ): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()

        if (comparison != null) {
            when {
                comparison.changePercent > 20 -> {
                    insights.add(SpendingInsight(
                        type = InsightType.WARNING,
                        title = "Spending Spike",
                        description = "Your spending is up ${comparison.changePercent.toInt()}% compared to last week.",
                        action = "Review your transactions to identify unusual expenses.",
                        priority = Priority.HIGH,
                        relatedCategory = null
                    ))
                }
                comparison.changePercent < -20 -> {
                    insights.add(SpendingInsight(
                        type = InsightType.ACHIEVEMENT,
                        title = "Great Week!",
                        description = "You spent ${(-comparison.changePercent).toInt()}% less than last week.",
                        action = null,
                        priority = Priority.MEDIUM,
                        relatedCategory = null
                    ))
                }
            }
        }

        categories.firstOrNull()?.let { topCat ->
            if (topCat.percentage > 50) {
                insights.add(SpendingInsight(
                    type = InsightType.TREND,
                    title = "${topCat.category.displayName} Dominates",
                    description = "${topCat.percentage.toInt()}% of your spending was on ${topCat.category.displayName}.",
                    action = "Consider if this aligns with your priorities.",
                    priority = Priority.MEDIUM,
                    relatedCategory = topCat.category
                ))
            }
        }

        return insights
    }

    private fun generateQuarterlyInsights(
        categoryTrends: List<CategoryTrend>,
        comparison: ComparisonMetrics?
    ): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()

        categoryTrends.filter { it.trend == TrendDirection.INCREASING && it.percentChange > 25 }.forEach { trend ->
            insights.add(SpendingInsight(
                type = InsightType.WARNING,
                title = "Rising ${trend.category.displayName}",
                description = "${trend.category.displayName} spending has increased ${trend.percentChange.toInt()}% this quarter.",
                action = "Review and set a budget for this category.",
                priority = Priority.HIGH,
                relatedCategory = trend.category
            ))
        }

        categoryTrends.filter { it.trend == TrendDirection.DECREASING && it.percentChange < -25 }.forEach { trend ->
            insights.add(SpendingInsight(
                type = InsightType.ACHIEVEMENT,
                title = "Reduced ${trend.category.displayName}",
                description = "You've cut ${trend.category.displayName} spending by ${(-trend.percentChange).toInt()}%!",
                action = null,
                priority = Priority.MEDIUM,
                relatedCategory = trend.category
            ))
        }

        return insights
    }

    private fun generateRecommendations(
        categories: List<CategorySummary>,
        budgetPerformance: List<BudgetPerformance>,
        trends: List<SpendingTrend>
    ): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()

        budgetPerformance.filter { it.status == BudgetStatus.EXCEEDED }.forEach { bp ->
            recommendations.add(Recommendation(
                area = bp.category.displayName,
                suggestion = "Reduce ${bp.category.displayName} spending or increase budget",
                potentialImpact = "Save ₹${(bp.spentAmount - bp.budgetAmount).setScale(0, RoundingMode.HALF_UP)} monthly",
                priority = Priority.HIGH
            ))
        }

        trends.filter { it.direction == TrendDirection.INCREASING && it.percentChange > 15 }.forEach { trend ->
            recommendations.add(Recommendation(
                area = trend.category.displayName,
                suggestion = "Monitor ${trend.category.displayName} - it's trending upward",
                potentialImpact = "Prevent budget overruns",
                priority = Priority.MEDIUM
            ))
        }

        return recommendations
    }

    private fun generateHalfYearlyRecommendations(categoryAnalysis: List<CategoryAnalysis>): List<Recommendation> {
        return categoryAnalysis
            .sortedByDescending { it.totalAmount }
            .take(3)
            .map { ca ->
                Recommendation(
                    area = ca.category.displayName,
                    suggestion = "Optimize your ${ca.category.displayName} spending (₹${ca.averagePerMonth.setScale(0, RoundingMode.HALF_UP)}/month avg)",
                    potentialImpact = "Potential 10-15% savings",
                    priority = Priority.MEDIUM
                )
            }
    }

    private fun generateAchievements(
        transactions: List<TransactionEntity>,
        categories: List<CategorySummary>
    ): List<Achievement> {
        val achievements = mutableListOf<Achievement>()

        if (transactions.size >= 100) {
            achievements.add(Achievement(
                title = "Century Club",
                description = "Tracked 100+ transactions this year",
                icon = "🏆",
                earnedAt = Instant.now()
            ))
        }

        if (categories.size >= 5) {
            achievements.add(Achievement(
                title = "Diverse Spender",
                description = "Spent across 5+ categories",
                icon = "🌈",
                earnedAt = Instant.now()
            ))
        }

        achievements.add(Achievement(
            title = "Finance Tracker",
            description = "Completed a full year of expense tracking",
            icon = "📊",
            earnedAt = Instant.now()
        ))

        return achievements
    }

    private fun generateDailyNarrative(total: BigDecimal, count: Int, topCategory: CategorySummary?): String {
        return buildString {
            append("Today you spent ₹${total.setScale(0, RoundingMode.HALF_UP)}")
            if (count > 0) append(" across $count transaction${if (count > 1) "s" else ""}")
            topCategory?.let { append(". Top category: ${it.category.displayName}") }
            append(".")
        }
    }

    private fun generateWeeklyNarrative(
        total: BigDecimal,
        count: Int,
        categories: List<CategorySummary>,
        comparison: ComparisonMetrics?
    ): String {
        return buildString {
            append("This week: ₹${total.setScale(0, RoundingMode.HALF_UP)} spent")
            if (count > 0) append(" ($count transactions)")
            comparison?.let {
                val direction = if (it.changePercent > 0) "up" else "down"
                append(". ${it.changePercent.toInt().let { kotlin.math.abs(it) }}% $direction from last week")
            }
            categories.firstOrNull()?.let { append(". Most spent on: ${it.category.displayName}") }
            append(".")
        }
    }

    private fun generateMonthlyNarrative(
        total: BigDecimal,
        categories: List<CategorySummary>,
        budgetPerformance: List<BudgetPerformance>
    ): String {
        val exceeded = budgetPerformance.count { it.status == BudgetStatus.EXCEEDED }
        return buildString {
            append("Monthly summary: ₹${total.setScale(0, RoundingMode.HALF_UP)} total spending")
            categories.firstOrNull()?.let { append(". Top category: ${it.category.displayName} (${it.percentage.toInt()}%)") }
            if (exceeded > 0) {
                append(". ⚠️ $exceeded budget${if (exceeded > 1) "s" else ""} exceeded")
            }
            append(".")
        }
    }

    private fun TransactionEntity.toDomainModel(): Transaction {
        return Transaction(
            id = id,
            amount = amount.toBigDecimal(),
            currency = currency,
            merchantName = merchantName,
            merchantRaw = merchantName,
            category = try { Category.valueOf(category) } catch (e: Exception) { Category.OTHER },
            subcategory = subcategory,
            timestamp = Instant.ofEpochMilli(timestamp),
            source = try { TransactionSource.valueOf(source) } catch (e: Exception) { TransactionSource.NOTIFICATION },
            categoryConfidence = categoryConfidence,
            categorySource = try { CategorySource.valueOf(categorySource) } catch (e: Exception) { CategorySource.UNKNOWN },
            rawNotificationText = rawNotificationText,
            notes = notes
        )
    }

    private fun createPlaceholderTransaction(): Transaction {
        return Transaction(
            id = "placeholder",
            amount = BigDecimal.ZERO,
            currency = "INR",
            merchantName = "N/A",
            merchantRaw = "N/A",
            category = Category.OTHER,
            timestamp = Instant.now(),
            source = TransactionSource.MANUAL
        )
    }
}
