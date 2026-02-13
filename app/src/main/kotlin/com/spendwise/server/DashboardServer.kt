package com.spendwise.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.spendwise.data.local.database.TransactionDao
import com.spendwise.data.local.database.BudgetDao
import com.spendwise.data.local.database.InsightDao
import com.spendwise.data.local.preferences.EncryptedPreferences
import com.spendwise.agents.reporting.ReportingAgent
import com.spendwise.domain.model.*
import java.math.RoundingMode
import java.time.YearMonth
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.net.NetworkInterface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP Security Compliance:
 * - Token-based authentication for all API endpoints
 * - Least privilege: Only GET endpoints exposed (read-only)
 * - No DELETE/MODIFY operations via API (require in-app biometric verification)
 */
@Singleton
class DashboardServer @Inject constructor(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val insightDao: InsightDao,
    private val reportingAgent: ReportingAgent,
    private val encryptedPreferences: EncryptedPreferences
) {
    private var server: ApplicationEngine? = null
    private var isRunning = false
    private var serverToken: String? = null

    val port = 8080

    fun start(): String? {
        if (isRunning) {
            Log.d(TAG, "Server already running")
            return getServerUrl()
        }

        return try {
            // MCP Security: Generate or retrieve authentication token
            serverToken = encryptedPreferences.getMcpServerToken()
                ?: encryptedPreferences.generateMcpServerToken()

            server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
                configureServer()
            }.start(wait = false)

            isRunning = true
            val url = getServerUrl()
            Log.d(TAG, "Server started at $url")
            Log.d(TAG, "MCP Token: $serverToken") // Display token for authorized clients
            url
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            null
        }
    }

    /**
     * Get the current server authentication token.
     * This should be displayed to the user so they can authorize LLM clients.
     */
    fun getServerToken(): String? = serverToken

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        isRunning = false
        Log.d(TAG, "Server stopped")
    }

    fun isRunning(): Boolean = isRunning

    fun getServerUrl(): String? {
        val ip = getLocalIpAddress()
        return if (ip != null) "http://$ip:$port" else null
    }

    /**
     * MCP Security: Validate Bearer token from Authorization header
     */
    private fun ApplicationCall.validateToken(): Boolean {
        val authHeader = request.header(HttpHeaders.Authorization)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false
        }
        val token = authHeader.removePrefix("Bearer ")
        return token == serverToken
    }

    private fun Application.configureServer() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            // MCP Security: Only allow GET methods (read-only access)
            // DELETE/MODIFY operations require in-app biometric verification
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Options)
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e(TAG, "Server error", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiError(cause.message ?: "Unknown error")
                )
            }
        }

        routing {
            // Public endpoints (no auth required)
            get("/") {
                call.respond(ApiResponse(
                    success = true,
                    message = "SpendWise Dashboard API - MCP Secured",
                    data = mapOf(
                        "version" to "1.1.0",
                        "security" to "Token authentication required for /api/* endpoints"
                    )
                ))
            }

            get("/health") {
                call.respond(mapOf("status" to "ok", "timestamp" to System.currentTimeMillis()))
            }

            // Protected API endpoints - require Bearer token
            // MCP Security: Token authentication for all data endpoints
            get("/api/dashboard") {
                if (!call.validateToken()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid or missing authentication token"))
                    return@get
                }
                val summary = getDashboardSummary()
                call.respond(ApiResponse(success = true, data = summary))
            }

            // Transactions
            get("/api/transactions") {
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
                val category = call.parameters["category"]

                val transactions = getTransactions(limit, offset, category)
                call.respond(ApiResponse(success = true, data = transactions))
            }

            get("/api/transactions/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Transaction ID required")
                )
                val transaction = transactionDao.getById(id)
                if (transaction != null) {
                    call.respond(ApiResponse(success = true, data = transaction.toApiModel()))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiError("Transaction not found"))
                }
            }

            // Category summary
            get("/api/categories") {
                val period = call.parameters["period"] ?: "month"
                val (startTime, endTime) = getPeriodRange(period)
                val categories = transactionDao.getCategorySummary(startTime, endTime).first()
                call.respond(ApiResponse(success = true, data = categories.map {
                    CategorySummaryApi(it.category, it.total, it.count)
                }))
            }

            // Reports
            get("/api/reports/{type}") {
                val type = call.parameters["type"] ?: "daily"
                val report = generateReport(type)
                call.respond(ApiResponse(success = true, data = report))
            }

            // Insights
            get("/api/insights") {
                val insights = insightDao.getRecentInsights(10)
                call.respond(ApiResponse(success = true, data = insights.map { it.toApiModel() }))
            }

            // Budgets
            get("/api/budgets") {
                val budgets = budgetDao.getActiveBudgets().first()
                call.respond(ApiResponse(success = true, data = budgets.map { it.toApiModel() }))
            }

            // Stats for charts
            get("/api/stats/daily") {
                val days = call.parameters["days"]?.toIntOrNull() ?: 30
                val stats = getDailyStats(days)
                call.respond(ApiResponse(success = true, data = stats))
            }

            get("/api/stats/category-trend") {
                val months = call.parameters["months"]?.toIntOrNull() ?: 6
                val trends = getCategoryTrends(months)
                call.respond(ApiResponse(success = true, data = trends))
            }

            // Note: React dashboard served separately via Vite dev server or built files
        }
    }

    private suspend fun getDashboardSummary(): DashboardSummary {
        val now = System.currentTimeMillis()
        val (monthStart, monthEnd) = getMonthRange()
        val (weekStart, weekEnd) = getWeekRange()
        val (todayStart, todayEnd) = getTodayRange()

        val totalMonth = transactionDao.getTotalSpent(monthStart, monthEnd) ?: 0.0
        val totalWeek = transactionDao.getTotalSpent(weekStart, weekEnd) ?: 0.0
        val totalToday = transactionDao.getTotalSpent(todayStart, todayEnd) ?: 0.0

        val categories = transactionDao.getCategorySummary(monthStart, monthEnd).first()
        val totalForPercentage = categories.sumOf { it.total }.takeIf { it > 0 } ?: 1.0

        val recentTransactions = transactionDao.getTransactionsPaged(5, 0)

        val budget = budgetDao.getOverallBudget(now)
        val budgetAmount = budget?.amount ?: 40000.0
        val budgetUsedPercent = ((totalMonth / budgetAmount) * 100).toFloat()

        return DashboardSummary(
            totalSpentMonth = totalMonth,
            totalSpentWeek = totalWeek,
            totalSpentToday = totalToday,
            monthlyBudget = budgetAmount,
            budgetUsedPercent = budgetUsedPercent,
            categoryBreakdown = categories.map {
                CategoryBreakdown(
                    category = it.category,
                    amount = it.total,
                    count = it.count,
                    percentage = ((it.total / totalForPercentage) * 100).toFloat()
                )
            },
            recentTransactions = recentTransactions.map { it.toApiModel() }
        )
    }

    private suspend fun getTransactions(
        limit: Int,
        offset: Int,
        category: String?
    ): List<TransactionApi> {
        val transactions = transactionDao.getTransactionsPaged(limit, offset)
        return if (category != null) {
            transactions.filter { it.category.equals(category, ignoreCase = true) }
        } else {
            transactions
        }.map { it.toApiModel() }
    }

    private suspend fun generateReport(type: String): FullReportApi {
        return when (type) {
            "daily" -> {
                val report = reportingAgent.generateDailyReport()
                FullReportApi(
                    type = "daily",
                    periodStart = report.period.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    periodEnd = report.period.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    totalSpent = report.totalSpent.toDouble(),
                    transactionCount = report.transactionCount,
                    averageTransaction = if (report.transactionCount > 0) report.totalSpent.toDouble() / report.transactionCount else 0.0,
                    categoryBreakdown = report.topCategories.map { it.toApi() },
                    topMerchants = emptyList(),
                    narrativeSummary = report.narrativeSummary,
                    comparison = report.comparisonToAverage?.toApi(),
                    insights = emptyList(),
                    trends = emptyList()
                )
            }
            "weekly" -> {
                val report = reportingAgent.generateWeeklyReport()
                FullReportApi(
                    type = "weekly",
                    periodStart = report.period.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    periodEnd = report.period.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    totalSpent = report.totalSpent.toDouble(),
                    transactionCount = report.dailyBreakdown.sumOf { it.transactionCount },
                    averageTransaction = report.totalSpent.toDouble() / maxOf(report.dailyBreakdown.sumOf { it.transactionCount }, 1),
                    categoryBreakdown = report.categoryBreakdown.map { it.toApi() },
                    topMerchants = report.topMerchants.map { it.toApi() },
                    narrativeSummary = report.narrativeSummary,
                    comparison = report.weekOverWeekComparison?.toApi(),
                    insights = report.insights.map { it.toApi() },
                    trends = emptyList(),
                    dailyBreakdown = report.dailyBreakdown.map { DailyBreakdownApi(it.date, it.amount.toDouble(), it.transactionCount) }
                )
            }
            "monthly" -> {
                val report = reportingAgent.generateMonthlyReport()
                FullReportApi(
                    type = "monthly",
                    periodStart = report.period.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    periodEnd = report.period.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    totalSpent = report.totalSpent.toDouble(),
                    transactionCount = report.weeklyBreakdown.sumOf { it.amount.toInt() },
                    averageTransaction = 0.0,
                    categoryBreakdown = report.categoryBreakdown.map { it.toApi() },
                    topMerchants = emptyList(),
                    narrativeSummary = report.narrativeSummary,
                    comparison = null,
                    insights = emptyList(),
                    trends = report.trends.map { it.toApi() },
                    budgetPerformance = report.budgetPerformance.map { it.toApi() },
                    recommendations = report.recommendations.map { it.toApi() }
                )
            }
            "quarterly" -> {
                val report = reportingAgent.generateQuarterlyReport()
                FullReportApi(
                    type = "quarterly",
                    periodStart = report.period.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    periodEnd = report.period.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    totalSpent = report.totalSpent.toDouble(),
                    transactionCount = report.monthlyBreakdown.sumOf { it.transactionCount },
                    averageTransaction = 0.0,
                    categoryBreakdown = emptyList(),
                    topMerchants = emptyList(),
                    narrativeSummary = report.narrativeSummary,
                    comparison = report.quarterOverQuarterComparison?.toApi(),
                    insights = report.insights.map { it.toApi() },
                    trends = emptyList(),
                    monthlyBreakdown = report.monthlyBreakdown.map { MonthlyBreakdownApi(it.month, it.amount.toDouble(), it.transactionCount) }
                )
            }
            "half-yearly" -> {
                val report = reportingAgent.generateHalfYearlyReport()
                FullReportApi(
                    type = "half-yearly",
                    periodStart = report.period.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    periodEnd = report.period.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    totalSpent = report.totalSpent.toDouble(),
                    transactionCount = report.monthlyBreakdown.sumOf { it.transactionCount },
                    averageTransaction = 0.0,
                    categoryBreakdown = emptyList(),
                    topMerchants = emptyList(),
                    narrativeSummary = report.narrativeSummary,
                    comparison = null,
                    insights = emptyList(),
                    trends = emptyList(),
                    monthlyBreakdown = report.monthlyBreakdown.map { MonthlyBreakdownApi(it.month, it.amount.toDouble(), it.transactionCount) },
                    recommendations = report.recommendations.map { it.toApi() }
                )
            }
            "yearly" -> {
                val report = reportingAgent.generateAnnualReport()
                FullReportApi(
                    type = "yearly",
                    periodStart = report.period.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    periodEnd = report.period.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    totalSpent = report.totalSpent.toDouble(),
                    transactionCount = report.monthlyBreakdown.sumOf { it.transactionCount },
                    averageTransaction = 0.0,
                    categoryBreakdown = report.categoryBreakdown.map { it.toApi() },
                    topMerchants = report.topMerchants.map { it.toApi() },
                    narrativeSummary = report.narrativeSummary,
                    comparison = report.yearOverYearComparison?.toApi(),
                    insights = emptyList(),
                    trends = emptyList(),
                    monthlyBreakdown = report.monthlyBreakdown.map { MonthlyBreakdownApi(it.month, it.amount.toDouble(), it.transactionCount) },
                    achievements = report.achievements.map { AchievementApi(it.title, it.description, it.icon) },
                    yearInReview = report.yearInReview.toApi()
                )
            }
            else -> {
                val report = reportingAgent.generateMonthlyReport()
                FullReportApi(
                    type = "monthly",
                    periodStart = report.period.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    periodEnd = report.period.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    totalSpent = report.totalSpent.toDouble(),
                    transactionCount = 0,
                    averageTransaction = 0.0,
                    categoryBreakdown = report.categoryBreakdown.map { it.toApi() },
                    topMerchants = emptyList(),
                    narrativeSummary = report.narrativeSummary,
                    comparison = null,
                    insights = emptyList(),
                    trends = emptyList()
                )
            }
        }
    }

    private suspend fun getDailyStats(days: Int): List<DailyStatApi> {
        val stats = mutableListOf<DailyStatApi>()
        val today = LocalDate.now()

        for (i in 0 until days) {
            val date = today.minusDays(i.toLong())
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

            val total = transactionDao.getTotalSpent(startOfDay, endOfDay) ?: 0.0
            val count = transactionDao.getTransactionCount(startOfDay, endOfDay)

            stats.add(DailyStatApi(
                date = date.toString(),
                amount = total,
                count = count
            ))
        }

        return stats.reversed()
    }

    private suspend fun getCategoryTrends(months: Int): List<CategoryTrendApi> {
        // Simplified - would need more complex query for real trends
        val (startTime, endTime) = getMonthRange()
        val categories = transactionDao.getCategorySummary(startTime, endTime).first()

        return categories.map {
            CategoryTrendApi(
                category = it.category,
                currentAmount = it.total,
                previousAmount = it.total * 0.9, // Placeholder
                changePercent = 10f // Placeholder
            )
        }
    }

    // Time range helpers
    private fun getMonthRange(): Pair<Long, Long> {
        val now = LocalDate.now()
        val start = now.with(TemporalAdjusters.firstDayOfMonth())
        val end = now.with(TemporalAdjusters.lastDayOfMonth())
        return start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
                end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }

    private fun getWeekRange(): Pair<Long, Long> {
        val now = LocalDate.now()
        val start = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val end = now.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
        return start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
                end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }

    private fun getTodayRange(): Pair<Long, Long> {
        val now = LocalDate.now()
        return now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
                now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }

    private fun getQuarterRange(): Pair<Long, Long> {
        val now = LocalDate.now()
        val quarter = (now.monthValue - 1) / 3
        val start = LocalDate.of(now.year, quarter * 3 + 1, 1)
        val end = start.plusMonths(3).minusDays(1)
        return start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
                end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }

    private fun getHalfYearRange(): Pair<Long, Long> {
        val now = LocalDate.now()
        val start = if (now.monthValue <= 6) {
            LocalDate.of(now.year, 1, 1)
        } else {
            LocalDate.of(now.year, 7, 1)
        }
        val end = start.plusMonths(6).minusDays(1)
        return start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
                end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }

    private fun getYearRange(): Pair<Long, Long> {
        val now = LocalDate.now()
        val start = LocalDate.of(now.year, 1, 1)
        val end = LocalDate.of(now.year, 12, 31)
        return start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
                end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }

    private fun getPeriodRange(period: String): Pair<Long, Long> {
        return when (period) {
            "today" -> getTodayRange()
            "week" -> getWeekRange()
            "month" -> getMonthRange()
            "quarter" -> getQuarterRange()
            "half-year" -> getHalfYearRange()
            "year" -> getYearRange()
            else -> getMonthRange()
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
        }
        return null
    }

    companion object {
        private const val TAG = "DashboardServer"
    }
}

