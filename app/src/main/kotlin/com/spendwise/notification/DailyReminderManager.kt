package com.spendwise.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.spendwise.MainActivity
import com.spendwise.R
import com.spendwise.data.local.database.TransactionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Manages daily expense reminder notifications.
 *
 * Sends fun Gen Z style notifications at 9 AM and 9 PM
 * to help users stay on top of their spending.
 */
@Singleton
class DailyReminderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "daily_reminders"
        const val CHANNEL_NAME = "Daily Expense Reminders"
        const val MORNING_WORK_TAG = "morning_reminder"
        const val EVENING_WORK_TAG = "evening_reminder"
        const val NOTIFICATION_ID_MORNING = 9001
        const val NOTIFICATION_ID_EVENING = 9002
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders to track your expenses"
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedule daily reminders at 9 AM and 9 PM.
     */
    fun scheduleDailyReminders() {
        val workManager = WorkManager.getInstance(context)

        // Cancel existing reminders first
        workManager.cancelAllWorkByTag(MORNING_WORK_TAG)
        workManager.cancelAllWorkByTag(EVENING_WORK_TAG)

        // Schedule 9 AM reminder
        scheduleMorningReminder(workManager)

        // Schedule 9 PM reminder
        scheduleEveningReminder(workManager)

        android.util.Log.d("DailyReminderManager", "Scheduled daily reminders for 9 AM and 9 PM")
    }

    private fun scheduleMorningReminder(workManager: WorkManager) {
        val delay = calculateDelayUntil(9, 0) // 9:00 AM

        val morningRequest = PeriodicWorkRequestBuilder<MorningReminderWorker>(
            12, TimeUnit.HOURS // Repeat every 12 hours
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(MORNING_WORK_TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            MORNING_WORK_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            morningRequest
        )
    }

    private fun scheduleEveningReminder(workManager: WorkManager) {
        val delay = calculateDelayUntil(21, 0) // 9:00 PM

        val eveningRequest = PeriodicWorkRequestBuilder<EveningReminderWorker>(
            12, TimeUnit.HOURS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(EVENING_WORK_TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            EVENING_WORK_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            eveningRequest
        )
    }

    private fun calculateDelayUntil(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target time has passed today, schedule for tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Cancel all daily reminders.
     */
    fun cancelDailyReminders() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(MORNING_WORK_TAG)
        workManager.cancelAllWorkByTag(EVENING_WORK_TAG)
    }
}

/**
 * Gen Z style morning messages
 */
object GenZMessages {

    val morningGreetings = listOf(
        "rise and grind bestie! 🌅",
        "good morning money monarch! 👑",
        "wakey wakey, time to check that wallet! 💸",
        "gm gm! let's get this bread 🍞",
        "another day another slay! ✨",
        "bestie woke up and chose financial responsibility 💅",
        "main character energy starts with knowing ur balance 🎬",
        "no cap, checking expenses is self-care 🧘"
    )

    val morningMessages = listOf(
        "Yesterday you spent {amount}. Was it worth it tho? 🤔",
        "Your wallet called, it misses you. You spent {amount} yesterday 📞",
        "POV: You're about to see where your money went 👀",
        "{amount} left the chat yesterday. Let's not repeat that energy 💀",
        "Spent {amount} yesterday. That's {coffees} coffees btw ☕",
        "Yesterday's damage: {amount}. Today we do better fr fr 💪",
        "Quick vibe check on your spending! {amount} yesterday 📊",
        "{amount} gone but not forgotten. Check your expenses! 🔍"
    )

    val eveningGreetings = listOf(
        "hey night owl! 🦉",
        "evening bestie! 🌙",
        "it's giving... expense tracking time ✨",
        "before you doom scroll... 📱",
        "hot take: knowing your spending is lowkey therapeutic 🧠",
        "real ones check their expenses before bed 😤",
        "financial glow-up check! 💫",
        "slay the day, slay the budget 👑"
    )

    val eveningMessages = listOf(
        "Today's spending: {amount}. We feasting or we fasting? 🍽️",
        "You dropped {amount} today. No judgment, just facts 📝",
        "Daily debrief: {amount} spent. How we feeling? 🎭",
        "{amount} today. That's your roman empire for tonight 🏛️",
        "Plot twist: You spent {amount} today. The villain arc? 😈",
        "Today's lore: {amount} spent. Tomorrow we write a new chapter 📖",
        "{amount} left your account today. Periodt. 💅",
        "Spent {amount} today. Sleep on it, plan tomorrow! 😴"
    )

    val noSpendingMessages = listOf(
        "Zero spending today?! That's lowkey iconic 👑",
        "No spending = no stress. You're literally winning 🏆",
        "A no-spend day?! The discipline is immaculate ✨",
        "Wallet stayed fat today. We love to see it 💰",
        "Not a single rupee spent. Main character behavior 🎬",
        "Your bank account said 'thank you' today 🙏"
    )

    val budgetWarnings = listOf(
        "oop- you've used {percent}% of your budget... 😬",
        "budget check: {percent}% gone. we need to talk 💀",
        "bestie... {percent}% of budget used. let's slow down 🐢",
        "{percent}% budget used. the math ain't mathing 📉"
    )

    fun getRandomMorning(): String = morningGreetings.random()
    fun getRandomMorningMessage(): String = morningMessages.random()
    fun getRandomEvening(): String = eveningGreetings.random()
    fun getRandomEveningMessage(): String = eveningMessages.random()
    fun getRandomNoSpending(): String = noSpendingMessages.random()
    fun getRandomBudgetWarning(): String = budgetWarnings.random()

    fun formatAmount(amount: Double): String {
        return if (amount >= 1000) {
            String.format("₹%.1fk", amount / 1000)
        } else {
            String.format("₹%.0f", amount)
        }
    }

    fun formatMessage(template: String, amount: Double): String {
        val formattedAmount = formatAmount(amount)
        val coffees = (amount / 200).toInt() // Assuming ₹200 per fancy coffee

        return template
            .replace("{amount}", formattedAmount)
            .replace("{coffees}", coffees.toString())
    }
}
