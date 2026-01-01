package com.spendwise.server

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spendwise.MainActivity
import com.spendwise.R
import com.spendwise.SpendWiseApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DashboardServerService : Service() {

    @Inject
    lateinit var dashboardServer: DashboardServer

    private val binder = LocalBinder()
    private var serverUrl: String? = null

    inner class LocalBinder : Binder() {
        fun getService(): DashboardServerService = this@DashboardServerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        serverUrl = dashboardServer.start()

        if (serverUrl != null) {
            val notification = createNotification(serverUrl!!)
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Server started at $serverUrl")
        } else {
            Log.e(TAG, "Failed to start server")
            stopSelf()
        }
    }

    private fun stopServer() {
        dashboardServer.stop()
        serverUrl = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Server stopped")
    }

    private fun createNotification(url: String): Notification {
        // Intent to open app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop server
        val stopIntent = Intent(this, DashboardServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SpendWiseApp.CHANNEL_SERVER)
            .setContentTitle("Dashboard Server Running")
            .setContentText("Access at $url")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    fun getServerUrl(): String? = serverUrl

    fun isRunning(): Boolean = dashboardServer.isRunning()

    override fun onDestroy() {
        dashboardServer.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DashboardServerService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.spendwise.action.START_SERVER"
        const val ACTION_STOP = "com.spendwise.action.STOP_SERVER"
    }
}