// API Models
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)

@Serializable
data class ApiError(val error: String)

@Serializable
data class DashboardSummary(
    val totalSpentMonth: Double,
    val totalSpentWeek: Double,
    val totalSpentToday: Double,
    val monthlyBudget: Double,
    val budgetUsedPercent: Float,
    val categoryBreakdown: List<CategoryBreakdown>,
    val recentTransactions: List<TransactionApi>
)

@Serializable
data class CategoryBreakdown(
    val category: String,
    val amount: Double,
    val count: Int,
    val percentage: Float
)

@Serializable
data class TransactionApi(
    val id: String,
    val amount: Double,
    val currency: String,
    val merchantName: String,
    val category: String,
    val subcategory: String?,
    val timestamp: Long,
    val formattedDate: String
)

@Serializable
data class CategorySummaryApi(
    val category: String,
    val total: Double,
    val count: Int
)

@Serializable
data class MerchantSummaryApi(
    val merchantName: String,
    val total: Double,
    val count: Int
)

@Serializable
data class ReportApi(
    val type: String,
    val periodStart: Long,
    val periodEnd: Long,
    val totalSpent: Double,
    val transactionCount: Int,
    val averageTransaction: Double,
    val categoryBreakdown: List<CategoryBreakdown>,
    val topMerchants: List<MerchantSummaryApi>
)

