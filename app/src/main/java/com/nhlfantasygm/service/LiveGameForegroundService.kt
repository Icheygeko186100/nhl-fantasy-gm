package com.nhlfantasygm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.nhlfantasygm.NhlFantasyApplication

class LiveGameForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.nhlfantasygm.service.action.START_LIVE_TRACKING"
        const val ACTION_STOP = "com.nhlfantasygm.service.action.STOP_LIVE_TRACKING"
        const val EXTRA_GAME_ID = "extra_game_id"

        fun start(context: Context, gameId: Long = -1L) {
            val intent = Intent(context, LiveGameForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GAME_ID, gameId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Graceful fallback if background start restriction applies
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveGameForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Fallback
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as? NhlFantasyApplication
        val manager = app?.liveGameNotificationManager

        if (intent?.action == ACTION_STOP || manager?.pinnedGameIds?.value?.isEmpty() == true) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        // Build foreground notification for the primary pinned game or fallback
        val notification = manager?.buildPrimaryForegroundNotification()
        if (notification != null) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            try {
                ServiceCompat.startForeground(
                    this,
                    manager.primaryNotificationId,
                    notification,
                    foregroundServiceType
                )
            } catch (e: Exception) {
                // Ignore if foreground permission restrictions apply
            }
        } else {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun stopForegroundCompat() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
