package com.nhlfantasygm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nhlfantasygm.NhlFantasyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LiveGameNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_UNPIN_GAME = "com.nhlfantasygm.ACTION_UNPIN_GAME"
        const val ACTION_REFRESH_GAME = "com.nhlfantasygm.ACTION_REFRESH_GAME"
        const val EXTRA_GAME_ID = "extra_game_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? NhlFantasyApplication ?: return
        val gameId = intent?.getLongExtra(EXTRA_GAME_ID, -1L) ?: -1L

        when (intent?.action) {
            ACTION_UNPIN_GAME -> {
                if (gameId > 0) {
                    app.liveGameNotificationManager.unpinGame(gameId, context)
                }
            }
            ACTION_REFRESH_GAME -> {
                CoroutineScope(Dispatchers.IO).launch {
                    app.repository.refreshLiveScoresOnly()
                    if (gameId > 0) {
                        app.liveGameNotificationManager.forceRefreshGame(gameId)
                    }
                }
            }
        }
    }
}
