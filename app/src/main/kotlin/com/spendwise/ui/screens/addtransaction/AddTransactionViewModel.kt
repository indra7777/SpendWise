package com.spendwise.ui.screens.addtransaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.local.database.TransactionDao
import com.spendwise.data.local.database.TransactionEntity
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val transactionDao: TransactionDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val transactionId: String? = savedStateHandle["transactionId"]

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    // Form State exposed to UI
    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    init {
        if (transactionId != null) {
            loadTransaction(transactionId)
        }
    }

    private fun loadTransaction(id: String) {
        viewModelScope.launch {
            try {
                val transaction = transactionDao.getById(id)
                if (transaction != null) {
                    _formState.value = TransactionFormState(
                        amount = abs(transaction.amount).toString(),
                        merchantName = transaction.merchantName,
                        category = try { Category.valueOf(transaction.category) } catch (e: Exception) { Category.OTHER },
                        notes = transaction.notes ?: "",
                        isExpense = transaction.amount < 0
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun updateForm(
        amount: String? = null,
        merchantName: String? = null,
        category: Category? = null,
        notes: String? = null,
        isExpense: Boolean? = null
    ) {
        _formState.value = _formState.value.copy(
            amount = amount ?: _formState.value.amount,
            merchantName = merchantName ?: _formState.value.merchantName,
            category = category ?: _formState.value.category,
            notes = notes ?: _formState.value.notes,
            isExpense = isExpense ?: _formState.value.isExpense
        )
    }

    fun saveTransaction() {
        val form = _formState.value
        val amountValue = form.amount.toDoubleOrNull() ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val finalAmount = if (form.isExpense) -abs(amountValue) else abs(amountValue)
                
                if (transactionId != null) {
                    // Update existing
                    val existing = transactionDao.getById(transactionId)
                    if (existing != null) {
                        val updated = existing.copy(
                            amount = finalAmount,
                            merchantName = form.merchantName,
                            category = form.category.name,
                            notes = form.notes.ifBlank { null },
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.insert(updated) // Dao insert is ON CONFLICT REPLACE
                    }
                } else {
                    // Create new
                    val transaction = TransactionEntity(
                        id = UUID.randomUUID().toString(),
                        amount = finalAmount,
                        currency = "INR",
                        merchantName = form.merchantName,
                        merchantRaw = form.merchantName,
                        category = form.category.name,
                        subcategory = null,
                        timestamp = System.currentTimeMillis(),
                        source = "MANUAL",
                        categoryConfidence = 1.0f,
                        categorySource = "USER",
                        isSynced = false,
                        notes = form.notes.ifBlank { null },
                        rawNotificationText = null
                    )
                    repository.insert(transaction)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to save transaction"
                )
            }
        }
    }
}

data class TransactionFormState(
    val amount: String = "",
    val merchantName: String = "",
    val category: Category = Category.OTHER,
    val notes: String = "",
    val isExpense: Boolean = true
)

data class AddTransactionUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)