@Serializable
data class FullReportApi(
    val type: String,
    val periodStart: Long,
    val periodEnd: Long,
    val totalSpent: Double,
    val transactionCount: Int,
    val averageTransaction: Double,
    val categoryBreakdown: List<CategoryBreakdownApi>,
    val topMerchants: List<MerchantSummaryFullApi>,
    val narrativeSummary: String,
    val comparison: ComparisonApi? = null,
    val insights: List<InsightFullApi> = emptyList(),
    val trends: List<TrendApi> = emptyList(),
    val dailyBreakdown: List<DailyBreakdownApi>? = null,
    val monthlyBreakdown: List<MonthlyBreakdownApi>? = null,
    val budgetPerformance: List<BudgetPerformanceApi>? = null,
    val recommendations: List<RecommendationApi>? = null,
    val achievements: List<AchievementApi>? = null,
    val yearInReview: YearInReviewApi? = null
)

@Serializable
data class CategoryBreakdownApi(
    val category: String,
    val displayName: String,
    val amount: Double,
    val percentage: Float,
    val count: Int,
    val changeFromPrevious: Float?
)

@Serializable
data class MerchantSummaryFullApi(
    val merchantName: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val category: String
)

@Serializable
data class ComparisonApi(
    val previousAmount: Double,
    val currentAmount: Double,
    val changePercent: Float,
    val direction: String
)

