package com.nhlfantasygm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nhlfantasygm.model.AppThemeMode

val IceCyan = Color(0xFF00E5FF)
val DeepCyan = Color(0xFF0284C7)
val RoyalBlue = Color(0xFF3B82F6)
val ProfitEmerald = Color(0xFF10B981)
val TrophyGold = Color(0xFFF59E0B)
val AlertRed = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF97316)
val SleeperViolet = Color(0xFF8B5CF6)

private val DarkRinkColorScheme = darkColorScheme(
    primary = IceCyan,
    onPrimary = Color(0xFF001F29),
    primaryContainer = Color(0xFF0D2D44),
    onPrimaryContainer = Color(0xFFB8EEFF),
    secondary = SleeperViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF231845),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = TrophyGold,
    onTertiary = Color(0xFF261600),
    tertiaryContainer = Color(0xFF3B2806),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF0A0F1D),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF131D31),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1A2640),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF223555),
    error = AlertRed,
    onError = Color.White
)

private val IceArenaLightColorScheme = lightColorScheme(
    primary = DeepCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF1D4ED8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White
)

val HockeyTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp
    )
)

private fun ColorScheme.applyLiquidGlass(isDark: Boolean): ColorScheme {
    return if (isDark) {
        copy(
            background = Color(0xFF070C17),
            surface = surface.copy(alpha = 0.64f),
            surfaceVariant = surfaceVariant.copy(alpha = 0.54f),
            primaryContainer = primaryContainer.copy(alpha = 0.72f),
            secondaryContainer = secondaryContainer.copy(alpha = 0.72f),
            outline = primary.copy(alpha = 0.44f)
        )
    } else {
        copy(
            surface = surface.copy(alpha = 0.76f),
            surfaceVariant = surfaceVariant.copy(alpha = 0.66f),
            primaryContainer = primaryContainer.copy(alpha = 0.78f),
            secondaryContainer = secondaryContainer.copy(alpha = 0.78f),
            outline = primary.copy(alpha = 0.40f)
        )
    }
}

@Composable
fun NhlFantasyGmTheme(
    themeMode: AppThemeMode = AppThemeMode.LIQUID_MATERIAL_YOU,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        else -> true
    }

    val baseScheme: ColorScheme = if (themeMode.usesMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }.getOrNull() ?: if (useDark) DarkRinkColorScheme else IceArenaLightColorScheme
    } else {
        if (useDark) DarkRinkColorScheme else IceArenaLightColorScheme
    }

    val finalColors = if (themeMode.usesLiquidGlass) {
        baseScheme.applyLiquidGlass(isDark = useDark)
    } else {
        baseScheme
    }

    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides themeMode.usesLiquidGlass,
        LocalMaterialYouEnabled provides themeMode.usesMaterialYou
    ) {
        MaterialTheme(
            colorScheme = finalColors,
            typography = HockeyTypography,
            content = content
        )
    }
}
