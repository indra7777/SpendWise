package com.spendwise.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    // TODO: Inject repositories
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            // TODO: Load real data from repository
            // For now, using placeholder data
            _uiState.value = HomeUiState(
                totalSpentThisMonth = 24500.0,
                changeFromLastMonth = 12f,
                todaySpent = 450.0,
                weekSpent = 3200.0,
                monthlyBudget = 40000.0,
                budgetPercentUsed = 61.25f,
                categoryBreakdown = listOf(
                    CategoryData(Category.FOOD, 8200.0, 33.5f),
                    CategoryData(Category.SHOPPING, 5100.0, 20.8f),
                    CategoryData(Category.TRANSPORT, 3400.0, 13.9f),
                    CategoryData(Category.ENTERTAINMENT, 2800.0, 11.4f),
                    CategoryData(Category.UTILITIES, 2500.0, 10.2f),
                    CategoryData(Category.OTHER, 2500.0, 10.2f)
                ),
                recentTransactions = listOf(
                    RecentTransaction("Swiggy", Category.FOOD, 250.0, "Today, 2:30 PM"),
                    RecentTransaction("Amazon", Category.SHOPPING, 1299.0, "Today, 11:45 AM"),
                    RecentTransaction("Uber", Category.TRANSPORT, 180.0, "Yesterday"),
                    RecentTransaction("Netflix", Category.ENTERTAINMENT, 649.0, "Dec 28"),
                    RecentTransaction("Big Bazaar", Category.GROCERIES, 2340.0, "Dec 27")
                ),
                insights = listOf(
                    Insight(
                        title = "Food spending up 25%",
                        description = "You've spent more on food this week compared to your average. Consider cooking at home more often.",
                        type = "warning"
                    ),
                    Insight(
                        title = "Great job on transport!",
                        description = "Your transport spending is 15% lower than last month. Keep it up!",
                        type = "achievement"
                    )
                )
            )
        }
    }

    fun refresh() {
        loadDashboardData()
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val totalSpentThisMonth: Double = 0.0,
    val changeFromLastMonth: Float = 0f,
    val todaySpent: Double = 0.0,
    val weekSpent: Double = 0.0,
    val monthlyBudget: Double = 0.0,
    val budgetPercentUsed: Float = 0f,
    val categoryBreakdown: List<CategoryData> = emptyList(),
    val recentTransactions: List<RecentTransaction> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val error: String? = null
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
