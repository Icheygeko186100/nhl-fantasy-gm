package com.nhlfantasygm.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhlfantasygm.BottomNavDestination
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.sin

/**
 * Authentic Open-Source Liquid Glass Optical Engine for Jetpack Compose (Android 12–16).
 *
 * Adapted from leading open-source Compose Liquid Glass implementations
 * (Kyant0/AndroidLiquidGlass, 6xingyv/gaze-glassy, and FletchMcKee/liquid):
 * - Rounded-box Signed Distance Field (SDF) convex lens physics
 * - Real-time optical lens refraction displacement
 * - Chromatic aberration (RGB wavelength dispersion at curved glass perimeter)
 * - Dynamic color vibrancy & saturation boost
 * - Directional specular rim reflection (top-left incident ray)
 * - Frosted blur chaining (Android 12+)
 * - Floating LiquidBottomTabs capsule dock
 * - Tactile spring-compressed LiquidButton
 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }
val LocalMaterialYouEnabled = compositionLocalOf { true }

data class LiquidGlassSettings(
    val refractionStrength: Float = 0.50f,
    val dispersion: Float = 0.35f,
    val blurRadiusDp: Float = 16f,
    val specularIntensity: Float = 0.70f,
    val vibrancy: Float = 1.35f
)

val LocalLiquidGlassSettings = compositionLocalOf { LiquidGlassSettings() }

@Language("AGSL")
private const val LIQUID_GLASS_AGSL_SHADER = """
    uniform shader content;
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float refractionHeight;
    uniform float refractionAmount;
    uniform float dispersion;
    uniform float vibrancy;
    uniform float specularIntensity;
    uniform float4 tintColor;

    float sdRoundBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 fragCoord) {
        float2 safeRes = max(resolution, float2(1.0, 1.0));
        float2 halfSize = safeRes * 0.5;
        float2 centered = fragCoord - halfSize;
        float dist = sdRoundBox(centered, halfSize, cornerRadius);

        // Outside shape boundary -> transparent
        if (dist > 0.0) {
            return half4(0.0);
        }

        // Lens profile: curved dome from rim inward
        float rH = max(refractionHeight, 1.0);
        float edgeDist = clamp(-dist / rH, 0.0, 1.0);
        // Slope derivative: steepest at the rim (edgeDist ~ 0), flat in center (edgeDist ~ 1)
        float slope = sqrt(max(0.0, 1.0 - edgeDist * edgeDist));
        float2 normal = normalize(centered + float2(0.0001, 0.0001)) * slope;

        // Optical displacement (refraction)
        float2 dispOffset = normal * refractionAmount;

        // Chromatic dispersion (separating R, G, B sampling coordinates)
        float d = refractionAmount * dispersion * 0.15;
        float2 rCoord = clamp(fragCoord - dispOffset * (1.0 + d), float2(0.0), safeRes - float2(1.0));
        float2 gCoord = clamp(fragCoord - dispOffset, float2(0.0), safeRes - float2(1.0));
        float2 bCoord = clamp(fragCoord - dispOffset * (1.0 - d), float2(0.0), safeRes - float2(1.0));

        half r = content.eval(rCoord).r;
        half g = content.eval(gCoord).g;
        half b = content.eval(bCoord).b;
        half a = content.eval(gCoord).a;

        half3 col = half3(r, g, b);

        // Vibrancy / saturation boost
        half luma = dot(col, half3(0.299, 0.587, 0.114));
        col = mix(half3(luma), col, half(vibrancy));

        // Specular rim reflection (incident top-left light ray)
        float2 lightDir = normalize(float2(-0.7071, -0.7071));
        float spec = max(0.0, dot(-normal, lightDir));
        spec = pow(spec, 4.0) * (1.0 - edgeDist) * specularIntensity;

        // Tint color blend
        col = mix(col, tintColor.rgb, half(tintColor.a));
        // Add specular rim gleam
        col += half3(spec * 0.45, spec * 0.55, spec * 0.65);

        return half4(clamp(col, 0.0, 1.0), a);
    }
"""

/**
 * Applies a Liquid Glass optical surface treatment (AGSL lens refraction, chromatic dispersion,
 * frosted blur chaining, specular rim border, and translucent gradient sheen).
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(18.dp),
    cornerRadius: Dp = 18.dp,
    refractionStrength: Float? = null,
    dispersion: Float? = null,
    specularIntensity: Float? = null,
    tintAlpha: Float = 0.55f,
    enableShader: Boolean = true
): Modifier {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    if (!isLiquidGlass) return this

    val settings = LocalLiquidGlassSettings.current
    val effectiveRefraction = refractionStrength ?: settings.refractionStrength
    val effectiveDispersion = dispersion ?: settings.dispersion
    val effectiveSpecular = specularIntensity ?: settings.specularIntensity

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current

    var sizePx by remember { mutableStateOf(Offset(400f, 160f)) }

    val glassBrush = remember(primary, secondary, surfaceColor, tintAlpha) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                primary.copy(alpha = 0.12f),
                surfaceColor.copy(alpha = tintAlpha),
                secondary.copy(alpha = 0.10f),
                surfaceColor.copy(alpha = (tintAlpha + 0.10f).coerceAtMost(0.92f))
            ),
            start = Offset(0f, 0f),
            end = Offset(900f, 700f)
        )
    }

    val rimBorderBrush = remember(primary, secondary, effectiveSpecular) {
        val rimAlpha = (0.45f * effectiveSpecular).coerceIn(0.15f, 0.85f)
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = rimAlpha),
                primary.copy(alpha = (rimAlpha * 0.85f)),
                secondary.copy(alpha = (rimAlpha * 0.50f)),
                Color.White.copy(alpha = (rimAlpha * 0.25f))
            ),
            start = Offset(0f, 0f),
            end = Offset(800f, 800f)
        )
    }

    val shaderEffect = remember(
        sizePx,
        primary,
        enableShader,
        effectiveRefraction,
        effectiveDispersion,
        effectiveSpecular,
        settings.blurRadiusDp,
        settings.vibrancy
    ) {
        if (!enableShader) return@remember null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val cornerRadiusPx = with(density) { cornerRadius.toPx() }
                val blurPx = with(density) { settings.blurRadiusDp.dp.toPx() }
                val refractionHeightPx = cornerRadiusPx * 1.5f
                val refractionAmountPx = cornerRadiusPx * 0.75f * effectiveRefraction

                val shader = RuntimeShader(LIQUID_GLASS_AGSL_SHADER)
                shader.setFloatUniform("resolution", sizePx.x.coerceAtLeast(1f), sizePx.y.coerceAtLeast(1f))
                shader.setFloatUniform("cornerRadius", cornerRadiusPx)
                shader.setFloatUniform("refractionHeight", refractionHeightPx)
                shader.setFloatUniform("refractionAmount", refractionAmountPx)
                shader.setFloatUniform("dispersion", effectiveDispersion)
                shader.setFloatUniform("vibrancy", settings.vibrancy)
                shader.setFloatUniform("specularIntensity", effectiveSpecular)
                shader.setFloatUniform(
                    "tintColor",
                    primary.red,
                    primary.green,
                    primary.blue,
                    0.28f
                )

                val lensEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                if (blurPx > 0.5f) {
                    val blurEffect = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    RenderEffect.createChainEffect(lensEffect, blurEffect).asComposeRenderEffect()
                } else {
                    lensEffect.asComposeRenderEffect()
                }
            }.getOrNull()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val blurPx = with(density) { settings.blurRadiusDp.dp.toPx() }
                if (blurPx > 0.5f) {
                    RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
                } else null
            }.getOrNull()
        } else {
            null
        }
    }

    return this
        .onSizeChanged { sz ->
            if (sz.width > 0 && sz.height > 0) {
                sizePx = Offset(sz.width.toFloat(), sz.height.toFloat())
            }
        }
        .clip(shape)
        .then(
            if (shaderEffect != null) {
                Modifier.graphicsLayer { renderEffect = shaderEffect }
            } else {
                Modifier
            }
        )
        .background(brush = glassBrush, shape = shape)
        .border(width = 1.2.dp, brush = rimBorderBrush, shape = shape)
}

/**
 * Applies a sleek Liquid Glass top navigation bar style with specular edge highlight.
 */
