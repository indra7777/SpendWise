package com.rupeelog.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rupeelog.data.local.database.TransactionDao
import com.rupeelog.data.local.database.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewTransactionsViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())

    init {
        loadTransactions()
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            try {
                // Fetch transactions with low confidence (< 0.8) or unreviewed
                // We'll use a threshold of 0.85 for now
                val list = transactionDao.getLowConfidenceTransactions(0.85f)
                _transactions.value = list
                
                if (list.isEmpty()) {
                    _uiState.value = ReviewUiState.Empty
                } else {
                    _uiState.value = ReviewUiState.Success(list.first(), list.size)
                }
            } catch (e: Exception) {
                _uiState.value = ReviewUiState.Error(e.message ?: "Failed to load transactions")
            }
        }
    }

    fun confirmCategory(transaction: TransactionEntity, category: String) {
        viewModelScope.launch {
            try {
                // Update transaction with CONFIRMED status
                transactionDao.updateCategory(
                    id = transaction.id,
                    category = category,
                    subcategory = transaction.subcategory, // Keep existing subcategory logic or clear it
                    confidence = 1.0f, // Maximum confidence
                    source = "USER_CORRECTED"
                )
                
                // Remove from local list and show next
                removeAndShowNext(transaction.id)
                
                // TODO: Train the model (add to corrections table)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun ignoreTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                transactionDao.deleteById(transaction.id)
                removeAndShowNext(transaction.id)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun removeAndShowNext(id: String) {
        val currentList = _transactions.value.toMutableList()
        currentList.removeAll { it.id == id }
        _transactions.value = currentList

        if (currentList.isEmpty()) {
            _uiState.value = ReviewUiState.Empty
        } else {
            _uiState.value = ReviewUiState.Success(currentList.first(), currentList.size)
        }
    }
}

sealed class ReviewUiState {
    data object Loading : ReviewUiState()
    data object Empty : ReviewUiState()
    data class Success(
        val currentTransaction: TransactionEntity,
        val remainingCount: Int
    ) : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}
