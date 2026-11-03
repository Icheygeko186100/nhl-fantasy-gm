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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhlfantasygm.BottomNavDestination
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Authentic Open-Source Liquid Glass Optical Engine for Jetpack Compose (Android 12–16).
 *
 * Architecture modeled after Kyant0/AndroidLiquidGlass (`LayerBackdrop` + `GraphicsLayer`):
 * 1. Offscreen Backdrop Capture (`LocalAuroraBackdrop` & `LocalScreenBackdrop`):
 *    - `RenderEffect` (Gaussian blur + AGSL convex lens refraction + chromatic dispersion) is
 *      applied EXCLUSIVELY to an offscreen `GraphicsLayer` containing the background behind the
 *      glass surface.
 *    - Foreground composable content (`drawContent()` — Text, Icons, Switches, Sliders, Buttons)
 *      is drawn AFTER the refracted glass layer with zero shader distortion, keeping all UI text
 *      100% razor-sharp.
 * 2. True Rounded-Box SDF Gradient (`sdRoundBoxGrad`) + Circular Lens Profile (`circleMap`):
 *    - Optical refraction bends smoothly along the curved glass perimeter bevel (`refractionHeight`)
 *      while keeping the interior frosted background clean and readable.
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

/**
 * Holds an offscreen [GraphicsLayer] representing the background behind glass components.
 */
class LiquidBackdropLayer(val graphicsLayer: GraphicsLayer) {
    var rootPosition by mutableStateOf(Offset.Zero)
    var isRecorded by mutableStateOf(false)
    var isRecording: Boolean = false
}

val LocalAuroraBackdrop = compositionLocalOf<LiquidBackdropLayer?> { null }
val LocalScreenBackdrop = compositionLocalOf<LiquidBackdropLayer?> { null }

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

    float2 sdRoundBoxGrad(float2 p, float2 b, float r) {
        float2 signs = sign(p);
        float2 q = abs(p) - b + r;
        if (q.x > 0.0 && q.y > 0.0) {
            return signs * normalize(q);
        }
        return signs * ((q.x > q.y) ? float2(1.0, 0.0) : float2(0.0, 1.0));
    }

    float circleMap(float x) {
        return 1.0 - sqrt(max(0.0, 1.0 - x * x));
    }

    half4 main(float2 fragCoord) {
        float2 safeRes = max(resolution, float2(1.0, 1.0));
        float2 halfSize = safeRes * 0.5;
        float clampedR = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 centered = fragCoord - halfSize;
        float dist = sdRoundBox(centered, halfSize, clampedR);

        if (dist > 0.5) {
            return half4(0.0);
        }

        // Bevel curve: 0.0 in the flat interior, rising smoothly to 1.0 at the curved glass rim
        float rH = max(refractionHeight, 1.0);
        float bevelProgress = 1.0 - clamp(-dist / rH, 0.0, 1.0);
        float lensCurve = circleMap(bevelProgress);

        // Outward normal perpendicular to the rounded rectangle edge
        float2 grad = sdRoundBoxGrad(centered, halfSize, clampedR);

        // Optical refraction displacement strictly along the curved glass bevel
        float2 dispOffset = grad * refractionAmount * lensCurve;

        // Subtle chromatic dispersion (RGB prism separation at the glass bevel)
        float chromaShift = dispersion * 0.14 * lensCurve;
        float2 rCoord = clamp(fragCoord - dispOffset * (1.0 + chromaShift), float2(0.0), safeRes - float2(1.0));
        float2 gCoord = clamp(fragCoord - dispOffset, float2(0.0), safeRes - float2(1.0));
        float2 bCoord = clamp(fragCoord - dispOffset * (1.0 - chromaShift), float2(0.0), safeRes - float2(1.0));

        half4 sampleR = content.eval(rCoord);
        half4 sampleG = content.eval(gCoord);
        half4 sampleB = content.eval(bCoord);

        half3 col = half3(sampleR.r, sampleG.g, sampleB.b);

        // Saturation / vibrancy boost for refracted background colors
        half luma = dot(col, half3(0.299, 0.587, 0.114));
        col = mix(half3(luma), col, half(vibrancy));

        // Frosted glass tint blend
        col = mix(col, tintColor.rgb, half(tintColor.a));

        // Physical 3D specular rim reflection (top-left primary light + bottom-right rim bounce)
        float2 lightDir1 = normalize(float2(-0.65, -0.75));
        float2 lightDir2 = normalize(float2(0.65, 0.75));
        float spec1 = pow(max(0.0, dot(grad, lightDir1)), 3.0) * bevelProgress * specularIntensity;
        float spec2 = pow(max(0.0, dot(grad, lightDir2)), 4.0) * bevelProgress * (specularIntensity * 0.45);
        float totalSpec = spec1 + spec2;

        col += half3(totalSpec * 0.65, totalSpec * 0.80, totalSpec * 0.95);

        return half4(clamp(col, 0.0, 1.0), 1.0);
    }
