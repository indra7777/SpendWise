package com.spendwise

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SpendWiseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Server notification channel
            val serverChannel = NotificationChannel(
                CHANNEL_SERVER,
                "Dashboard Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the dashboard server is running"
                setShowBadge(false)
            }

            // Budget alerts channel
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about budget warnings and limits"
                enableVibration(true)
            }

            // Insights channel
            val insightsChannel = NotificationChannel(
                CHANNEL_INSIGHTS,
                "Spending Insights",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily and weekly spending insights"
            }

            notificationManager.createNotificationChannels(
                listOf(serverChannel, alertsChannel, insightsChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_SERVER = "server_channel"
        const val CHANNEL_ALERTS = "alerts_channel"
        const val CHANNEL_INSIGHTS = "insights_channel"
    }
}
