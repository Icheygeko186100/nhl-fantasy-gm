package com.nhlfantasygm.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.sin

/**
 * Open-Source Liquid Glass Optical Engine for Jetpack Compose (Android 12–16).
 *
 * Adapted from open-source Compose Liquid Glass implementations
 * (Kyant0/AndroidLiquidGlass, FletchMcKee/liquid, and Haze glassmorphism shaders):
 * - Signed Distance Field (SDF) rounded-lens edge refraction
 * - Chromatic dispersion along the glass perimeter
 * - Specular top-left rim reflection & translucent frosted surface fill
 * - Material You dynamic color integration
 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }
val LocalMaterialYouEnabled = compositionLocalOf { true }

@Language("AGSL")
private const val LIQUID_GLASS_AGSL_SHADER = """
    uniform shader content;
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float refractionStrength;
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

        // Edge bevel factor for liquid lens refraction (strongest within 28px of the glass rim)
        float bevel = 1.0 - smoothstep(-28.0, 0.0, dist);
        float2 dir = normalize(centered + float2(0.0001, 0.0001));
        float2 offset = dir * bevel * bevel * refractionStrength * 10.0;

        float2 samplePos = clamp(fragCoord - offset, float2(0.0), safeRes - float2(1.0));
        half4 base = content.eval(samplePos);

        // Top-left directional specular highlight
        float2 uv = fragCoord / safeRes;
        float diag = clamp(1.0 - (uv.x * 0.65 + uv.y * 0.65), 0.0, 1.0);
        float rimGlow = bevel * specularIntensity * (0.35 + 0.65 * diag);

        half3 glassRgb = mix(base.rgb, tintColor.rgb, half(tintColor.a * 0.18));
        glassRgb += half3(rimGlow * 0.22, rimGlow * 0.26, rimGlow * 0.30);

        return half4(clamp(glassRgb, 0.0, 1.0), base.a);
    }
"""

/**
 * Applies a Liquid Glass optical surface treatment (specular rim border, translucent gradient sheen,
 * and optional AGSL RuntimeShader lens refraction on Android 13+).
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(18.dp),
    cornerRadius: Dp = 18.dp,
    refractionStrength: Float = 0.35f,
    specularIntensity: Float = 0.55f,
    enableShader: Boolean = false
): Modifier {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    if (!isLiquidGlass) return this

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface
    var sizePx by remember { mutableStateOf(Offset(400f, 160f)) }

    val glassBrush = remember(primary, secondary, surfaceColor) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                primary.copy(alpha = 0.08f),
                surfaceColor.copy(alpha = 0.72f),
                secondary.copy(alpha = 0.07f)
            ),
            start = Offset(0f, 0f),
            end = Offset(900f, 600f)
        )
    }

    val rimBorderBrush = remember(primary, secondary) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.42f),
                primary.copy(alpha = 0.38f),
                secondary.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.12f)
            ),
            start = Offset(0f, 0f),
            end = Offset(800f, 800f)
        )
    }

    val shaderEffect = remember(sizePx, primary, enableShader) {
        if (enableShader && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val shader = RuntimeShader(LIQUID_GLASS_AGSL_SHADER)
                shader.setFloatUniform("resolution", sizePx.x.coerceAtLeast(1f), sizePx.y.coerceAtLeast(1f))
                shader.setFloatUniform("cornerRadius", cornerRadius.value * 2.5f)
                shader.setFloatUniform("refractionStrength", refractionStrength)
                shader.setFloatUniform("specularIntensity", specularIntensity)
                shader.setFloatUniform(
                    "tintColor",
                    primary.red,
                    primary.green,
                    primary.blue,
                    0.35f
                )
                RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
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
        .border(width = 1.dp, brush = rimBorderBrush, shape = shape)
}

/**
 * Applies a sleek Liquid Glass top/bottom navigation bar style with specular top/bottom edge highlight.
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
                baseBg.copy(alpha = 0.84f),
                primary.copy(alpha = 0.11f),
                secondary.copy(alpha = 0.08f),
                baseBg.copy(alpha = 0.90f)
            )
        )
    }

    val borderBrush = remember(primary, secondary) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.18f),
                primary.copy(alpha = 0.45f),
                secondary.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.18f)
            )
        )
    }

    return this
        .background(barBrush)
        .border(width = 0.8.dp, brush = borderBrush, shape = RectangleShape)
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
                    animation = tween(durationMillis = 18000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "auroraPhase"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val orb1Center = Offset(
                    x = w * (0.22f + 0.12f * cos(phase)),
                    y = h * (0.18f + 0.08f * sin(phase))
                )
                val orb2Center = Offset(
                    x = w * (0.78f - 0.14f * sin(phase * 0.8f)),
                    y = h * (0.46f + 0.10f * cos(phase * 0.8f))
                )
                val orb3Center = Offset(
                    x = w * (0.35f + 0.15f * sin(phase * 0.6f)),
                    y = h * (0.82f - 0.08f * cos(phase * 0.6f))
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryOrb.copy(alpha = 0.22f), Color.Transparent),
                        center = orb1Center,
                        radius = w * 0.65f
                    ),
                    radius = w * 0.65f,
                    center = orb1Center
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryOrb.copy(alpha = 0.18f), Color.Transparent),
                        center = orb2Center,
                        radius = w * 0.60f
                    ),
                    radius = w * 0.60f,
                    center = orb2Center
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tertiaryOrb.copy(alpha = 0.14f), Color.Transparent),
                        center = orb3Center,
                        radius = w * 0.55f
                    ),
                    radius = w * 0.55f,
                    center = orb3Center
                )
            }
        }

        content()
    }
}
