package com.spendwise.data.manager

import android.content.Context
import android.net.Uri
import com.spendwise.data.local.database.TransactionDao
import com.spendwise.data.local.database.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao
) {
    suspend fun exportToCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val transactions: List<TransactionEntity> = transactionDao.getAllTransactionsSync()
            
            val contentResolver = context.contentResolver
            val outputStream = contentResolver.openOutputStream(uri) ?: throw Exception("Cannot open URI")
            
            outputStream.use { stream ->
                val writer = BufferedWriter(OutputStreamWriter(stream))
                writer.use { w ->
                    w.write("Date,Amount,Currency,Merchant,Category,Subcategory,Source,Notes")
                    w.newLine()
                    
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    
                    for (tx in transactions) {
                        val dateStr = dateFormat.format(Date(tx.timestamp))
                        val line = StringBuilder()
                        line.append(dateStr).append(",")
                        line.append(tx.amount).append(",")
                        line.append(tx.currency).append(",")
                        line.append(escapeCsv(tx.merchantName)).append(",")
                        line.append(tx.category).append(",")
                        line.append(tx.subcategory ?: "").append(",")
                        line.append(tx.source).append(",")
                        line.append(escapeCsv(tx.notes ?: ""))
                        
                        w.write(line.toString())
                        w.newLine()
                    }
                }
            }
            Result.success(transactions.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}