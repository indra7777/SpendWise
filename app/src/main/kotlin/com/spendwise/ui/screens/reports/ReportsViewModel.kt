package com.spendwise.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.agents.reporting.ReportingAgent
import com.spendwise.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class ReportPeriod(val displayName: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    HALF_YEARLY("Half-Yearly"),
    ANNUAL("Annual")
}

data class ReportsUiState(
    val selectedPeriod: ReportPeriod = ReportPeriod.MONTHLY,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentReport: FinancialReport? = null,

    // Common report data
    val totalSpent: Double = 0.0,
    val transactionCount: Int = 0,
    val narrativeSummary: String = "",
    val comparison: ComparisonMetrics? = null,
    val categoryBreakdown: List<CategorySummary> = emptyList(),
    val topMerchants: List<MerchantSummary> = emptyList(),
    val insights: List<SpendingInsight> = emptyList(),
    val trends: List<SpendingTrend> = emptyList(),
    val budgetPerformance: List<BudgetPerformance> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val achievements: List<Achievement> = emptyList()
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportingAgent: ReportingAgent
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReport()
    }

    fun selectPeriod(period: ReportPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        loadReport()
    }

    fun loadReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                when (_uiState.value.selectedPeriod) {
                    ReportPeriod.DAILY -> loadDailyReport()
                    ReportPeriod.WEEKLY -> loadWeeklyReport()
                    ReportPeriod.MONTHLY -> loadMonthlyReport()
                    ReportPeriod.QUARTERLY -> loadQuarterlyReport()
                    ReportPeriod.HALF_YEARLY -> loadHalfYearlyReport()
                    ReportPeriod.ANNUAL -> loadAnnualReport()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load report"
                    )
                }
            }
        }
    }

    private suspend fun loadDailyReport() {
        val report = reportingAgent.generateDailyReport()
        _uiState.update {
            it.copy(
                isLoading = false,
                currentReport = report,
                totalSpent = report.totalSpent.toDouble(),
                transactionCount = report.transactionCount,
                narrativeSummary = report.narrativeSummary,
                comparison = report.comparisonToAverage,
                categoryBreakdown = report.topCategories,
                topMerchants = emptyList(),
                insights = emptyList(),
                trends = emptyList(),
                budgetPerformance = emptyList(),
                recommendations = emptyList(),
                achievements = emptyList()
            )
        }
    }

    private suspend fun loadWeeklyReport() {
        val report = reportingAgent.generateWeeklyReport()
        _uiState.update {
            it.copy(
                isLoading = false,
                currentReport = report,
                totalSpent = report.totalSpent.toDouble(),
                transactionCount = report.dailyBreakdown.sumOf { it.transactionCount },
                narrativeSummary = report.narrativeSummary,
                comparison = report.weekOverWeekComparison,
                categoryBreakdown = report.categoryBreakdown,
                topMerchants = report.topMerchants,
                insights = report.insights,
                trends = emptyList(),
                budgetPerformance = emptyList(),
                recommendations = emptyList(),
                achievements = emptyList()
            )
        }
    }

    private suspend fun loadMonthlyReport() {
        val report = reportingAgent.generateMonthlyReport()
        _uiState.update {
            it.copy(
                isLoading = false,
                currentReport = report,
                totalSpent = report.totalSpent.toDouble(),
                transactionCount = report.categoryBreakdown.sumOf { it.transactionCount },
                narrativeSummary = report.narrativeSummary,
                comparison = null,
                categoryBreakdown = report.categoryBreakdown,
                topMerchants = emptyList(),
                insights = emptyList(),
                trends = report.trends,
                budgetPerformance = report.budgetPerformance,
                recommendations = report.recommendations,
                achievements = emptyList()
            )
        }
    }

    private suspend fun loadQuarterlyReport() {
        val report = reportingAgent.generateQuarterlyReport()
        _uiState.update {
            it.copy(
                isLoading = false,
                currentReport = report,
                totalSpent = report.totalSpent.toDouble(),
                transactionCount = report.monthlyBreakdown.sumOf { it.transactionCount },
                narrativeSummary = report.narrativeSummary,
                comparison = report.quarterOverQuarterComparison,
                categoryBreakdown = emptyList(),
                topMerchants = emptyList(),
                insights = report.insights,
                trends = emptyList(),
                budgetPerformance = emptyList(),
                recommendations = emptyList(),
                achievements = emptyList()
            )
        }
    }

    private suspend fun loadHalfYearlyReport() {
        val report = reportingAgent.generateHalfYearlyReport()
        _uiState.update {
            it.copy(
                isLoading = false,
                currentReport = report,
                totalSpent = report.totalSpent.toDouble(),
                transactionCount = report.monthlyBreakdown.sumOf { it.transactionCount },
                narrativeSummary = report.narrativeSummary,
                comparison = null,
                categoryBreakdown = emptyList(),
                topMerchants = emptyList(),
                insights = emptyList(),
                trends = emptyList(),
                budgetPerformance = emptyList(),
                recommendations = report.recommendations,
                achievements = emptyList()
            )
        }
    }

    private suspend fun loadAnnualReport() {
        val report = reportingAgent.generateAnnualReport()
        _uiState.update {
            it.copy(
                isLoading = false,
                currentReport = report,
                totalSpent = report.totalSpent.toDouble(),
                transactionCount = report.monthlyBreakdown.sumOf { it.transactionCount },
                narrativeSummary = report.narrativeSummary,
                comparison = report.yearOverYearComparison,
                categoryBreakdown = report.categoryBreakdown,
                topMerchants = report.topMerchants,
                insights = emptyList(),
                trends = emptyList(),
                budgetPerformance = emptyList(),
                recommendations = emptyList(),
                achievements = report.achievements
            )
        }
    }
}
