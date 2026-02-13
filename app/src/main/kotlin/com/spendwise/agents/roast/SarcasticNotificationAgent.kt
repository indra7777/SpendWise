package com.spendwise.agents.roast

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.spendwise.R
import com.spendwise.data.local.database.TransactionEntity
import com.spendwise.data.local.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class SarcasticNotificationAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    fun processTransaction(transaction: TransactionEntity) {
        if (!userPreferences.isRoastModeEnabled()) return

        // Only roast expenses (debits)
        if (transaction.amount >= 0) return

        // Logic to decide if we should roast (don't roast EVERY transaction, maybe 30% chance for small ones, 100% for big ones)
        val amount = kotlin.math.abs(transaction.amount)
        val shouldRoast = when {
            amount > 5000 -> true // Always roast big spenders
            amount > 1000 -> Random.nextFloat() < 0.7f // 70% chance
            else -> Random.nextFloat() < 0.3f // 30% chance
        }

        if (!shouldRoast) return

        val roastMessage = getRoastMessage(transaction)
        showNotification(roastMessage)
    }

    fun roastDailySummary(totalSpent: Double, dailyBudget: Double) {
        if (!userPreferences.isRoastModeEnabled()) return
        if (totalSpent <= 0) return

        val overBudget = totalSpent > dailyBudget
        val message = if (overBudget) {
            "You spent ₹${totalSpent.toInt()} today. That's over your daily budget of ₹${dailyBudget.toInt()}. Are you allergic to saving money?"
        } else {
            "You spent ₹${totalSpent.toInt()} today. Not bad, but don't get cocky."
        }
        
        showNotification(message)
    }

    private fun getRoastMessage(transaction: TransactionEntity): String {
        val hour = Calendar.getInstance().apply { timeInMillis = transaction.timestamp }.get(Calendar.HOUR_OF_DAY)
        val isLateNight = hour < 5 || hour > 23

        if (isLateNight) {
            return RoastTemplates.LATE_NIGHT.random()
        }

        return when (transaction.category.lowercase()) {
            "food", "dining", "restaurants", "food_delivery" -> RoastTemplates.FOOD_DINING.random()
            "shopping", "clothing", "electronics" -> RoastTemplates.SHOPPING.random()
            "entertainment", "movies", "games" -> RoastTemplates.ENTERTAINMENT.random()
            "transport", "taxi", "uber", "ola" -> RoastTemplates.TRANSPORT.random()
            else -> {
                if (kotlin.math.abs(transaction.amount) > 2000) {
                    RoastTemplates.GENERIC_HIGH_EXPENSE.random()
                } else {
                    "Spending money again? I'm watching you."
                }
            }
        }
    }

    private fun showNotification(message: String) {
        val notificationId = System.currentTimeMillis().toInt() // Unique ID

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Make sure this resource exists, or use android.R.drawable.ic_dialog_info
            .setContentTitle("SpendWise says...")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sarcastic Alerts"
            val descriptionText = "Get roasted for your spending habits"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "roast_channel"
    }
}
