package com.spendwise.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendwise.MainActivity
import com.spendwise.R
import com.spendwise.data.local.database.TransactionDao
import com.spendwise.data.local.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs

/**
 * Morning reminder worker - runs at 9 AM.
 * Shows yesterday's spending summary with Gen Z vibes.
 */
@HiltWorker
class MorningReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Check if reminders are enabled
            if (!userPreferences.dailyRemindersEnabled.first()) {
                return@withContext Result.success()
            }

            // Get yesterday's spending
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val yesterdayStart = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            val yesterdayEnd = calendar.timeInMillis

            val yesterdaySpending = transactionDao.getTotalSpendingBetweenSync(
                yesterdayStart,
                yesterdayEnd
            )

            val title = GenZMessages.getRandomMorning()
            val message = if (abs(yesterdaySpending) < 1) {
                GenZMessages.getRandomNoSpending()
            } else {
                GenZMessages.formatMessage(
                    GenZMessages.getRandomMorningMessage(),
                    abs(yesterdaySpending)
                )
            }

            showNotification(
                title = title,
                message = message,
                notificationId = DailyReminderManager.NOTIFICATION_ID_MORNING
            )

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("MorningReminderWorker", "Failed to show notification", e)
            Result.retry()
        }
    }

    private fun showNotification(title: String, message: String, notificationId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "transactions")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DailyReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, notification)
    }
}

/**
 * Evening reminder worker - runs at 9 PM.
 * Shows today's spending summary with Gen Z vibes.
 */
@HiltWorker
class EveningReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Check if reminders are enabled
            if (!userPreferences.dailyRemindersEnabled.first()) {
                return@withContext Result.success()
            }

            // Get today's spending
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val todayStart = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            val todayEnd = calendar.timeInMillis

            val todaySpending = transactionDao.getTotalSpendingBetweenSync(
                todayStart,
                todayEnd
            )

            // Check budget usage
            val monthlyBudget = userPreferences.monthlyBudget.first()
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis

            val monthSpending = transactionDao.getTotalSpendingBetweenSync(
                monthStart,
                System.currentTimeMillis()
            )

            val budgetUsedPercent = if (monthlyBudget > 0) {
                ((abs(monthSpending) / monthlyBudget) * 100).toInt()
            } else 0

            val title = GenZMessages.getRandomEvening()
            val message = buildString {
                if (abs(todaySpending) < 1) {
                    append(GenZMessages.getRandomNoSpending())
                } else {
                    append(
                        GenZMessages.formatMessage(
                            GenZMessages.getRandomEveningMessage(),
                            abs(todaySpending)
                        )
                    )
                }

                // Add budget warning if over 80%
                if (budgetUsedPercent >= 80) {
                    append("\n\n")
                    append(
                        GenZMessages.getRandomBudgetWarning()
                            .replace("{percent}", budgetUsedPercent.toString())
                    )
                }
            }

            showNotification(
                title = title,
                message = message,
                notificationId = DailyReminderManager.NOTIFICATION_ID_EVENING
            )

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("EveningReminderWorker", "Failed to show notification", e)
            Result.retry()
        }
    }

    private fun showNotification(title: String, message: String, notificationId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "home")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DailyReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, notification)
    }
}
