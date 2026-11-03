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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nhlfantasygm.data.local.LiveNhlGameEntity
import com.nhlfantasygm.model.DataFreshnessState
import com.nhlfantasygm.ui.components.NhlTeamBadge
import com.nhlfantasygm.ui.theme.*

@Composable
fun ScoresScreen(
    liveGames: List<LiveNhlGameEntity>,
    pinnedGameIds: Set<Long> = emptySet(),
    isSyncing: Boolean,
    freshness: DataFreshnessState,
    onRefreshScores: () -> Unit,
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

    val isLiquidGlass = LocalLiquidGlassEnabled.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(if (isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Info
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today's NHL Scoreboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "Pin live games to Android notifications & lockscreen (Android 12+)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7E8B9F)
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
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(RoundedCornerShape(14.dp), 14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🏒", fontSize = 36.sp)
                        Text(
                            text = "No Games Scheduled Today",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap refresh to check for upcoming NHL game schedules and live updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRefreshScores,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.2.dp,
            when {
                isPinned -> ProfitEmerald.copy(alpha = 0.7f)
                isLive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                cornerRadius = 16.dp,
                enableShader = true
            )
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
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ProfitEmerald)
                        )
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
                    LiquidButton(
                        onClick = onTogglePin,
                        shape = RoundedCornerShape(10.dp),
                        containerColor = if (isPinned) ProfitEmerald.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isPinned) ProfitEmerald else MaterialTheme.colorScheme.primary,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, if (isPinned) ProfitEmerald else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "Unpin Live Score" else "Pin Live Score",
                            tint = if (isPinned) ProfitEmerald else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isPinned) "Pinned • Live" else "Pin Live Score",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPinned) ProfitEmerald else MaterialTheme.colorScheme.primary
                        )
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
