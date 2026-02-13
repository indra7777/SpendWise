package com.spendwise.agents.roast

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendwise.data.local.database.TransactionDao
import com.spendwise.data.local.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class DailyRoastWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val userPreferences: UserPreferences,
    private val sarcasticNotificationAgent: SarcasticNotificationAgent
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val calendar = Calendar.getInstance()
            
            // Set range to Today 00:00:00 to Now
            val endTime = calendar.timeInMillis
            
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            val totalSpent = transactionDao.getTotalSpent(startTime, endTime) ?: 0.0
            
            // Calculate Daily Budget (Monthly / 30)
            val monthlyBudget = userPreferences.getMonthlyBudget()
            val dailyBudget = monthlyBudget / 30.0

            // Trigger Roast
            // We use abs() because expenses are stored as negative
            sarcasticNotificationAgent.roastDailySummary(kotlin.math.abs(totalSpent), dailyBudget)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
