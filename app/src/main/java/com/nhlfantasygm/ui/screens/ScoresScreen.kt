package com.nhlfantasygm.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.nhlfantasygm.data.local.LiveNhlGameEntity
import com.nhlfantasygm.data.remote.GameWeekDayDto
import com.nhlfantasygm.model.DataFreshnessState
import com.nhlfantasygm.ui.components.*
import com.nhlfantasygm.ui.theme.*

@Composable
fun ScoresScreen(
    liveGames: List<LiveNhlGameEntity>,
    gameWeek: List<GameWeekDayDto> = emptyList(),
    selectedDate: String? = null,
    todayDate: String = "",
    pinnedGameIds: Set<Long> = emptySet(),
    isSyncing: Boolean,
    freshness: DataFreshnessState,
    onRefreshScores: () -> Unit,
    onSelectDate: (String?) -> Unit = {},
    onTogglePinGame: (LiveNhlGameEntity) -> Unit = {},
    onUnpinAllGames: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingGameToPin by remember { mutableStateOf<LiveNhlGameEntity?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingGameToPin?.let { onTogglePinGame(it) }
        }
        pendingGameToPin = null
    }

    val handlePinClick: (LiveNhlGameEntity) -> Unit = { game ->
        val isCurrentlyPinned = pinnedGameIds.contains(game.gameId)
        if (isCurrentlyPinned || !game.isFinal) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isCurrentlyPinned) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    pendingGameToPin = game
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onTogglePinGame(game)
                }
            } else {
                onTogglePinGame(game)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E17)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Info
        item {
            val isViewingToday = selectedDate == null || selectedDate == todayDate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isViewingToday) "Today's NHL Scoreboard" else "NHL Scoreboard • ${formatDateHeader(selectedDate ?: "")}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isViewingToday) "Pin live games to Android notifications & lockscreen (Android 12+)"
                        else "Viewing past/upcoming schedule • Tap TODAY to return",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isViewingToday) Color(0xFF7E8B9F) else Color(0xFF00E5FF)
                    )
                }

                IconButton(
                    onClick = onRefreshScores,
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF00E5FF),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Scores",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }

        // Multi-day Date Selector Strip
        if (gameWeek.isNotEmpty()) {
            item {
                val activeDate = selectedDate ?: todayDate.ifBlank {
                    try {
                        java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")).toString()
                    } catch (_: Exception) {
                        ""
                    }
                }
                val currentIndex = gameWeek.indexOfFirst { it.date == activeDate }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Prev Button [<]
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF131D31),
                        border = BorderStroke(1.dp, Color(0xFF223555)),
                        modifier = Modifier.bouncingClickable {
                            val prevDate = if (currentIndex > 0) {
                                gameWeek[currentIndex - 1].date
                            } else {
                                try {
                                    java.time.LocalDate.parse(activeDate).minusDays(1).toString()
                                } catch (_: Exception) { null }
                            }
                            if (prevDate != null) onSelectDate(prevDate)
                        }
                    ) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Day",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Horizontal Scrollable Day Chips
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(gameWeek, key = { it.date }) { day ->
                            val isSelected = (day.date == activeDate)
                            val isToday = (day.date == todayDate)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    isSelected -> Color(0xFF00E5FF).copy(alpha = 0.16f)
                                    isToday -> Color(0xFF1E293B)
                                    else -> Color(0xFF131D31)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected -> Color(0xFF00E5FF)
                                        isToday -> Color(0xFF38BDF8).copy(alpha = 0.6f)
                                        else -> Color(0xFF223555)
                                    }
                                ),
                                modifier = Modifier.bouncingClickable {
                                    if (isToday) onSelectDate(null) else onSelectDate(day.date)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (isToday) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF38BDF8))
                                        )
                                    }

                                    Text(
                                        text = if (isToday) "TODAY" else day.dayAbbrev,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Bold,
                                        color = when {
                                            isSelected -> Color(0xFF00E5FF)
                                            isToday -> Color(0xFF38BDF8)
                                            else -> Color(0xFF94A3B8)
                                        }
                                    )

                                    Text(
                                        text = formatShortDate(day.date),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White else Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )

                                    if (day.numberOfGames > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF0B1220),
                                            modifier = Modifier.padding(start = 2.dp)
                                        ) {
                                            Text(
                                                text = "${day.numberOfGames}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF7E8B9F),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Next Button [>]
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF131D31),
                        border = BorderStroke(1.dp, Color(0xFF223555)),
                        modifier = Modifier.bouncingClickable {
                            val nextDate = if (currentIndex in 0 until gameWeek.size - 1) {
                                gameWeek[currentIndex + 1].date
                            } else {
                                try {
                                    java.time.LocalDate.parse(activeDate).plusDays(1).toString()
                                } catch (_: Exception) { null }
                            }
                            if (nextDate != null) onSelectDate(nextDate)
                        }
                    ) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Day",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Pinned Games Banner (Now Bar Active)
        if (pinnedGameIds.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF064E3B).copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, ProfitEmerald.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ProfitEmerald)
                            )
                            Text(
                                text = "📌 ${pinnedGameIds.size} Game${if (pinnedGameIds.size > 1) "s" else ""} Pinned to Live Notifications",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = ProfitEmerald
                            )
                        }

                        TextButton(
                            onClick = onUnpinAllGames,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Unpin All",
                                style = MaterialTheme.typography.labelSmall,
                                color = AlertRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (liveGames.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF131D31),
                    border = BorderStroke(1.dp, Color(0xFF223555)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🏒", fontSize = 36.sp)
                        val isToday = selectedDate == null || selectedDate == todayDate
                        Text(
                            text = if (isToday) "No Games Scheduled Today" else "No Games Scheduled (${selectedDate ?: ""})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap refresh or choose another date to check NHL schedules and live updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7E8B9F),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRefreshScores,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF001F29)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sync Live Scores", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(liveGames, key = { it.gameId }) { game ->
                val isPinned = pinnedGameIds.contains(game.gameId)
                NhlGameScorecard(
                    game = game,
                    isPinned = isPinned,
                    onTogglePin = { handlePinClick(game) }
                )
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NhlGameScorecard(
    game: LiveNhlGameEntity,
    isPinned: Boolean,
    onTogglePin: () -> Unit
) {
    val isLive = game.gameState.equals("LIVE", ignoreCase = true) || game.gameState.equals("CRIT", ignoreCase = true)
    val isFinal = game.gameState.equals("OFF", ignoreCase = true) || game.gameState.equals("FINAL", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF131D31),
        border = BorderStroke(
            1.dp,
            when {
                isPinned -> ProfitEmerald.copy(alpha = 0.6f)
                isLive -> ProfitEmerald.copy(alpha = 0.35f)
                else -> Color(0xFF223555)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Game Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLive) {
                        LivePulseDot(color = ProfitEmerald, size = 8.dp)
                    }
                    Text(
                        text = when {
                            isLive -> "LIVE • ${if (game.periodLabel.isNotBlank()) game.periodLabel else "In Progress"} ${game.clockLabel}"
                            isFinal -> "FINAL"
                            else -> formatCardStartTime(game.startTimeUtc)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            isLive -> ProfitEmerald
                            isFinal -> Color(0xFF94A3B8)
                            else -> Color(0xFF00E5FF)
                        }
                    )
                }

                Text(
                    text = game.gameDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            }

            // Teams & Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Away Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    NhlTeamBadge(abbrev = game.awayAbbrev, logoUrl = game.awayLogoUrl, size = 32.dp)
                    Column {
                        Text(
                            text = game.awayAbbrev,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${game.awaySog} SOG",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7E8B9F)
                        )
                    }
                }

                // Scores
                Text(
                    text = "${game.awayScore}  -  ${game.homeScore}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                // Home Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = game.homeAbbrev,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${game.homeSog} SOG",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7E8B9F)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    NhlTeamBadge(abbrev = game.homeAbbrev, logoUrl = game.homeLogoUrl, size = 32.dp)
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // Footer: Venue / Arena and Pin to Now Bar Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = game.venueName.takeIf { it.isNotBlank() } ?: "NHL Regular Season",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(Modifier.width(8.dp))

                // Pin to Notifications / Unpin Button or Completed Badge
                if (isFinal && !isPinned) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Final • Over",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                } else {
                    val pinScale by animateFloatAsState(
                        targetValue = if (isPinned) 1.08f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "pinScale"
                    )
                    val pinColor by animateColorAsState(
                        targetValue = if (isPinned) ProfitEmerald else Color(0xFF00E5FF),
                        animationSpec = tween(180),
                        label = "pinColor"
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPinned) ProfitEmerald.copy(alpha = 0.18f) else Color(0xFF1E293B),
                        border = BorderStroke(
                            1.dp,
                            if (isPinned) ProfitEmerald else Color(0xFF00E5FF).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.bouncingClickable { onTogglePin() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) "Unpin Live Score" else "Pin Live Score",
                                tint = pinColor,
                                modifier = Modifier
                                    .size(13.dp)
                                    .scale(pinScale)
                            )
                            Text(
                                text = if (isPinned) "Pinned • Live" else "Pin Live Score",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = pinColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatCardStartTime(isoUtc: String): String {
    if (isoUtc.isBlank()) return "SCHEDULED"
    return try {
        val instant = java.time.Instant.parse(isoUtc)
        val localTime = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
        localTime.format(formatter)
    } catch (e: Exception) {
        isoUtc.take(16).replace("T", " ")
    }
}

private fun formatShortDate(dateStr: String): String {
    return try {
        val date = java.time.LocalDate.parse(dateStr)
        date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))
    } catch (_: Exception) {
        dateStr.takeLast(5)
    }
}

private fun formatDateHeader(dateStr: String): String {
    return try {
        val date = java.time.LocalDate.parse(dateStr)
        date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", java.util.Locale.US))
    } catch (_: Exception) {
        dateStr
    }
}