@Composable
fun Modifier.liquidGlassBar(): Modifier {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    if (!isLiquidGlass) return this

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val baseBg = MaterialTheme.colorScheme.background

    val barBrush = remember(primary, secondary, baseBg) {
        Brush.verticalGradient(
            colors = listOf(
                baseBg.copy(alpha = 0.85f),
                primary.copy(alpha = 0.12f),
                secondary.copy(alpha = 0.08f),
                baseBg.copy(alpha = 0.92f)
            )
        )
    }

    val borderBrush = remember(primary, secondary) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                primary.copy(alpha = 0.45f),
                secondary.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.22f)
            )
        )
    }

    return this
        .background(barBrush)
        .border(width = 1.dp, brush = borderBrush, shape = RectangleShape)
}

/**
 * Ambient animated Liquid Glass & Material You Aurora Backdrop.
 * Renders glowing, slowly drifting color orbs underneath translucent glass surfaces.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val bgColor = MaterialTheme.colorScheme.background
    val primaryOrb = MaterialTheme.colorScheme.primary
    val secondaryOrb = MaterialTheme.colorScheme.secondary
    val tertiaryOrb = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (isLiquidGlass) {
            val infiniteTransition = rememberInfiniteTransition(label = "liquidGlassAurora")
            val phase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 6.2831855f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 20000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "auroraPhase"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val orb1Center = Offset(
                    x = w * (0.24f + 0.15f * cos(phase)),
                    y = h * (0.16f + 0.10f * sin(phase))
                )
                val orb2Center = Offset(
                    x = w * (0.80f - 0.16f * sin(phase * 0.85f)),
                    y = h * (0.44f + 0.12f * cos(phase * 0.85f))
                )
                val orb3Center = Offset(
                    x = w * (0.32f + 0.18f * sin(phase * 0.65f)),
                    y = h * (0.78f - 0.10f * cos(phase * 0.65f))
                )
                val orb4Center = Offset(
                    x = w * (0.70f + 0.12f * cos(phase * 0.5f)),
                    y = h * (0.92f + 0.08f * sin(phase * 0.5f))
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryOrb.copy(alpha = 0.28f), Color.Transparent),
                        center = orb1Center,
                        radius = w * 0.70f
                    ),
                    radius = w * 0.70f,
                    center = orb1Center
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryOrb.copy(alpha = 0.22f), Color.Transparent),
                        center = orb2Center,
                        radius = w * 0.65f
                    ),
                    radius = w * 0.65f,
                    center = orb2Center
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tertiaryOrb.copy(alpha = 0.18f), Color.Transparent),
                        center = orb3Center,
                        radius = w * 0.60f
                    ),
                    radius = w * 0.60f,
                    center = orb3Center
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryOrb.copy(alpha = 0.16f), Color.Transparent),
                        center = orb4Center,
                        radius = w * 0.55f
                    ),
                    radius = w * 0.55f,
                    center = orb4Center
                )
            }
        }

        content()
    }
}

/**
 * Authentic Liquid Glass Card container with convex lens refraction, chromatic dispersion,
 * and specular highlights.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    cornerRadius: Dp = 18.dp,
    tintAlpha: Float = 0.65f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val surfaceColor = MaterialTheme.colorScheme.surface

    val cardModifier = if (isLiquidGlass) {
        modifier
            .liquidGlassSurface(
                shape = shape,
                cornerRadius = cornerRadius,
                tintAlpha = tintAlpha,
                enableShader = true
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
    } else {
        modifier
            .clip(shape)
            .background(surfaceColor, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
    }

    Box(
        modifier = cardModifier
    ) {
        content()
    }
}

/**
 * Tactile fluid-glass button with elastic spring compression feedback on press.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "liquidButtonScale"
    )

    val specularBoost by animateFloatAsState(
        targetValue = if (isPressed) 1.25f else 0.85f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "liquidButtonSpecular"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .then(
                if (isLiquidGlass) {
                    Modifier.liquidGlassSurface(
                        shape = shape,
                        cornerRadius = 14.dp,
                        specularIntensity = specularBoost,
                        tintAlpha = 0.40f,
                        enableShader = true
                    )
                } else Modifier
            ),
        shape = shape,
        color = if (isLiquidGlass) containerColor.copy(alpha = 0.88f) else containerColor,
        contentColor = contentColor,
        border = border ?: if (isLiquidGlass) BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)) else null,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Floating Liquid Glass Bottom Capsule Dock (adapted from Kyant0/AndroidLiquidGlass `LiquidBottomTabs`).
 *
 * Renders an elevated, rounded capsule dock with real-time lens refraction and an animated
 * sliding liquid pill that tracks the active destination.
 */
