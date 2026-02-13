package com.spendwise.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a raw SMS message.
 */
data class SmsMessage(
    val id: Long,
    val address: String,  // Sender ID (e.g., "HDFCBK", "SBIINB")
    val body: String,
    val date: Long,       // Timestamp in milliseconds
    val type: Int         // 1 = received, 2 = sent
)

/**
 * Reads SMS messages from the device's SMS inbox.
 *
 * Requires READ_SMS permission.
 */
@Singleton
class SmsReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SmsReader"
        private val SMS_URI: Uri = Uri.parse("content://sms/inbox")

        // Common bank sender IDs in India
        private val BANK_SENDER_PATTERNS = listOf(
            // Major banks
            "HDFCBK", "HDFCBANK", "HDFC",
            "ICICIB", "ICICIBANK", "ICICI",
            "SBIINB", "SBICRD", "ATMSBI", "SBI",
            "AXISBK", "AXIS",
            "KOTAKB", "KOTAK",
            "YESBK", "YESBANK",
            "BOIIND", "BOI",
            "PNBSMS", "PNB",
            "CANBNK", "CANARA",
            "UNIONB", "UNION",
            "IABOROD", "BOB",
            "IDFCFB", "IDFC",
            "INDUSB", "INDUSIND",
            "FEDERAL", "FEDSMS",

            // Payment banks & wallets
            "AIRTEL", "APBANK",
            "PAYTM", "PYTM",
            "JIOPAY", "JIO",
            "AMAZONPAY", "AMZN",

            // Credit cards
            "HDFCCC", "ICICICC", "SBICARD", "AXISCC",

            // UPI apps (when they send SMS)
            "PHONEPE", "GPAY", "BHIM"
        )
    }

    /**
     * Reads all SMS messages from inbox.
     *
     * @param limit Maximum number of messages to read (0 = no limit)
     * @param sinceTimestamp Only read messages after this timestamp (0 = no filter)
     * @return List of SMS messages
     */
    fun readAllSms(limit: Int = 0, sinceTimestamp: Long = 0): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()

        try {
            val selection = if (sinceTimestamp > 0) "date > ?" else null
            val selectionArgs = if (sinceTimestamp > 0) arrayOf(sinceTimestamp.toString()) else null
            val sortOrder = "date DESC" + if (limit > 0) " LIMIT $limit" else ""

            val cursor: Cursor? = context.contentResolver.query(
                SMS_URI,
                arrayOf("_id", "address", "body", "date", "type"),
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow("_id")
                val addressIndex = it.getColumnIndexOrThrow("address")
                val bodyIndex = it.getColumnIndexOrThrow("body")
                val dateIndex = it.getColumnIndexOrThrow("date")
                val typeIndex = it.getColumnIndexOrThrow("type")

                while (it.moveToNext()) {
                    val sms = SmsMessage(
                        id = it.getLong(idIndex),
                        address = it.getString(addressIndex) ?: "",
                        body = it.getString(bodyIndex) ?: "",
                        date = it.getLong(dateIndex),
                        type = it.getInt(typeIndex)
                    )
                    messages.add(sms)
                }
            }

            Log.d(TAG, "Read ${messages.size} SMS messages")
        } catch (e: SecurityException) {
            Log.e(TAG, "SMS permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS", e)
        }

        return messages
    }

    /**
     * Reads only bank/financial SMS messages.
     * Filters by known bank sender IDs.
     *
     * @param limit Maximum number of messages to read
     * @param sinceTimestamp Only read messages after this timestamp
     * @return List of bank SMS messages
     */
    fun readBankSms(limit: Int = 0, sinceTimestamp: Long = 0): List<SmsMessage> {
        val allSms = readAllSms(limit = 0, sinceTimestamp = sinceTimestamp)

        val bankSms = allSms.filter { sms ->
            isBankSender(sms.address) && containsFinancialKeywords(sms.body)
        }

        Log.d(TAG, "Filtered ${bankSms.size} bank SMS from ${allSms.size} total")

        return if (limit > 0) bankSms.take(limit) else bankSms
    }

    /**
     * Checks if the sender ID matches a known bank pattern.
     */
    private fun isBankSender(address: String): Boolean {
        val upperAddress = address.uppercase()
        return BANK_SENDER_PATTERNS.any { pattern ->
            upperAddress.contains(pattern)
        }
    }

    /**
     * Checks if the SMS body contains financial keywords.
     * This helps filter out promotional messages from banks.
     */
    private fun containsFinancialKeywords(body: String): Boolean {
        val lowerBody = body.lowercase()
        val keywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "transferred", "paid", "received", "sent",
            "rs.", "rs ", "₹", "inr",
            "a/c", "acct", "account",
            "upi", "neft", "imps", "rtgs"
        )
        return keywords.any { lowerBody.contains(it) }
    }

    /**
     * Gets the count of bank SMS messages.
     */
    fun getBankSmsCount(sinceTimestamp: Long = 0): Int {
        return readBankSms(sinceTimestamp = sinceTimestamp).size
    }
}