"""

/**
 * Records the composable's drawing output (plus the underlying Aurora backdrop) into
 * [LocalScreenBackdrop] so floating glass overlays like [LiquidBottomTabs] can sample,
 * blur, and refract the live scrolling screen content underneath them.
 */
@Composable
fun Modifier.captureLiquidBackdrop(): Modifier {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val screenBackdrop = LocalScreenBackdrop.current
    val auroraBackdrop = LocalAuroraBackdrop.current
    if (!isLiquidGlass || screenBackdrop == null) return this

    return this
        .onGloballyPositioned { coords ->
            screenBackdrop.rootPosition = coords.positionInRoot()
        }
        .drawWithContent {
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w > 0 && h > 0) {
                screenBackdrop.isRecording = true
                try {
                    screenBackdrop.graphicsLayer.record(IntSize(w, h)) {
                        if (auroraBackdrop != null && auroraBackdrop.isRecorded) {
                            val dx = auroraBackdrop.rootPosition.x - screenBackdrop.rootPosition.x
                            val dy = auroraBackdrop.rootPosition.y - screenBackdrop.rootPosition.y
                            translate(dx, dy) {
                                drawLayer(auroraBackdrop.graphicsLayer)
                            }
                        }
                        this@drawWithContent.drawContent()
                    }
                    screenBackdrop.isRecorded = true
                } finally {
                    screenBackdrop.isRecording = false
                }
            }
            drawContent()
        }
}

/**
 * Applies a true Liquid Glass optical surface treatment:
 * - Samples the offscreen background layer (`LocalScreenBackdrop` for floating bars, or
 *   `LocalAuroraBackdrop` for cards inside the screen) into a dedicated `glassLayer`.
 * - Applies Gaussian blur + AGSL convex lens refraction + chromatic dispersion ONLY to `glassLayer`.
 * - Draws the frosted glass sheen and specular rim reflection.
 * - Calls `drawContent()` untouched so all foreground Text, Icons, Switches, and Sliders are 100% sharp.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(18.dp),
    cornerRadius: Dp = 18.dp,
    refractionStrength: Float? = null,
    dispersion: Float? = null,
    specularIntensity: Float? = null,
    minBlurDp: Float = 0f,
    tintAlpha: Float = 0.36f,
    enableShader: Boolean = true
): Modifier {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    if (!isLiquidGlass) return this

    val settings = LocalLiquidGlassSettings.current
    val effectiveRefraction = refractionStrength ?: settings.refractionStrength
    val effectiveDispersion = dispersion ?: settings.dispersion
    val effectiveSpecular = specularIntensity ?: settings.specularIntensity
    val effectiveBlurDp = maxOf(settings.blurRadiusDp, minBlurDp)

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current

    val auroraBackdrop = LocalAuroraBackdrop.current
    val screenBackdrop = LocalScreenBackdrop.current
    val glassLayer = rememberGraphicsLayer()

    var myRootPosition by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(Offset(400f, 160f)) }

    val shaderEffect = remember(
        sizePx,
        primary,
        enableShader,
        effectiveRefraction,
        effectiveDispersion,
        effectiveSpecular,
        effectiveBlurDp,
        settings.vibrancy
    ) {
        if (!enableShader) return@remember null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val cornerRadiusPx = with(density) { cornerRadius.toPx() }
                val blurPx = with(density) { effectiveBlurDp.dp.toPx() }
                val minHalfDim = min(sizePx.x, sizePx.y) * 0.5f
                val refractionHeightPx = min(cornerRadiusPx * 1.45f, minHalfDim * 0.85f).coerceAtLeast(12f)
                val refractionAmountPx = (refractionHeightPx * 0.65f * effectiveRefraction).coerceAtLeast(2f)

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
                    surfaceColor.red * 0.7f + primary.red * 0.3f,
                    surfaceColor.green * 0.7f + primary.green * 0.3f,
                    surfaceColor.blue * 0.7f + primary.blue * 0.3f,
                    (tintAlpha * 0.55f).coerceIn(0.10f, 0.55f)
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
                val blurPx = with(density) { effectiveBlurDp.dp.toPx() }
                if (blurPx > 0.5f) {
                    RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
                } else null
            }.getOrNull()
        } else {
            null
        }
    }

    val outlinePath = remember { Path() }

    return this
        .onGloballyPositioned { coords ->
            myRootPosition = coords.positionInRoot()
            val sz = coords.size
            if (sz.width > 0 && sz.height > 0) {
                val next = Offset(sz.width.toFloat(), sz.height.toFloat())
                if (next != sizePx) {
                    sizePx = next
                }
            }
        }
        .clip(shape)
        .drawWithContent {
            val w = size.width.toInt()
            val h = size.height.toInt()
            val cornerPx = cornerRadius.toPx()

            // Choose screenBackdrop for floating overlays (drawn after screenBackdrop finishes recording),
            // or auroraBackdrop for cards inside the screen body.
            val activeBackdrop = if (screenBackdrop != null && screenBackdrop.isRecorded && !screenBackdrop.isRecording) {
                screenBackdrop
            } else {
                auroraBackdrop
            }

            outlinePath.reset()
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Generic -> outlinePath.addPath(outline.path)
                is Outline.Rounded -> outlinePath.addRoundRect(outline.roundRect)
                is Outline.Rectangle -> outlinePath.addRect(outline.rect)
            }

            // 1. Record & draw the blurred + lens-refracted background layer ONLY
            if (activeBackdrop != null && activeBackdrop.isRecorded && w > 0 && h > 0) {
                glassLayer.renderEffect = shaderEffect
                val dx = activeBackdrop.rootPosition.x - myRootPosition.x
                val dy = activeBackdrop.rootPosition.y - myRootPosition.y
                glassLayer.record(IntSize(w, h)) {
                    translate(dx, dy) {
                        drawLayer(activeBackdrop.graphicsLayer)
                    }
                }
                clipPath(outlinePath) {
                    drawLayer(glassLayer)
                }
            }

            // 2. Frosted glass surface sheen & diagonal light reflection (drawn behind foreground content)
            val sheenAlpha = (0.18f * effectiveSpecular).coerceIn(0.05f, 0.28f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = sheenAlpha),
                        primary.copy(alpha = 0.10f),
                        surfaceColor.copy(alpha = (tintAlpha * 0.45f).coerceIn(0.08f, 0.45f)),
                        secondary.copy(alpha = 0.08f),
                        Color.White.copy(alpha = sheenAlpha * 0.45f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )

            // Subtle top-edge inner glass reflection band
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.22f * effectiveSpecular).coerceIn(0.04f, 0.32f)),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = (size.height * 0.38f).coerceAtLeast(16f)
                ),
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )

            // 3. Draw foreground UI content (Text, Icons, Switches, Sliders, Buttons) 100% sharp!
            drawContent()

            // 4. Crisp 3D specular glass rim border on top
            val rimAlpha = (0.52f * effectiveSpecular).coerceIn(0.16f, 0.90f)
            val strokeWidthPx = 1.35.dp.toPx()
            drawPath(
                path = outlinePath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = rimAlpha),
                        primary.copy(alpha = rimAlpha * 0.85f),
                        secondary.copy(alpha = rimAlpha * 0.45f),
                        Color.White.copy(alpha = rimAlpha * 0.65f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                style = Stroke(width = strokeWidthPx)
            )
        }
}

/**
 * Applies a sleek Liquid Glass top navigation bar style with frosted backdrop blur and specular edge highlight.
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
                baseBg.copy(alpha = 0.78f),
                primary.copy(alpha = 0.14f),
                secondary.copy(alpha = 0.10f),
                baseBg.copy(alpha = 0.86f)
            )
        )
    }

    val borderBrush = remember(primary, secondary) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.28f),
                primary.copy(alpha = 0.50f),
                secondary.copy(alpha = 0.38f),
                Color.White.copy(alpha = 0.28f)
            )
        )
    }

    return this
        .background(barBrush)
        .border(width = 1.dp, brush = borderBrush, shape = RectangleShape)
}

/**
 * Ambient animated Liquid Glass & Material You Aurora Backdrop.
 * Renders glowing, slowly drifting color orbs and caustic light ribbons into an offscreen
 * [GraphicsLayer] (`LocalAuroraBackdrop`) so glass cards can optically refract and blur them.
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

    val auroraGraphicsLayer = rememberGraphicsLayer()
    val screenGraphicsLayer = rememberGraphicsLayer()

    val auroraBackdrop = remember(auroraGraphicsLayer) { LiquidBackdropLayer(auroraGraphicsLayer) }
    val screenBackdrop = remember(screenGraphicsLayer) { LiquidBackdropLayer(screenGraphicsLayer) }

    CompositionLocalProvider(
        LocalAuroraBackdrop provides auroraBackdrop,
        LocalScreenBackdrop provides screenBackdrop
    ) {
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

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            auroraBackdrop.rootPosition = coords.positionInRoot()
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val intW = w.toInt()
                    val intH = h.toInt()

                    if (intW > 0 && intH > 0) {
                        auroraBackdrop.graphicsLayer.record(IntSize(intW, intH)) {
                            drawRect(color = bgColor)

                            val orb1Center = Offset(
                                x = w * (0.22f + 0.18f * cos(phase)),
                                y = h * (0.14f + 0.10f * sin(phase))
                            )
                            val orb2Center = Offset(
                                x = w * (0.82f - 0.18f * sin(phase * 0.85f)),
                                y = h * (0.36f + 0.12f * cos(phase * 0.85f))
                            )
                            val orb3Center = Offset(
                                x = w * (0.28f + 0.20f * sin(phase * 0.65f)),
                                y = h * (0.64f - 0.10f * cos(phase * 0.65f))
                            )
                            val orb4Center = Offset(
                                x = w * (0.74f + 0.15f * cos(phase * 0.5f)),
                                y = h * (0.86f + 0.08f * sin(phase * 0.5f))
                            )

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        primaryOrb.copy(alpha = 0.42f),
                                        primaryOrb.copy(alpha = 0.14f),
                                        Color.Transparent
                                    ),
                                    center = orb1Center,
                                    radius = w * 0.68f
                                ),
                                radius = w * 0.68f,
                                center = orb1Center
                            )

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        secondaryOrb.copy(alpha = 0.36f),
                                        secondaryOrb.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    center = orb2Center,
                                    radius = w * 0.64f
                                ),
                                radius = w * 0.64f,
                                center = orb2Center
                            )

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        tertiaryOrb.copy(alpha = 0.28f),
                                        primaryOrb.copy(alpha = 0.10f),
                                        Color.Transparent
                                    ),
                                    center = orb3Center,
                                    radius = w * 0.62f
                                ),
                                radius = w * 0.62f,
                                center = orb3Center
                            )

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        primaryOrb.copy(alpha = 0.34f),
                                        secondaryOrb.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    center = orb4Center,
                                    radius = w * 0.58f
                                ),
                                radius = w * 0.58f,
                                center = orb4Center
                            )

                            // Subtle caustic light ribbons that create visible lens refraction along card edges
                            val ribbonY1 = h * (0.26f + 0.06f * sin(phase * 1.2f))
                            val ribbonY2 = h * (0.58f + 0.06f * cos(phase * 0.9f))
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        primaryOrb.copy(alpha = 0.18f),
                                        Color.White.copy(alpha = 0.10f),
                                        secondaryOrb.copy(alpha = 0.16f),
                                        Color.Transparent
                                    ),
                                    start = Offset(0f, ribbonY1 - h * 0.12f),
                                    end = Offset(w, ribbonY1 + h * 0.12f)
                                ),
                                topLeft = Offset(0f, 0f),
                                size = Size(w, h)
                            )
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        secondaryOrb.copy(alpha = 0.15f),
                                        primaryOrb.copy(alpha = 0.16f),
                                        Color.Transparent
                                    ),
                                    start = Offset(w, ribbonY2 - h * 0.15f),
                                    end = Offset(0f, ribbonY2 + h * 0.15f)
                                ),
                                topLeft = Offset(0f, 0f),
                                size = Size(w, h)
                            )
                        }
                        auroraBackdrop.isRecorded = true
                        drawLayer(auroraBackdrop.graphicsLayer)
                    }
                }
            }

            content()
        }
    }
}

/**
 * Authentic Liquid Glass Card container with offscreen convex lens refraction, chromatic dispersion,
 * and specular highlights. Foreground content remains 100% sharp.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    cornerRadius: Dp = 18.dp,
    tintAlpha: Float = 0.42f,
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
 * Tactile fluid-glass button with elastic spring compression feedback on press and crisp typography.
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
        targetValue = if (isPressed) 1.15f else 0.85f,
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
                        tintAlpha = 0.28f,
                        enableShader = true
                    )
                } else Modifier
            ),
        shape = shape,
        color = if (isLiquidGlass) containerColor.copy(alpha = 0.78f) else containerColor,
        contentColor = contentColor,
        border = border ?: if (isLiquidGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.38f)) else null,
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
 * Floating Liquid Glass Bottom Capsule Dock (modeled after Kyant0/AndroidLiquidGlass `LiquidBottomTabs`).
 *
 * Samples and blurs [LocalScreenBackdrop] (the scrolling screen content underneath the dock)
 * with convex capsule lens refraction at the perimeter while keeping tab icons and labels
 * 100% razor-sharp.
 */
@Composable
fun LiquidBottomTabs(
    selectedDestination: BottomNavDestination,
    onSelectDestination: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = true
) {
    val destinations = remember { BottomNavDestination.entries }

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val pillShape = remember { RoundedCornerShape(32.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = pillShape,
            color = if (isLiquidGlass) Color(0xFF0A1222).copy(alpha = 0.58f) else MaterialTheme.colorScheme.surface,
            tonalElevation = if (isLiquidGlass) 0.dp else 6.dp,
            border = if (isLiquidGlass) {
                BorderStroke(1.2.dp, Color.White.copy(alpha = 0.24f))
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
                            cornerRadius = 32.dp,
                            refractionStrength = 0.55f,
                            dispersion = 0.32f,
                            specularIntensity = 0.90f,
                            minBlurDp = 20f,
                            tintAlpha = 0.62f,
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
                destinations.forEach { dest ->
                    val isSelected = dest == selectedDestination

                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) primary else onSurfaceVariant,
                        animationSpec = tween(220),
                        label = "tabIconTint"
                    )

                    val textWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium

                    val itemScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.04f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "tabItemScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(itemScale)
                            .clip(RoundedCornerShape(26.dp))
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.16f),
                                                    primary.copy(alpha = 0.24f),
                                                    primary.copy(alpha = 0.12f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(26.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.45f),
                                                    primary.copy(alpha = 0.28f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(26.dp)
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
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
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
