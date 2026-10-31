package com.nhlfantasygm.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nhlfantasygm.MainActivity
import com.nhlfantasygm.R
import com.nhlfantasygm.data.local.LiveNhlGameEntity
import com.nhlfantasygm.data.repository.NhlFantasyRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveGameNotificationManager(
    private val context: Context,
    private val repository: NhlFantasyRepository
) {

    companion object {
        const val LIVE_GAMES_CHANNEL_ID = "nhl_live_games_now_bar"
        const val LIVE_GAMES_CHANNEL_NAME = "Live NHL Games & Now Bar"
        private const val NOTIFICATION_ID_BASE = 50000
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _pinnedGameIds = MutableStateFlow<Set<Long>>(emptySet())
    val pinnedGameIds: StateFlow<Set<Long>> = _pinnedGameIds.asStateFlow()

    private var activePollingJob: Job? = null

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LIVE_GAMES_CHANNEL_ID,
                LIVE_GAMES_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pinned live game scoreboards, period clocks, and shots on goal"
                enableLights(true)
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private var observeJob: Job? = null

    private fun startObservingIfPinned() {
        if (_pinnedGameIds.value.isEmpty()) {
            observeJob?.cancel()
            observeJob = null
            return
        }
        if (observeJob?.isActive == true) return

        observeJob = scope.launch(Dispatchers.IO) {
            try {
                repository.liveGamesFlow.collect { games ->
                    if (_pinnedGameIds.value.isNotEmpty()) {
                        updateNotificationsForGames(games)
                    }
                }
            } catch (e: CancellationException) {
                // Expected when job cancelled
            } catch (t: Throwable) {
                // Safely ignore DB closure on app/test teardown
            }
        }
    }

    fun isGamePinned(gameId: Long): Boolean {
        return _pinnedGameIds.value.contains(gameId)
    }

    fun togglePin(game: LiveNhlGameEntity): Boolean {
        return if (isGamePinned(game.gameId)) {
            unpinGame(game.gameId, context)
            false
        } else {
            pinGame(game, context)
            true
        }
    }

    fun pinGame(game: LiveNhlGameEntity, ctx: Context = context) {
        val updated = _pinnedGameIds.value + game.gameId
        _pinnedGameIds.value = updated
        postOrUpdateNotification(game, ctx)
        startObservingIfPinned()
        checkAndStartLivePolling()
    }

    fun unpinGame(gameId: Long, ctx: Context = context) {
        val updated = _pinnedGameIds.value - gameId
        _pinnedGameIds.value = updated
        notificationManager.cancel(getNotificationId(gameId))
        startObservingIfPinned()
        checkAndStartLivePolling()
    }

    fun unpinAll(ctx: Context = context) {
        _pinnedGameIds.value.forEach { id ->
            notificationManager.cancel(getNotificationId(id))
        }
        _pinnedGameIds.value = emptySet()
        observeJob?.cancel()
        observeJob = null
        activePollingJob?.cancel()
        activePollingJob = null
    }

    fun updateNotificationsForGames(games: List<LiveNhlGameEntity>) {
        val pinned = _pinnedGameIds.value
        if (pinned.isEmpty()) return

        games.filter { it.gameId in pinned }.forEach { game ->
            postOrUpdateNotification(game, context)
        }
        checkAndStartLivePolling(games)
    }

    fun postOrUpdateNotification(game: LiveNhlGameEntity, ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        // Tap on notification -> Opens Scores in MainActivity
        val contentIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TAB", "SCORES")
            putExtra("GAME_ID", game.gameId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            ctx,
            getNotificationId(game.gameId),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Unpin
        val unpinIntent = Intent(ctx, LiveGameNotificationReceiver::class.java).apply {
            action = LiveGameNotificationReceiver.ACTION_UNPIN_GAME
            putExtra(LiveGameNotificationReceiver.EXTRA_GAME_ID, game.gameId)
        }
        val unpinPendingIntent = PendingIntent.getBroadcast(
            ctx,
            (getNotificationId(game.gameId) + 1000),
            unpinIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Refresh
        val refreshIntent = Intent(ctx, LiveGameNotificationReceiver::class.java).apply {
            action = LiveGameNotificationReceiver.ACTION_REFRESH_GAME
            putExtra(LiveGameNotificationReceiver.EXTRA_GAME_ID, game.gameId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            ctx,
            (getNotificationId(game.gameId) + 2000),
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            game.isLive -> "🔴 LIVE ${game.periodLabel} ${game.clockLabel} • ${game.awayAbbrev} ${game.awayScore} - ${game.homeScore} ${game.homeAbbrev}"
            game.isFinal -> "🏁 FINAL • ${game.awayAbbrev} ${game.awayScore} - ${game.homeScore} ${game.homeAbbrev}"
            else -> "🏒 ${game.awayAbbrev} @ ${game.homeAbbrev} • ${game.startTimeUtc.takeIf { it.isNotBlank() } ?: "Scheduled"}"
        }

        val text = "SOG: ${game.awayAbbrev} ${game.awaySog} - ${game.homeSog} ${game.homeAbbrev} • ${game.venueName}"

        val bigText = buildString {
            append("${game.awayName} (${game.awayAbbrev}): ${game.awayScore}   [${game.awaySog} SOG]\n")
            append("${game.homeName} (${game.homeAbbrev}): ${game.homeScore}   [${game.homeSog} SOG]\n")
            val status = when {
                game.isLive -> "LIVE: ${game.periodLabel} (${game.clockLabel} remaining)"
                game.isFinal -> "FINAL SCORE"
                else -> "START TIME: ${game.startTimeUtc.takeIf { it.isNotBlank() } ?: "TBD"}"
            }
            append("Status: $status\n")
            if (game.venueName.isNotBlank()) {
                append("Arena: ${game.venueName}")
            }
        }

        val notification = NotificationCompat.Builder(ctx, LIVE_GAMES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_puck)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Live NHL • Now Bar")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_live_puck, "Open Game", contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Unpin", unpinPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Refresh", refreshPendingIntent)
            .build()

        notificationManager.notify(getNotificationId(game.gameId), notification)
    }

    private fun checkAndStartLivePolling(cachedGames: List<LiveNhlGameEntity>? = null) {
        val pinned = _pinnedGameIds.value
        if (pinned.isEmpty()) {
            activePollingJob?.cancel()
            activePollingJob = null
            return
        }

        if (activePollingJob?.isActive == true) return

        activePollingJob = scope.launch(Dispatchers.IO) {
            while (isActive && _pinnedGameIds.value.isNotEmpty()) {
                delay(30_000L) // Poll live score every 30 seconds while pinned
                if (_pinnedGameIds.value.isNotEmpty()) {
                    runCatching {
                        repository.refreshLiveScoresOnly()
                    }
                }
            }
        }
    }

    private fun getNotificationId(gameId: Long): Int {
        return (NOTIFICATION_ID_BASE + (gameId % 100000L).toInt())
    }
}