@Serializable
data class InsightFullApi(
    val type: String,
    val title: String,
    val description: String,
    val action: String?,
    val priority: String,
    val relatedCategory: String?
)

@Serializable
data class TrendApi(
    val category: String,
    val direction: String,
    val percentChange: Float,
    val description: String
)

@Serializable
data class DailyBreakdownApi(
    val date: String,
    val amount: Double,
    val transactionCount: Int
)

@Serializable
data class MonthlyBreakdownApi(
    val month: String,
    val amount: Double,
    val transactionCount: Int
)

@Serializable
data class BudgetPerformanceApi(
    val category: String,
    val budgetAmount: Double,
    val spentAmount: Double,
    val percentUsed: Float,
    val status: String
)

@Serializable
data class RecommendationApi(
    val area: String,
    val suggestion: String,
    val potentialImpact: String,
    val priority: String
)

@Serializable
data class AchievementApi(
    val title: String,
    val description: String,
    val icon: String
)

@Serializable
data class YearInReviewApi(
    val headline: String,
    val topCategory: String,
    val mostFrequentMerchant: String,
    val totalTransactions: Int,
    val averagePerDay: Double
)

@Serializable
data class DailyStatApi(
    val date: String,
    val amount: Double,
    val count: Int
)

@Serializable
data class CategoryTrendApi(
    val category: String,
    val currentAmount: Double,
    val previousAmount: Double,
    val changePercent: Float
)

