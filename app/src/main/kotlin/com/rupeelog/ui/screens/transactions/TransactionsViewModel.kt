package com.rupeelog.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rupeelog.data.local.database.TransactionDao
import com.rupeelog.data.local.database.TransactionEntity
import com.rupeelog.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _allTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            transactionDao.getAllTransactions().collect { transactionList ->
                _allTransactions.value = transactionList
                applyFilter()
                _isLoading.value = false
            }
        }
    }

    fun setFilter(category: Category?) {
        _selectedCategory.value = category
        applyFilter()
    }

    private fun applyFilter() {
        val category = _selectedCategory.value
        _transactions.value = if (category == null) {
            _allTransactions.value
        } else {
            _allTransactions.value.filter { it.category == category.name }
        }
    }

    fun updateCategory(transactionId: String, newCategory: Category) {
        viewModelScope.launch {
            transactionDao.updateCategory(
                id = transactionId,
                category = newCategory.name,
                subcategory = null,
                confidence = 1.0f,
                source = "USER_CORRECTED"
            )
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            transactionDao.deleteById(transactionId)
        }
    }

    fun getGroupedTransactions(): Map<String, List<TransactionEntity>> {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val today = dateFormat.format(Date())
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))

        return _transactions.value.groupBy { transaction ->
            val date = dateFormat.format(Date(transaction.timestamp))
            when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> date
            }
        }
    }
}