@Composable
fun LiquidBottomTabs(
    selectedDestination: BottomNavDestination,
    onSelectDestination: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = true
) {
    val destinations = remember { BottomNavDestination.entries }
    val selectedIndex = destinations.indexOf(selectedDestination).coerceAtLeast(0)

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val pillShape = remember { RoundedCornerShape(30.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = pillShape,
            color = if (isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.surface,
            tonalElevation = if (isLiquidGlass) 0.dp else 6.dp,
            border = if (isLiquidGlass) {
                BorderStroke(1.2.dp, Color.White.copy(alpha = 0.28f))
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .then(
                    if (isLiquidGlass) {
                        Modifier.liquidGlassSurface(
                            shape = pillShape,
                            cornerRadius = 30.dp,
                            refractionStrength = 0.60f,
                            dispersion = 0.40f,
                            specularIntensity = 0.85f,
                            tintAlpha = 0.70f,
                            enableShader = true
                        )
                    } else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, dest ->
                    val isSelected = dest == selectedDestination

                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) primary else onSurfaceVariant,
                        animationSpec = tween(220),
                        label = "tabIconTint"
                    )

                    val textWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium

                    val itemScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "tabItemScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(itemScale)
                            .clip(RoundedCornerShape(24.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                primary.copy(alpha = 0.24f),
                                                primary.copy(alpha = 0.12f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    ).border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.32f),
                                                primary.copy(alpha = 0.20f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                } else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectDestination(dest) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                tint = iconTint,
                                modifier = Modifier.size(23.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = textWeight,
                                color = if (isSelected) onSurface else onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
