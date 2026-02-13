package com.rupeelog.server

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var service: DashboardServerService? = null
    private var isBound = false

    private val _serverState = MutableStateFlow(ServerState())
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as DashboardServerService.LocalBinder
            service = localBinder.getService()
            isBound = true
            updateState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            updateState()
        }
    }

    fun startServer() {
        val intent = Intent(context, DashboardServerService::class.java).apply {
            action = DashboardServerService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        // Bind to get updates
        context.bindService(
            Intent(context, DashboardServerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        _serverState.value = _serverState.value.copy(isStarting = true)
    }

    fun stopServer() {
        val intent = Intent(context, DashboardServerService::class.java).apply {
            action = DashboardServerService.ACTION_STOP
        }
        context.startService(intent)

        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }

        _serverState.value = ServerState(isRunning = false, url = null)
    }

    private fun updateState() {
        _serverState.value = ServerState(
            isRunning = service?.isRunning() ?: false,
            url = service?.getServerUrl(),
            isStarting = false
        )
    }

    fun refreshState() {
        updateState()
    }
}

data class ServerState(
    val isRunning: Boolean = false,
    val url: String? = null,
    val isStarting: Boolean = false
)
