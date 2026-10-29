package com.nhlfantasygm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nhlfantasygm.data.local.PlayerEntity
import com.nhlfantasygm.model.*
import com.nhlfantasygm.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataFreshnessBanner(
    freshness: DataFreshnessState,
    lastSyncEpochMs: Long,
    playerCount: Int,
    errorMessage: String,
    isSyncing: Boolean,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor = when (freshness) {
        DataFreshnessState.LIVE -> ProfitEmerald
        DataFreshnessState.SYNCING -> IceCyan
        DataFreshnessState.CACHED -> TrophyGold
        DataFreshnessState.UNAVAILABLE -> AlertRed
    }

    val timeLabel = if (lastSyncEpochMs > 0L) {
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.CANADA)
        "Updated ${fmt.format(Date(lastSyncEpochMs))} • $playerCount NHL players"
    } else if (isSyncing) {
        "Fetching all 32 NHL rosters, stats, scores & injuries..."
    } else {
        errorMessage.ifBlank { "Tap refresh to sync NHL player database" }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = freshness.badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "• Virtual CAD Simulation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (errorMessage.isNotBlank() && freshness != DataFreshnessState.LIVE && !isSyncing) {
                        Text(
                            text = "Offline / Network Notice: $errorMessage",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.4.dp,
                    color = IceCyan
                )
            } else {
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh NHL Data",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerHeadshot(
    headshotUrl: String,
    fullName: String,
    teamAbbrev: String,
    sweaterNumber: Int,
    size: Dp = 48.dp
) {
    val initials = fullName.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { teamAbbrev.take(2) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (headshotUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(headshotUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = fullName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Text(
                        text = if (sweaterNumber > 0) "#$sweaterNumber" else initials,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                loading = {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        } else {
            Text(
                text = if (sweaterNumber > 0) "#$sweaterNumber" else initials,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun NhlTeamBadge(
    abbrev: String,
    logoUrl: String = "",
    size: Dp = 32.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = abbrev,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                error = {
                    Text(
                        text = abbrev.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        } else {
            Text(
                text = abbrev.take(3),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun FranchiseCrestBadge(
    emblem: TeamEmblem,
    primaryColorHex: Long,
    secondaryColorHex: Long,
    abbreviation: String,
    size: Dp = 48.dp
) {
    val primary = Color(primaryColorHex)
    val secondary = Color(secondaryColorHex)

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(secondary, primary.copy(alpha = 0.35f))
                )
            )
            .border(1.8.dp, primary, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val w = this.size.width
            val h = this.size.height
            // Draw outer hockey crest shield silhouette
            val shield = Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                lineTo(w * 0.90f, h * 0.22f)
                lineTo(w * 0.90f, h * 0.58f)
                cubicTo(w * 0.90f, h * 0.78f, w * 0.68f, h * 0.90f, w * 0.5f, h * 0.95f)
                cubicTo(w * 0.32f, h * 0.90f, w * 0.10f, h * 0.78f, w * 0.10f, h * 0.58f)
                lineTo(w * 0.10f, h * 0.22f)
                close()
            }
            drawPath(shield, color = primary.copy(alpha = 0.22f))
            drawPath(shield, color = primary, style = Stroke(width = 2.5f))

            // Draw emblem-specific geometric motif
            when (emblem) {
                TeamEmblem.CROSSED_STICKS -> {
                    drawLine(primary, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.74f, h * 0.74f), strokeWidth = 3.2f)
                    drawLine(primary, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.26f, h * 0.74f), strokeWidth = 3.2f)
                }
                TeamEmblem.NORTH_STAR, TeamEmblem.MAPLE_SHIELD -> {
                    drawCircle(primary.copy(alpha = 0.45f), radius = w * 0.20f, center = Offset(w * 0.5f, h * 0.5f))
                }
                TeamEmblem.MOUNTAIN_PEAK -> {
                    val peak = Path().apply {
                        moveTo(w * 0.22f, h * 0.72f)
                        lineTo(w * 0.50f, h * 0.28f)
                        lineTo(w * 0.78f, h * 0.72f)
                        close()
                    }
                    drawPath(peak, color = primary.copy(alpha = 0.4f))
                }
                else -> {
                    drawCircle(primary.copy(alpha = 0.3f), radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f))
                }
            }
        }

        val icon = when (emblem) {
            TeamEmblem.MAPLE_SHIELD -> Icons.Default.Shield
            TeamEmblem.CROSSED_STICKS -> Icons.Default.SportsHockey
            TeamEmblem.ARCTIC_WOLF -> Icons.Default.Pets
            TeamEmblem.ROYAL_CROWN -> Icons.Default.EmojiEvents
            TeamEmblem.ICE_STORM -> Icons.Default.Bolt
            TeamEmblem.MOUNTAIN_PEAK -> Icons.Default.Terrain
            TeamEmblem.NORTH_STAR -> Icons.Default.Star
            TeamEmblem.BLAZING_PUCK -> Icons.Default.LocalFireDepartment
            TeamEmblem.GRIZZLY_CLAW -> Icons.Default.Security
            TeamEmblem.FALCON_WING -> Icons.Default.Air
            TeamEmblem.TRIDENT_DEEP -> Icons.Default.Water
            TeamEmblem.FORTRESS_CREST -> Icons.Default.Castle
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = emblem.displayName,
                tint = primary,
                modifier = Modifier.size(size * 0.42f)
            )
            if (size >= 44.dp) {
                Text(
                    text = abbreviation.take(3),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 9.sp
                )
            }
        }
    }
}

@Composable
fun OvrBadge(ovr: Int, modifier: Modifier = Modifier) {
    val bgColor = when {
        ovr >= 90 -> TrophyGold
        ovr >= 85 -> IceCyan
        ovr >= 79 -> ProfitEmerald
        else -> Color(0xFF64748B)
    }
    val textColor = if (ovr >= 85) Color(0xFF07131F) else Color.White

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = "$ovr",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = textColor
        )
    }
}

@Composable
fun PositionPill(position: Position, modifier: Modifier = Modifier) {
    val tint = when (position) {
        Position.CENTER -> IceCyan
        Position.LEFT_WING, Position.RIGHT_WING -> RoyalBlue
        Position.DEFENSE -> ProfitEmerald
        Position.GOALIE -> TrophyGold
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Text(
            text = position.uiShort,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@Composable
fun InjuryBadge(severity: InjurySeverity, statusLabel: String) {
    if (severity == InjurySeverity.HEALTHY) return
    val color = when (severity) {
        InjurySeverity.DAY_TO_DAY -> WarningAmber
        InjurySeverity.OUT, InjurySeverity.INJURED_RESERVE -> AlertRed
        InjurySeverity.HEALTHY -> ProfitEmerald
    }
    val short = when (severity) {
        InjurySeverity.INJURED_RESERVE -> "IR"
        InjurySeverity.OUT -> "OUT"
        InjurySeverity.DAY_TO_DAY -> "DTD"
        InjurySeverity.HEALTHY -> ""
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = short.ifBlank { statusLabel.take(6) },
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Composable
fun ExpensiveTransactionDialog(
    player: PlayerEntity,
    warningText: String,
    isSelling: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = TrophyGold
            )
        },
        title = {
            Text(
                text = if (isSelling) "Confirm Player Sale" else "High-Value Signing Confirmation",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${player.fullName} (${player.teamAbbrev} • ${player.position.uiShort} • ${player.overallRating} OVR)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "All amounts are fictional Virtual CAD game currency.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelling) WarningAmber else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isSelling) "Confirm Sale" else "Confirm Signing")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(text = actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
