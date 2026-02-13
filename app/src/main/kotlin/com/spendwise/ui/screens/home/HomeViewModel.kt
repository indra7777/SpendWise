package com.spendwise.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Observe changes to the transaction table to refresh dashboard automatically
        viewModelScope.launch {
            repository.getAllTransactions().collect {
                loadDashboardData()
            }
        }
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            // Don't show loading state on updates to avoid flickering
            // _uiState.value = _uiState.value.copy(isLoading = true) 

            try {
                val now = System.currentTimeMillis()
                val startOfToday = TransactionRepository.getStartOfToday()
                val endOfToday = TransactionRepository.getEndOfToday()
                val startOfWeek = TransactionRepository.getStartOfWeek()
                val startOfMonth = TransactionRepository.getStartOfMonth()
                val startOfLastMonth = TransactionRepository.getStartOfLastMonth()
                val endOfLastMonth = TransactionRepository.getEndOfLastMonth()

                // Get spending data (expenses are negative, income is positive)
                val totalThisMonth = repository.getTotalSpent(startOfMonth, now)
                val totalLastMonth = repository.getTotalSpent(startOfLastMonth, endOfLastMonth)
                val todaySpent = repository.getTotalSpent(startOfToday, endOfToday)
                val weekSpent = repository.getTotalSpent(startOfWeek, now)
                val totalIncomeThisMonth = repository.getTotalIncome(startOfMonth, now)

                // Calculate change percentage
                val changePercent = if (totalLastMonth > 0) {
                    ((totalThisMonth - totalLastMonth) / totalLastMonth * 100).toFloat()
                } else {
                    0f
                }

                // Get budget
                val monthlyBudget = repository.getMonthlyBudget()
                val budgetPercent = if (monthlyBudget > 0) {
                    (totalThisMonth / monthlyBudget * 100).toFloat()
                } else {
                    0f
                }

                // Get category breakdown
                val categorySummary = repository.getCategorySummary(startOfMonth, now).first()
                val totalForCategories = categorySummary.sumOf { it.total }
                val categoryBreakdown = categorySummary.mapNotNull { result ->
                    try {
                        val category = Category.valueOf(result.category)
                        val percentage = if (totalForCategories > 0) {
                            (result.total / totalForCategories * 100).toFloat()
                        } else 0f
                        CategoryData(category, result.total, percentage)
                    } catch (e: Exception) {
                        null
                    }
                }

                // Get recent transactions
                val recentEntities = repository.getRecentTransactions(5)
                val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
                    .withZone(ZoneId.systemDefault())
                val recentTransactions = recentEntities.mapNotNull { entity ->
                    try {
                        val category = Category.valueOf(entity.category)
                        val formattedTime = formatRelativeTime(entity.timestamp)
                        RecentTransaction(
                            merchantName = entity.merchantName,
                            category = category,
                            amount = entity.amount,
                            formattedTime = formattedTime
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // Get insights
                val insightEntities = repository.getRecentInsights(2)
                val insights = insightEntities.map { entity ->
                    Insight(
                        title = entity.title,
                        description = entity.description,
                        type = entity.type
                    )
                }

                // Get pending reviews
                val pendingReviews = repository.getPendingReviewCount()

                _uiState.value = HomeUiState(
                    isLoading = false,
                    totalSpentThisMonth = totalThisMonth,
                    totalIncomeThisMonth = totalIncomeThisMonth,
                    changeFromLastMonth = changePercent,
                    todaySpent = todaySpent,
                    weekSpent = weekSpent,
                    monthlyBudget = monthlyBudget,
                    budgetPercentUsed = budgetPercent,
                    categoryBreakdown = categoryBreakdown,
                    recentTransactions = recentTransactions,
                    insights = insights,
                    pendingReviews = pendingReviews,
                    hasData = recentEntities.isNotEmpty()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 7 -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d")
                    .withZone(ZoneId.systemDefault())
                formatter.format(Instant.ofEpochMilli(timestamp))
            }
            days > 1 -> "${days.toInt()} days ago"
            days == 1L -> "Yesterday"
            hours > 1 -> "${hours.toInt()} hours ago"
            hours == 1L -> "1 hour ago"
            minutes > 1 -> "${minutes.toInt()} mins ago"
            else -> "Just now"
        }
    }

    fun refresh() {
        loadDashboardData()
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val totalSpentThisMonth: Double = 0.0,
    val totalIncomeThisMonth: Double = 0.0,
    val changeFromLastMonth: Float = 0f,
    val todaySpent: Double = 0.0,
    val weekSpent: Double = 0.0,
    val monthlyBudget: Double = 0.0,
    val budgetPercentUsed: Float = 0f,
    val categoryBreakdown: List<CategoryData> = emptyList(),
    val recentTransactions: List<RecentTransaction> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val pendingReviews: Int = 0,
    val error: String? = null,
    val hasData: Boolean = false
)

data class CategoryData(
    val category: Category,
    val amount: Double,
    val percentage: Float
)

data class RecentTransaction(
    val merchantName: String,
    val category: Category,
    val amount: Double,
    val formattedTime: String
)

data class Insight(
    val title: String,
    val description: String,
    val type: String
)
