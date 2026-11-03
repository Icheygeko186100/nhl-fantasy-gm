package com.nhlfantasygm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhlfantasygm.data.local.FantasyTeamEntity
import com.nhlfantasygm.model.TeamEmblem
import com.nhlfantasygm.ui.theme.*

enum class SleeperLeagueSubTab(val label: String) {
    MATCH("MATCH"),
    TEAM("TEAM"),
    PLAYERS("PLAYERS"),
    LEAGUE("LEAGUE")
}

/**
 * Clean Sleeper League Header matching the user's reference UI.
 *
 * Top row:
 *   [<]  [Emblem]  League Title                      [⚙️]
 *
 * Tab row:
 *   MATCH        TEAM        PLAYERS        LEAGUE
 *   (with smooth animated underline indicator and spring scale on the active tab)
 *
 * Built with statusBarsPadding() so it is strictly protected from notch / status bar overlap on
 * Samsung Galaxy S22 Ultra and Android 16.
 */
@Composable
fun SleeperLeagueHeader(
    leagueTitle: String,
    team: FantasyTeamEntity?,
    activeSubTab: SleeperLeagueSubTab,
    onSelectSubTab: (SleeperLeagueSubTab) -> Unit,
    onOpenSettings: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = Color(0xFF0D1424),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Back, Crest, League Title, and Settings Cog
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    } else {
                        Spacer(Modifier.width(4.dp))
                    }

                    FranchiseCrestBadge(
                        emblem = TeamEmblem.fromId(team?.emblemId),
                        primaryColorHex = team?.primaryColorHex ?: 0xFF00E5FF,
                        secondaryColorHex = team?.secondaryColorHex ?: 0xFF001F29,
                        abbreviation = team?.abbreviation ?: "GM",
                        size = 32.dp
                    )

                    Text(
                        text = leagueTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Row 2: Sleeper Sub-Nav Tabs (MATCH | TEAM | PLAYERS | LEAGUE)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SleeperLeagueSubTab.entries.forEach { tab ->
                    val isSelected = tab == activeSubTab
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color(0xFF7E8B9F),
                        animationSpec = tween(180),
                        label = "tabText"
                    )
                    val textScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tabScale"
                    )
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (isSelected) 38.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tabIndicatorWidth"
                    )
                    val indicatorAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = tween(180),
                        label = "tabIndicatorAlpha"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectSubTab(tab) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = textColor,
                            letterSpacing = 1.sp,
                            modifier = Modifier.scale(textScale)
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .width(indicatorWidth)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = indicatorAlpha))
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF1E293B),
                thickness = 1.dp
            )
        }
    }
}

/**
 * Standard Header for non-fantasy screens (Home, Scores, Profile).
 * Protected by statusBarsPadding().
 */
@Composable
fun SleeperStandardHeader(
    title: String,
    subtitle: String? = null,
    team: FantasyTeamEntity? = null,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = Color(0xFF0D1424),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (team != null) {
                        FranchiseCrestBadge(
                            emblem = TeamEmblem.fromId(team.emblemId),
                            primaryColorHex = team.primaryColorHex,
                            secondaryColorHex = team.secondaryColorHex,
                            abbreviation = team.abbreviation,
                            size = 32.dp
                        )
                    }

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7E8B9F),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            HorizontalDivider(
                color = Color(0xFF1E293B),
                thickness = 1.dp
            )
        }
    }
}
