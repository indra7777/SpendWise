package com.spendwise.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.spendwise.data.local.database.TransactionDao
import com.spendwise.data.local.database.TransactionEntity
import com.spendwise.agents.categorization.CategorizationAgent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class TransactionNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var notificationParser: NotificationParser

    @Inject
    lateinit var transactionDao: TransactionDao

    @Inject
    lateinit var categorizationAgent: CategorizationAgent

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // UPI Apps package names
    private val upiApps = setOf(
        "com.phonepe.app",                    // PhonePe
        "in.amazon.mShop.android.shopping",   // Amazon (Amazon Pay)
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "net.one97.paytm",                    // Paytm
        "com.whatsapp",                       // WhatsApp Pay
        "in.org.npci.upiapp",                 // BHIM
        "com.freecharge.android",             // Freecharge
        "com.mobikwik_new",                   // Mobikwik
    )

    // Bank apps package names
    private val bankApps = setOf(
        "com.sbi.SBIFreedomPlus",             // SBI YONO
        "com.sbi.lotusintouch",               // SBI YONO Lite
        "com.csam.icici.bank.imobile",        // ICICI iMobile
        "com.axis.mobile",                    // Axis Mobile
        "com.hdfc.mobilebanking",             // HDFC MobileBanking
        "com.kotak.mobile.banking",           // Kotak Mobile Banking
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // Check if it's from a financial app
        if (!isFinancialApp(packageName)) return

        val notification = sbn.notification
        val extras = notification.extras

        // Extract notification text
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullText = listOf(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString(" | ")

        Log.d(TAG, "Financial notification from $packageName: $fullText")

        // Process in background
        serviceScope.launch {
            processNotification(packageName, fullText)
        }
    }

    private suspend fun processNotification(packageName: String, notificationText: String) {
        try {
            // Parse the notification
            val parsedTransaction = notificationParser.parse(notificationText, packageName)

            if (!parsedTransaction.isValidTransaction) {
                Log.d(TAG, "Not a valid transaction notification")
                return
            }

            Log.d(TAG, "Parsed transaction: $parsedTransaction")

            // Create transaction entity
            val transactionId = UUID.randomUUID().toString()

            // Categorize the transaction
            val categoryResult = categorizationAgent.categorize(
                merchantText = parsedTransaction.merchantName ?: notificationText,
                amount = parsedTransaction.amount
            )

            val transaction = TransactionEntity(
                id = transactionId,
                amount = parsedTransaction.amount ?: 0.0,
                currency = parsedTransaction.currency ?: "INR",
                merchantName = categoryResult.merchantName ?: parsedTransaction.merchantName ?: "Unknown",
                merchantRaw = parsedTransaction.merchantRaw ?: notificationText,
                category = categoryResult.category,
                subcategory = categoryResult.subcategory,
                timestamp = parsedTransaction.timestamp ?: System.currentTimeMillis(),
                source = "NOTIFICATION",
                categoryConfidence = categoryResult.confidence,
                categorySource = categoryResult.source,
                rawNotificationText = notificationText
            )

            // Save to database
            transactionDao.insert(transaction)

            Log.d(TAG, "Transaction saved: ${transaction.merchantName} - ${transaction.amount} - ${transaction.category}")

            // TODO: Check budget alerts
            // TODO: Trigger analysis if needed

        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    private fun isFinancialApp(packageName: String): Boolean {
        return packageName in upiApps || packageName in bankApps
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for our use case
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Notification listener service destroyed")
    }

    companion object {
        private const val TAG = "TransactionListener"
    }
}
