package com.rupeelog.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rupeelog.agents.categorization.CategorizationAgent
import com.rupeelog.data.importer.ImportResult
import com.rupeelog.data.importer.ParsedTransaction
import com.rupeelog.data.importer.StatementParser
import com.rupeelog.data.local.database.TransactionDao
import com.rupeelog.data.local.database.TransactionEntity
import com.rupeelog.sms.SmsTransactionImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportUiState(
    val isImporting: Boolean = false,
    val showResultDialog: Boolean = false,
    val showPasswordDialog: Boolean = false,
    val pendingPdfUri: Uri? = null,
    val detectedBank: BankType? = null,
    val progress: Float = 0f,
    val progressMessage: String = "",
    val result: ImportResultSummary? = null,
    // SMS Import state
    val smsImportAvailable: Boolean = false,
    val smsCount: Int = 0,
    val isImportingSms: Boolean = false,
    val smsImportProgress: Float = 0f,
    val smsImportMessage: String = ""
)

data class ImportResultSummary(
    val success: Boolean,
    val format: String,
    val totalParsed: Int,
    val totalImported: Int,
    val totalSkipped: Int,
    val errors: List<String>
)

/**
 * Bank types with their known password formats
 */
enum class BankType(
    val displayName: String,
    val passwordHint: String
) {
    SBI_YONO("SBI YONO", "DDMM@Last4MobileDigits (e.g., 1210@5467)"),
    SBI_EMAIL("SBI Email Statement", "Last5Mobile + DDMMYY (e.g., 95827271291)"),
    SBI_NETBANKING("SBI Net Banking", "11-digit Account Number"),
    PHONEPE("PhonePe", "10-digit Mobile Number"),
    HDFC("HDFC Bank", "Password set during download"),
    ICICI("ICICI Bank", "Password set during download"),
    AXIS("Axis Bank", "Password set during download"),
    UNKNOWN("Unknown Bank", "Enter the PDF password")
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statementParser: StatementParser,
    private val transactionDao: TransactionDao,
    private val categorizationAgent: CategorizationAgent,
    private val smsTransactionImporter: SmsTransactionImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        // Check SMS import availability on init
        checkSmsAvailability()
    }

    /**
     * Check if SMS import is available and count importable messages.
     */
    fun checkSmsAvailability() {
        android.util.Log.d("ImportViewModel", "=== checkSmsAvailability() called ===")
        viewModelScope.launch {
            try {
                android.util.Log.d("ImportViewModel", "Checking importable count...")
                val count = withContext(Dispatchers.IO) {
                    smsTransactionImporter.getImportableCount()
                }
                android.util.Log.d("ImportViewModel", "Found $count importable SMS messages")
                _uiState.update {
                    it.copy(
                        smsImportAvailable = count > 0,
                        smsCount = count
                    )
                }
            } catch (e: SecurityException) {
                android.util.Log.e("ImportViewModel", "SecurityException in checkSmsAvailability", e)
                // SMS permission not granted
                _uiState.update {
                    it.copy(smsImportAvailable = false, smsCount = 0)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(smsImportAvailable = false, smsCount = 0)
                }
            }
        }
    }

    /**
     * Import transactions from SMS history.
     */
    fun importFromSms() {
        android.util.Log.d("ImportViewModel", "=== importFromSms() called ===")
        viewModelScope.launch {
            android.util.Log.d("ImportViewModel", "Starting SMS import coroutine")
            _uiState.update {
                it.copy(
                    isImportingSms = true,
                    smsImportProgress = 0f,
                    smsImportMessage = "Reading SMS messages..."
                )
            }

            try {
                smsTransactionImporter.importWithProgress().collect { progress ->
                    _uiState.update {
                        it.copy(
                            smsImportProgress = if (progress.total > 0) {
                                progress.current.toFloat() / progress.total
                            } else 0f,
                            smsImportMessage = progress.status
                        )
                    }
                }

                // Re-check count after import
                val newCount = withContext(Dispatchers.IO) {
                    smsTransactionImporter.getImportableCount()
                }

                _uiState.update {
                    it.copy(
                        isImportingSms = false,
                        smsCount = newCount,
                        smsImportProgress = 1f,
                        showResultDialog = true,
                        result = ImportResultSummary(
                            success = true,
                            format = "SMS",
                            totalParsed = _uiState.value.smsCount,
                            totalImported = _uiState.value.smsCount - newCount,
                            totalSkipped = 0,
                            errors = emptyList()
                        )
                    )
                }

            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(
                        isImportingSms = false,
                        showResultDialog = true,
                        result = ImportResultSummary(
                            success = false,
                            format = "SMS",
                            totalParsed = 0,
                            totalImported = 0,
                            totalSkipped = 0,
                            errors = listOf("SMS permission not granted. Please enable SMS permission in Settings.")
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImportingSms = false,
                        showResultDialog = true,
                        result = ImportResultSummary(
                            success = false,
                            format = "SMS",
                            totalParsed = 0,
                            totalImported = 0,
                            totalSkipped = 0,
                            errors = listOf("SMS import failed: ${e.message}")
                        )
                    )
                }
            }
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, progress = 0f, progressMessage = "Reading file...") }

            try {
                // Check if it's a PDF file
                val isPdf = withContext(Dispatchers.IO) {
                    statementParser.isPdf(context, uri)
                }

                if (isPdf) {
                    // Check if password protected
                    val isProtected = withContext(Dispatchers.IO) {
                        statementParser.isPdfPasswordProtected(context, uri)
                    }

                    if (isProtected) {
                        // Show password dialog
                        _uiState.update {
                            it.copy(
                                isImporting = false,
                                showPasswordDialog = true,
                                pendingPdfUri = uri,
                                detectedBank = detectBankFromFileName(uri)
                            )
                        }
                        return@launch
                    }

                    // Parse unprotected PDF
                    val parseResult = withContext(Dispatchers.IO) {
                        statementParser.parsePdfFile(context, uri, null)
                    }
                    processParseResult(parseResult)
                    return@launch
                }

                // Parse CSV/text file
                val parseResult = withContext(Dispatchers.IO) {
                    statementParser.parseFile(context, uri)
                }

                processParseResult(parseResult)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        showResultDialog = true,
                        result = ImportResultSummary(
                            success = false,
                            format = "Error",
                            totalParsed = 0,
                            totalImported = 0,
                            totalSkipped = 0,
                            errors = listOf("Import failed: ${e.message}")
                        )
                    )
                }
            }
        }
    }

    fun dismissResultDialog() {
        _uiState.update { it.copy(showResultDialog = false, result = null) }
    }

    fun dismissPasswordDialog() {
        _uiState.update { 
            it.copy(
                showPasswordDialog = false, 
                pendingPdfUri = null,
                detectedBank = null
            ) 
        }
    }

    fun importPdfWithPassword(password: String) {
        val uri = _uiState.value.pendingPdfUri ?: return
        
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    showPasswordDialog = false,
                    isImporting = true, 
                    progress = 0f, 
                    progressMessage = "Decrypting PDF..."
                ) 
            }

            try {
                val parseResult = withContext(Dispatchers.IO) {
                    statementParser.parsePdfFile(context, uri, password)
                }

                if (!parseResult.success && parseResult.format.contains("Password", ignoreCase = true)) {
                    // Wrong password - show dialog again
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            showPasswordDialog = true,
                            pendingPdfUri = uri
                        )
                    }
                    return@launch
                }

                processParseResult(parseResult)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        showResultDialog = true,
                        pendingPdfUri = null,
                        result = ImportResultSummary(
                            success = false,
                            format = "PDF Error",
                            totalParsed = 0,
                            totalImported = 0,
                            totalSkipped = 0,
                            errors = listOf("PDF import failed: ${e.message}")
                        )
                    )
                }
            }
        }
    }

    private suspend fun processParseResult(parseResult: ImportResult) {
        if (!parseResult.success) {
            _uiState.update {
                it.copy(
                    isImporting = false,
                    showResultDialog = true,
                    pendingPdfUri = null,
                    result = ImportResultSummary(
                        success = false,
                        format = parseResult.format,
                        totalParsed = 0,
                        totalImported = 0,
                        totalSkipped = 0,
                        errors = parseResult.errors
                    )
                )
            }
            return
        }

        _uiState.update { 
            it.copy(
                progress = 0.2f, 
                progressMessage = "Parsed ${parseResult.transactions.size} transactions..."
            ) 
        }

        // Get existing transaction timestamps to avoid duplicates
        val existingTimestamps = withContext(Dispatchers.IO) {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            transactionDao.getTransactionsBetweenSync(thirtyDaysAgo, System.currentTimeMillis())
                .map { Triple(it.timestamp, it.amount, it.merchantName) }
                .toSet()
        }

        // Filter out duplicates
        val newTransactions = parseResult.transactions.filter { parsed ->
            !existingTimestamps.any { existing ->
                kotlin.math.abs(existing.first - parsed.date) < 60000 &&
                kotlin.math.abs(existing.second - (if (parsed.isCredit) parsed.amount else -parsed.amount)) < 0.01
            }
        }

        val skippedCount = parseResult.transactions.size - newTransactions.size

        _uiState.update {
            it.copy(
                progress = 0.3f,
                progressMessage = "Categorizing ${newTransactions.size} new transactions..."
            )
        }

        // Categorize and save transactions
        val importedTransactions = mutableListOf<TransactionEntity>()
        val errors = parseResult.errors.toMutableList()

        newTransactions.forEachIndexed { index, parsed ->
            try {
                val categorization = categorizationAgent.categorize(
                    merchantText = parsed.merchantName,
                    amount = parsed.amount
                )

                val entity = statementParser.convertToTransactionEntity(
                    parsed = parsed,
                    category = categorization.category,
                    categoryConfidence = categorization.confidence,
                    categorySource = categorization.source
                )

                withContext(Dispatchers.IO) {
                    transactionDao.insert(entity)
                }

                importedTransactions.add(entity)

                val progress = 0.3f + (0.7f * (index + 1) / newTransactions.size)
                _uiState.update {
                    it.copy(
                        progress = progress,
                        progressMessage = "Imported ${index + 1}/${newTransactions.size}..."
                    )
                }
            } catch (e: Exception) {
                errors.add("Failed to import: ${parsed.merchantName} - ${e.message}")
            }
        }

        _uiState.update {
            it.copy(
                isImporting = false,
                showResultDialog = true,
                pendingPdfUri = null,
                progress = 1f,
                result = ImportResultSummary(
                    success = importedTransactions.isNotEmpty() || parseResult.transactions.isEmpty(),
                    format = parseResult.format,
                    totalParsed = parseResult.transactions.size,
                    totalImported = importedTransactions.size,
                    totalSkipped = skippedCount,
                    errors = errors.take(5)
                )
            )
        }
    }

    private fun detectBankFromFileName(uri: Uri): BankType {
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        }?.lowercase() ?: ""

        return when {
            fileName.contains("yono") || fileName.contains("sbi") && fileName.contains("app") -> BankType.SBI_YONO
            fileName.contains("sbi") && fileName.contains("email") -> BankType.SBI_EMAIL
            fileName.contains("sbi") -> BankType.SBI_NETBANKING
            fileName.contains("phonepe") || fileName.contains("phone_pe") -> BankType.PHONEPE
            fileName.contains("hdfc") -> BankType.HDFC
            fileName.contains("icici") -> BankType.ICICI
            fileName.contains("axis") -> BankType.AXIS
            else -> BankType.UNKNOWN
        }
    }
}
