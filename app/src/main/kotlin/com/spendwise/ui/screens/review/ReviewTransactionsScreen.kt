package com.spendwise.ui.screens.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.ui.components.ReviewTransactionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewTransactionsScreen(
    onBack: () -> Unit,
    onEditTransaction: (String) -> Unit,
    viewModel: ReviewTransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Transactions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is ReviewUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is ReviewUiState.Empty -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "All caught up!",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "You've reviewed all pending transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onBack) {
                            Text("Go Home")
                        }
                    }
                }
                is ReviewUiState.Error -> {
                    Text("Error: ${state.message}")
                }
                is ReviewUiState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${state.remainingCount} remaining",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        // Animated Content for card transitions
                        AnimatedContent(
                            targetState = state.currentTransaction,
                            transitionSpec = {
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut()
                                )
                            },
                            label = "CardTransition"
                        ) { transaction ->
                            ReviewTransactionCard(
                                transaction = transaction,
                                // Provide smarter suggestions based on current category or common ones
                                suggestedCategories = getSuggestions(transaction.category),
                                onCategorize = { category ->
                                    viewModel.confirmCategory(transaction, category)
                                },
                                onIgnore = {
                                    viewModel.ignoreTransaction(transaction)
                                },
                                onEdit = {
                                    onEditTransaction(transaction.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Simple helper for suggestions
fun getSuggestions(current: String): List<String> {
    val common = listOf("Food", "Shopping", "Transport", "Utilities", "Health")
    return (common - current).take(2) + current
}