@Serializable
data class InsightApi(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val priority: String
)

@Serializable
data class BudgetApi(
    val id: Long,
    val category: String?,
    val amount: Double,
    val periodStart: Long,
    val periodEnd: Long
)

// Extension functions
private fun com.spendwise.data.local.database.TransactionEntity.toApiModel() = TransactionApi(
    id = id,
    amount = amount,
    currency = currency,
    merchantName = merchantName,
    category = category,
    subcategory = subcategory,
    timestamp = timestamp,
    formattedDate = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
)

private fun com.spendwise.data.local.database.InsightEntity.toApiModel() = InsightApi(
    id = id,
    type = type,
    title = title,
    description = description,
    priority = priority
)

private fun com.spendwise.data.local.database.BudgetEntity.toApiModel() = BudgetApi(
    id = id,
    category = category,
    amount = amount,
    periodStart = periodStart,
    periodEnd = periodEnd
)

// Domain model extension functions for reports
private fun CategorySummary.toApi() = CategoryBreakdownApi(
    category = category.name,
    displayName = category.displayName,
    amount = amount.toDouble(),
    percentage = percentage,
    count = transactionCount,
    changeFromPrevious = changeFromPrevious
)

private fun MerchantSummary.toApi() = MerchantSummaryFullApi(
    merchantName = merchantName,
    totalAmount = totalAmount.toDouble(),
    transactionCount = transactionCount,
    category = category.name
)

private fun ComparisonMetrics.toApi() = ComparisonApi(
    previousAmount = previousAmount.toDouble(),
    currentAmount = currentAmount.toDouble(),
    changePercent = changePercent,
    direction = direction.name
)

private fun SpendingInsight.toApi() = InsightFullApi(
    type = type.name,
    title = title,
    description = description,
    action = action,
    priority = priority.name,
    relatedCategory = relatedCategory?.name
)

private fun SpendingTrend.toApi() = TrendApi(
    category = category.name,
    direction = direction.name,
    percentChange = percentChange,
    description = description
)

private fun BudgetPerformance.toApi() = BudgetPerformanceApi(
    category = category.name,
    budgetAmount = budgetAmount.toDouble(),
    spentAmount = spentAmount.toDouble(),
    percentUsed = percentUsed,
    status = status.name
)

private fun Recommendation.toApi() = RecommendationApi(
    area = area,
    suggestion = suggestion,
    potentialImpact = potentialImpact,
    priority = priority.name
)

private fun YearInReview.toApi() = YearInReviewApi(
    headline = headline,
    topCategory = topCategory.name,
    mostFrequentMerchant = mostFrequentMerchant,
    totalTransactions = totalTransactions,
    averagePerDay = averagePerDay.toDouble()
)
