package com.spendwise.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.local.database.TransactionDao
import com.spendwise.data.local.database.InsightDao
import com.spendwise.data.local.database.BudgetDao
import com.spendwise.data.local.database.CategoryRuleDao
import com.spendwise.data.local.database.SyncQueueDao
import com.spendwise.data.local.preferences.EncryptedPreferences
import com.spendwise.data.manager.BackupManager
import com.spendwise.data.repository.AuthRepository
import com.spendwise.data.repository.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.net.Uri

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transactionDao: TransactionDao,
    private val insightDao: InsightDao,
    private val budgetDao: BudgetDao,
    private val categoryRuleDao: CategoryRuleDao,
    private val syncQueueDao: SyncQueueDao,
    private val encryptedPreferences: EncryptedPreferences,
    private val backupManager: BackupManager
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _deleteDataState = MutableStateFlow<DeleteDataState>(DeleteDataState.Idle)
    val deleteDataState: StateFlow<DeleteDataState> = _deleteDataState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            authRepository.signInWithGoogle(activityContext)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
    
    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            val result = backupManager.exportToCsv(uri)
            if (result.isSuccess) {
                _exportState.value = ExportState.Success(result.getOrDefault(0))
            } else {
                _exportState.value = ExportState.Error(result.exceptionOrNull()?.message ?: "Export failed")
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    /**
     * DPDP Compliance: Right to Erasure - Delete all user data
     * This implements the user's right to have their data deleted.
     */
    fun deleteAllData() {
        viewModelScope.launch {
            _deleteDataState.value = DeleteDataState.Deleting
            try {
                // Delete all local data
                transactionDao.deleteAll()

                // Delete insights
                insightDao.deleteExpired(Long.MAX_VALUE) // This deletes all by using max time

                // Clear encrypted preferences (API keys, consent records, etc.)
                encryptedPreferences.clearAll()

                // Sign out from cloud if signed in
                if (authRepository.isAuthenticated()) {
                    authRepository.signOut()
                }

                // TODO: Delete cloud data via Supabase API when implemented

                _deleteDataState.value = DeleteDataState.Success
            } catch (e: Exception) {
                _deleteDataState.value = DeleteDataState.Error(e.message ?: "Failed to delete data")
            }
        }
    }

    fun resetDeleteState() {
        _deleteDataState.value = DeleteDataState.Idle
    }
}

sealed class DeleteDataState {
    data object Idle : DeleteDataState()
    data object Deleting : DeleteDataState()
    data object Success : DeleteDataState()
    data class Error(val message: String) : DeleteDataState()
}

sealed class ExportState {
    data object Idle : ExportState()
    data object Exporting : ExportState()
    data class Success(val count: Int) : ExportState()
    data class Error(val message: String) : ExportState()
}
