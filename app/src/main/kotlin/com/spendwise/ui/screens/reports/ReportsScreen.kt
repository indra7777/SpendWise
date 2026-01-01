package com.spendwise.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.domain.model.*
import com.spendwise.ui.theme.FinanceColors
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Text(
                text = "Reports",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Analyze your spending patterns",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Period Selector
        item {
            ReportPeriodSelector(
                selectedPeriod = uiState.selectedPeriod,
                onPeriodSelected = { viewModel.selectPeriod(it) }
            )
        }

        // Loading or Content
        when {
            uiState.isLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = FinanceColors.Primary)
                    }
                }
            }
            uiState.error != null -> {
                item {
                    ErrorCard(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadReport() }
                    )
                }
            }
            else -> {
                // Period Summary
                item {
                    PeriodSummaryCard(
                        period = uiState.selectedPeriod,
                        totalSpent = uiState.totalSpent,
                        transactionCount = uiState.transactionCount,
                        narrative = uiState.narrativeSummary,
                        comparison = uiState.comparison,
                        currencyFormat = currencyFormat
                    )
                }

                // Category Breakdown
                if (uiState.categoryBreakdown.isNotEmpty()) {
                    item {
                        CategoryBreakdownCard(
                            categories = uiState.categoryBreakdown,
                            currencyFormat = currencyFormat
                        )
                    }
                }

                // Top Merchants
                if (uiState.topMerchants.isNotEmpty()) {
                    item {
                        TopMerchantsCard(
                            merchants = uiState.topMerchants,
                            currencyFormat = currencyFormat
                        )
                    }
                }

                // Insights
                if (uiState.insights.isNotEmpty()) {
                    item {
                        InsightsCard(insights = uiState.insights)
                    }
                }

                // Trends
                if (uiState.trends.isNotEmpty()) {
                    item {
                        TrendsCard(trends = uiState.trends)
                    }
                }

                // Budget Performance
                if (uiState.budgetPerformance.isNotEmpty()) {
                    item {
                        BudgetPerformanceCard(
                            performance = uiState.budgetPerformance,
                            currencyFormat = currencyFormat
                        )
                    }
                }

                // Recommendations
                if (uiState.recommendations.isNotEmpty()) {
                    item {
                        RecommendationsCard(recommendations = uiState.recommendations)
                    }
                }

                // Achievements
                if (uiState.achievements.isNotEmpty()) {
                    item {
                        AchievementsCard(achievements = uiState.achievements)
                    }
                }

                // AI Insights Section
                item {
                    AIInsightsCard()
                }
            }
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportPeriodSelector(
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ReportPeriod.entries) { period ->
            FilterChip(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FinanceColors.Primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun PeriodSummaryCard(
    period: ReportPeriod,
    totalSpent: Double,
    transactionCount: Int,
    narrative: String,
    comparison: ComparisonMetrics?,
    currencyFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1F2937), Color(0xFF111827))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${period.displayName} Report",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = currencyFormat.format(totalSpent),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$transactionCount transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )

                    comparison?.let { comp ->
                        Spacer(modifier = Modifier.width(16.dp))
                        val isIncrease = comp.direction == TrendDirection.INCREASING
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isIncrease) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (isIncrease) FinanceColors.Negative else FinanceColors.Positive,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${String.format("%.1f", kotlin.math.abs(comp.changePercent))}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isIncrease) FinanceColors.Negative else FinanceColors.Positive
                            )
                        }
                    }
                }

                if (narrative.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = narrative,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    categories: List<CategorySummary>,
    currencyFormat: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "By Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            categories.take(6).forEach { category ->
                CategoryRow(category = category, currencyFormat = currencyFormat)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategorySummary,
    currencyFormat: NumberFormat
) {
    val categoryColor = try {
        Color(android.graphics.Color.parseColor(category.category.colorHex))
    } catch (e: Exception) {
        FinanceColors.Primary
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(categoryColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.category.displayName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currencyFormat.format(category.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${String.format("%.1f", category.percentage)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = category.percentage / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = categoryColor,
            trackColor = Color.LightGray.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun TopMerchantsCard(
    merchants: List<MerchantSummary>,
    currencyFormat: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Top Merchants",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            merchants.take(5).forEachIndexed { index, merchant ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.LightGray.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = merchant.merchantName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${merchant.transactionCount} transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Text(
                        text = currencyFormat.format(merchant.totalAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (index < merchants.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: List<SpendingInsight>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            insights.forEach { insight ->
                InsightItem(insight = insight)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InsightItem(insight: SpendingInsight) {
    val (icon, color) = when (insight.type) {
        InsightType.WARNING -> Icons.Default.Warning to FinanceColors.Warning
        InsightType.ACHIEVEMENT -> Icons.Default.EmojiEvents to FinanceColors.Positive
        InsightType.TREND -> Icons.Default.TrendingUp to FinanceColors.Info
        InsightType.ANOMALY -> Icons.Default.Error to FinanceColors.Negative
        InsightType.OPPORTUNITY -> Icons.Default.Star to Color(0xFFF59E0B)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = insight.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            insight.action?.let { action ->
                Text(
                    text = action,
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceColors.Primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TrendsCard(trends: List<SpendingTrend>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Spending Trends",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            trends.forEach { trend ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (trend.direction) {
                            TrendDirection.INCREASING -> Icons.Default.TrendingUp
                            TrendDirection.DECREASING -> Icons.Default.TrendingDown
                            TrendDirection.STABLE -> Icons.Default.TrendingFlat
                        },
                        contentDescription = null,
                        tint = when (trend.direction) {
                            TrendDirection.INCREASING -> FinanceColors.Negative
                            TrendDirection.DECREASING -> FinanceColors.Positive
                            TrendDirection.STABLE -> Color.Gray
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = trend.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BudgetPerformanceCard(
    performance: List<BudgetPerformance>,
    currencyFormat: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Budget Performance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            performance.forEach { bp ->
                val statusColor = when (bp.status) {
                    BudgetStatus.UNDER_BUDGET -> FinanceColors.Positive
                    BudgetStatus.ON_TRACK -> FinanceColors.Info
                    BudgetStatus.WARNING -> FinanceColors.Warning
                    BudgetStatus.EXCEEDED -> FinanceColors.Negative
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bp.category.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${currencyFormat.format(bp.spentAmount)} / ${currencyFormat.format(bp.budgetAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = (bp.percentUsed / 100f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = statusColor,
                        trackColor = Color.LightGray.copy(alpha = 0.3f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RecommendationsCard(recommendations: List<Recommendation>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TipsAndUpdates,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            recommendations.forEach { rec ->
                Column {
                    Text(
                        text = rec.area,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = rec.suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                    Text(
                        text = rec.potentialImpact,
                        style = MaterialTheme.typography.bodySmall,
                        color = FinanceColors.Positive,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AchievementsCard(achievements: List<Achievement>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF4FF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFA855F7)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            achievements.forEach { achievement ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = achievement.icon,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = achievement.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = achievement.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AIInsightsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = FinanceColors.Info.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = FinanceColors.Info
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI-Powered Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Get personalized recommendations based on your spending patterns, powered by AI.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { /* Generate insights */ },
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Info)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Insights")
            }
        }
    }
}

@Composable
private fun ErrorCard(
    error: String,
    onRetry: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = FinanceColors.Negative,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

