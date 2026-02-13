package com.rupeelog.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.rupeelog.data.local.database.TransactionDao
import com.rupeelog.data.local.database.TransactionEntity
import com.rupeelog.agents.categorization.CategorizationAgent
import com.rupeelog.agents.deduplication.TransactionDeduplicationEngine
import com.rupeelog.parser.core.ParserRegistry
import com.rupeelog.parser.core.TransactionType
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
    lateinit var parserRegistry: ParserRegistry

    @Inject
    lateinit var transactionDao: TransactionDao

    @Inject
    lateinit var categorizationAgent: CategorizationAgent

    @Inject
    lateinit var deduplicationEngine: TransactionDeduplicationEngine

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

    // SMS/Messaging apps package names
    private val smsApps = setOf(
        "com.google.android.apps.messaging", // Google Messages
        "com.android.mms",                   // Stock Android SMS
        "com.samsung.android.messaging",     // Samsung Messages
        "com.miui.securitycenter",           // MIUI Security (shows SMS)
        "com.xiaomi.mipicks",                // Xiaomi
        "com.mi.android.globalminusscreen",  // POCO/Xiaomi
        "com.android.messaging",             // AOSP Messaging
    )

    // Bank apps package names
    private val bankApps = setOf(
        "com.sbi.SBIFreedomPlus",             // SBI YONO
        "com.sbi.lotusintouch",               // SBI YONO Lite
        "com.csam.icici.bank.imobile",        // ICICI iMobile
        "com.axis.mobile",                    // Axis Mobile
        "com.hdfc.mobilebanking",             // HDFC MobileBanking
        "com.kotak.mobile.banking",           // Kotak Mobile Banking
        "com.airtel.money",                   // Airtel Payments Bank
    )

    // DPDP Compliance: Financial keywords that MUST be present to process a notification
    // This ensures we only capture transaction-related data, not personal messages
    private val financialKeywords = setOf(
        "debited", "credited", "paid", "received", "sent", "withdrawn",
        "transferred", "payment", "transaction", "spent", "purchase",
        "₹", "rs.", "rs ", "inr", "upi"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // Check if it's from a financial app
        if (!isFinancialApp(packageName)) return

        val notification = sbn.notification
        val extras = notification.extras

        // Extract notification text
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullText = listOf(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString(" | ")

        // DPDP Compliance: Early keyword filtering - IMMEDIATELY drop non-financial notifications
        // This ensures we never process or store personal messages/chats
        if (!containsFinancialKeyword(fullText)) {
            // Non-financial notification - drop immediately without storing anything
            return
        }

        // Only log minimal info for debugging (no raw text in production)
        Log.d(TAG, "Processing financial notification from $packageName")

        // Process in background
        serviceScope.launch {
            processNotification(packageName, fullText)
        }
    }

    /**
     * DPDP Compliance: Check if notification contains financial keywords
     * This is the first line of defense - notifications without these keywords
     * are never processed or stored.
     */
    private fun containsFinancialKeyword(text: String): Boolean {
        val lowerText = text.lowercase()
        return financialKeywords.any { keyword -> lowerText.contains(keyword) }
    }

    private suspend fun processNotification(packageName: String, notificationText: String) {
        try {
            // Extract sender ID from notification (for SMS) or use package name
            val sender = extractSenderFromPackage(packageName)

            // Parse the notification using the new modular parser
            val parsedTransaction = parserRegistry.parse(
                body = notificationText,
                sender = sender,
                timestamp = System.currentTimeMillis()
            )

            if (parsedTransaction == null) {
                Log.d(TAG, "Not a valid transaction notification")
                return
            }

            // DPDP: Log only essential info, no raw text
            Log.d(TAG, "Valid transaction detected: ${parsedTransaction.type} from ${parsedTransaction.bankName}")

            // Create transaction entity
            val transactionId = parsedTransaction.transactionHash // Use hash for deduplication

            // Categorize the transaction
            val categoryResult = categorizationAgent.categorize(
                merchantText = parsedTransaction.merchant ?: "Unknown",
                amount = parsedTransaction.amount.toDouble()
            )

            // Get signed amount based on transaction type
            val signedAmount = parsedTransaction.getSignedAmount().toDouble()

            // DPDP Compliance: Only store extracted transaction data, NOT raw notification text
            // This implements Purpose Limitation - we only keep what's necessary
            val transaction = TransactionEntity(
                id = transactionId,
                amount = signedAmount,
                currency = parsedTransaction.currency,
                merchantName = categoryResult.merchantName ?: parsedTransaction.merchant ?: "Unknown",
                merchantRaw = parsedTransaction.merchant ?: "Unknown", // Store cleaned merchant name only
                category = categoryResult.category,
                subcategory = categoryResult.subcategory,
                timestamp = parsedTransaction.timestamp,
                source = parsedTransaction.bankName, // Store bank/app name instead of package
                categoryConfidence = categoryResult.confidence,
                categorySource = categoryResult.source,
                rawNotificationText = null // DPDP: Never store raw notification text
            )

            // Process through deduplication engine
            deduplicationEngine.process(transaction)

            // DPDP: Minimal logging - no amounts or merchant details
            Log.d(TAG, "Transaction sent to deduplication engine")

            // TODO: Check budget alerts
            // TODO: Trigger analysis if needed

        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    /**
     * Maps package name to a sender ID for parser selection.
     * For SMS apps, the actual sender ID will be extracted from the notification.
     */
    private fun extractSenderFromPackage(packageName: String): String {
        return when {
            packageName.contains("phonepe") -> "com.phonepe.app"
            packageName.contains("amazon") -> "in.amazon.mShop.android.shopping"
            packageName.contains("google.android.apps.nbu") -> "com.google.android.apps.nbu.paisa.user"
            packageName.contains("paytm") || packageName.contains("one97") -> "net.one97.paytm"
            packageName.contains("whatsapp") -> "com.whatsapp"
            // For bank apps and SMS, use package name as sender
            else -> packageName
        }
    }

    private fun isFinancialApp(packageName: String): Boolean {
        return packageName in upiApps || packageName in bankApps || packageName in smsApps
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
