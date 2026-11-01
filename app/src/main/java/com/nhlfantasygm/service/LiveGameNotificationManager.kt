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
import java.util.concurrent.ConcurrentHashMap

data class LiveGameSnapshot(
    val gameState: String,
    val periodLabel: String,
    val clockLabel: String,
    val awayScore: Int,
    val homeScore: Int,
    val awaySog: Int,
    val homeSog: Int,
    val isLive: Boolean,
    val isFinal: Boolean
) {
    companion object {
        fun from(game: LiveNhlGameEntity): LiveGameSnapshot = LiveGameSnapshot(
            gameState = game.gameState,
            periodLabel = game.periodLabel,
            clockLabel = game.clockLabel,
            awayScore = game.awayScore,
            homeScore = game.homeScore,
            awaySog = game.awaySog,
            homeSog = game.homeSog,
            isLive = game.isLive,
            isFinal = game.isFinal
        )
    }
}

class LiveGameNotificationManager(
    private val context: Context,
    private val repository: NhlFantasyRepository
) {

    companion object {
        const val LIVE_GAMES_CHANNEL_ID = "nhl_live_games_now_bar"
        const val LIVE_GAMES_CHANNEL_NAME = "Live NHL Games & Now Bar"
        const val NOTIFICATION_ID_BASE = 50000
        const val FOREGROUND_FALLBACK_ID = 49999
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _pinnedGameIds = MutableStateFlow<Set<Long>>(emptySet())
    val pinnedGameIds: StateFlow<Set<Long>> = _pinnedGameIds.asStateFlow()

    private val lastNotifiedSnapshots = ConcurrentHashMap<Long, LiveGameSnapshot>()
    private val lastKnownGameEntities = ConcurrentHashMap<Long, LiveNhlGameEntity>()

    private var activePollingJob: Job? = null
    private var observeJob: Job? = null

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
                description = "Live NHL scoreboard, period clock, and Now Bar activity"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

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
        lastKnownGameEntities[game.gameId] = game
        postOrUpdateNotification(game, ctx, force = true)
        LiveGameForegroundService.start(ctx, game.gameId)
        startObservingIfPinned()
        checkAndStartLivePolling()
    }

    fun unpinGame(gameId: Long, ctx: Context = context) {
        val updated = _pinnedGameIds.value - gameId
        _pinnedGameIds.value = updated
        lastNotifiedSnapshots.remove(gameId)
        lastKnownGameEntities.remove(gameId)
        notificationManager.cancel(getNotificationId(gameId))

        if (updated.isEmpty()) {
            LiveGameForegroundService.stop(ctx)
        }
        startObservingIfPinned()
        checkAndStartLivePolling()
    }

    fun unpinAll(ctx: Context = context) {
        _pinnedGameIds.value.forEach { id ->
            notificationManager.cancel(getNotificationId(id))
        }
        _pinnedGameIds.value = emptySet()
        lastNotifiedSnapshots.clear()
        lastKnownGameEntities.clear()
        LiveGameForegroundService.stop(ctx)
        observeJob?.cancel()
        observeJob = null
        activePollingJob?.cancel()
        activePollingJob = null
    }

    fun forceRefreshGame(gameId: Long) {
        lastKnownGameEntities[gameId]?.let { game ->
            postOrUpdateNotification(game, context, force = true)
        }
    }

    fun updateNotificationsForGames(games: List<LiveNhlGameEntity>) {
        val pinned = _pinnedGameIds.value
        if (pinned.isEmpty()) return

        games.filter { it.gameId in pinned }.forEach { game ->
            lastKnownGameEntities[game.gameId] = game
            postOrUpdateNotification(game, context, force = false)
        }
        checkAndStartLivePolling(games)
    }

    fun postOrUpdateNotification(game: LiveNhlGameEntity, ctx: Context, force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        val snapshot = LiveGameSnapshot.from(game)
        val previousSnapshot = lastNotifiedSnapshots[game.gameId]

        // State-delta deduplication: if game data hasn't changed, skip notify() completely
        // to prevent notification flickering or 30-second re-alert spam.
        if (!force && previousSnapshot == snapshot) {
            return
        }

        lastNotifiedSnapshots[game.gameId] = snapshot
        val notification = buildNotification(game, ctx)
        notificationManager.notify(getNotificationId(game.gameId), notification)
    }

    val primaryNotificationId: Int
        get() = _pinnedGameIds.value.firstOrNull()?.let { getNotificationId(it) } ?: FOREGROUND_FALLBACK_ID

    fun buildPrimaryForegroundNotification(): Notification {
        val firstGameId = _pinnedGameIds.value.firstOrNull()
        val game = firstGameId?.let { lastKnownGameEntities[it] }
        return if (game != null) {
            buildNotification(game, context)
        } else {
            buildFallbackNotification(context)
        }
    }

    fun buildNotification(game: LiveNhlGameEntity, ctx: Context): Notification {
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

        val text = when {
            game.isLive -> "SOG: ${game.awayAbbrev} ${game.awaySog} - ${game.homeAbbrev} ${game.homeSog} • ${game.venueName}"
            game.isFinal -> "Final • SOG: ${game.awayAbbrev} ${game.awaySog} - ${game.homeAbbrev} ${game.homeSog}"
            else -> "Starts: ${game.startTimeUtc} • ${game.venueName}"
        }

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

        val builder = NotificationCompat.Builder(ctx, LIVE_GAMES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_puck)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Live NHL • Now Bar")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_live_puck, "Open Game", contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Unpin", unpinPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Refresh", refreshPendingIntent)

        // Android 16 & Samsung One UI 8 Promoted Ongoing Live Notification extras
        builder.extras.putBoolean("android.requestPromotedOngoing", true)

        return builder.build()
    }

    private fun buildFallbackNotification(ctx: Context): Notification {
        val contentIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TAB", "SCORES")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            ctx,
            FOREGROUND_FALLBACK_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(ctx, LIVE_GAMES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_puck)
            .setContentTitle("🏒 NHL Live Scores Active")
            .setContentText("Keeping pinned live games updated on Now Bar")
            .setSubText("Live NHL • Now Bar")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        builder.extras.putBoolean("android.requestPromotedOngoing", true)
        return builder.build()
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

    fun getNotificationId(gameId: Long): Int {
        return (NOTIFICATION_ID_BASE + (gameId % 100000L).toInt())
    }
}
