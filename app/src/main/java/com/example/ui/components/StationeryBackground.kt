package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SkyCanvasBottom
import com.example.ui.theme.SkyCanvasTop
import com.example.ui.theme.SubtleBorder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class FloatingPetalSeed(
    val xFraction: Float,
    val phaseOffset: Float,
    val cyclesPerLoop: Int,
    val swayAmplitudeDp: Float,
    val sizeDp: Float,
    val alpha: Float,
    val isDewMote: Boolean
)

private val SkyDriftSeeds = listOf(
    FloatingPetalSeed(0.14f, 0.06f, 1, 18f, 9f, 0.24f, isDewMote = false),
    FloatingPetalSeed(0.84f, 0.22f, 1, 16f, 10f, 0.22f, isDewMote = false),
    FloatingPetalSeed(0.32f, 0.44f, 2, 12f, 4.5f, 0.28f, isDewMote = true),
    FloatingPetalSeed(0.72f, 0.61f, 1, 20f, 8.5f, 0.20f, isDewMote = false),
    FloatingPetalSeed(0.54f, 0.78f, 2, 10f, 4f, 0.24f, isDewMote = true),
    FloatingPetalSeed(0.90f, 0.89f, 1, 14f, 5f, 0.25f, isDewMote = true)
)

/**
 * Serene, modern Forget-Me-Not Sky-Blue atmospheric backdrop.
 *
 * Performance architecture:
 * - Renders inside an isolated sibling background layer so frame invalidations NEVER
 *   recompose or invalidate foreground content (`Scaffold`, `LazyColumn`, transitions).
 * - Uses `drawWithCache` to pre-build ALL `Brush` shaders and reusable `Path` geometry
 *   once per viewport size change (zero per-frame heap/GPU shader allocations even at 120Hz
 *   on high-resolution tablets).
 */
@Composable
fun ForgetMeNotStationeryBackground(
    modifier: Modifier = Modifier,
    showNotebookLines: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        SkyAmbientBackdropLayer()
        content()
    }
}

@Composable
private fun SkyAmbientBackdropLayer() {
    val reducedMotion = rememberReducedMotion()

    val breezePhase: State<Float>
    val glowPhase: State<Float>
    val driftPhase: State<Float>

    if (reducedMotion) {
        breezePhase = remember { mutableFloatStateOf(0f) }
        glowPhase = remember { mutableFloatStateOf(0f) }
        driftPhase = remember { mutableFloatStateOf(0.28f) }
    } else {
        val transition = rememberInfiniteTransition(label = "sky_ambient")
        breezePhase = transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "breeze_phase"
        )
        glowPhase = transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "glow_phase"
        )
        driftPhase = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 38000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "drift_phase"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                // Pre-compute ALL brushes and reusable paths once per viewport size change
                val skyGradient = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to SkyCanvasTop,
                        0.38f to CloudWhite,
                        0.74f to Color(0xFFDCECFD),
                        1.0f to SkyCanvasBottom
                    )
                )

                val primaryOrbRadius = (size.maxDimension * 0.56f).coerceAtLeast(1f)
                val midOrbRadius = primaryOrbRadius * 0.84f
                val bottomOrbRadius = primaryOrbRadius * 0.88f

                // Radial brushes centered at Offset.Zero so they can be translated cleanly per frame
                val topRightOrbBrush = Brush.radialGradient(
                    colors = listOf(
                        ForgetMeNotBlue.copy(alpha = 0.22f),
                        SkyBlue.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset.Zero,
                    radius = primaryOrbRadius
                )
                val midLeftOrbBrush = Brush.radialGradient(
                    colors = listOf(
                        PureWhite.copy(alpha = 0.60f),
                        SkyBlue.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset.Zero,
                    radius = midOrbRadius
                )
                val bottomOrbBrush = Brush.radialGradient(
                    colors = listOf(
                        ForgetMeNotBlue.copy(alpha = 0.17f),
                        PollenYellow.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset.Zero,
                    radius = bottomOrbRadius
                )

                val ribbonBrush = Brush.verticalGradient(
                    colors = listOf(
                        SkyBlue.copy(alpha = 0.28f),
                        ForgetMeNotBlue.copy(alpha = 0.16f)
                    ),
                    startY = size.height * 0.74f,
                    endY = size.height
                )
                val ribbonPath = Path()

                // Scale floral accents gracefully on tablets without growing oversized
                val densityScale = (size.minDimension / 390.dp.toPx()).coerceIn(0.95f, 1.35f)
                val flowerRadiusLarge = 34.dp.toPx() * densityScale
                val flowerRadiusSmall = 19.dp.toPx() * densityScale
                val flowerRadiusBottom = 38.dp.toPx() * densityScale
                val flowerRadiusBottomSmall = 20.dp.toPx() * densityScale

                // Unit petal Path centered at (0, 0) pointing upward to -1f; scaled/rotated on draw
                val unitFlowerPetalPath = buildUnitFlowerPetalPath()
                val unitDriftingPetalPath = buildUnitDriftingPetalPath()

                val petalBrushLarge = Brush.verticalGradient(
                    colors = listOf(
                        ForgetMeNotBlue.copy(alpha = 0.23f),
                        PureWhite.copy(alpha = 0.13f)
                    ),
                    startY = -1f,
                    endY = 0f
                )
                val petalBrushSoft = Brush.verticalGradient(
                    colors = listOf(
                        ForgetMeNotBlue.copy(alpha = 0.17f),
                        PureWhite.copy(alpha = 0.10f)
                    ),
                    startY = -1f,
                    endY = 0f
                )

                val dewBrush = Brush.radialGradient(
                    colors = listOf(
                        PureWhite.copy(alpha = 0.50f),
                        Color.Transparent
                    ),
                    center = Offset.Zero,
                    radius = 12.dp.toPx()
                )

                val waveAmpPx = 14.dp.toPx()
                val marginY = 24.dp.toPx()
                val seedSwayPx = FloatArray(SkyDriftSeeds.size) { i -> SkyDriftSeeds[i].swayAmplitudeDp.dp.toPx() }
                val seedSizePx = FloatArray(SkyDriftSeeds.size) { i -> SkyDriftSeeds[i].sizeDp.dp.toPx() }

                onDrawBehind {
                    val breeze = breezePhase.value.toDouble()
                    val glow = glowPhase.value.toDouble()
                    val drift = driftPhase.value

                    // 1. Airy 60% Forget-Me-Not Sky-Blue canvas
                    drawRect(brush = skyGradient)

                    // 2. Soft breathing watercolor orbs (zero shader allocations; translate only)
                    translate(
                        left = size.width * (0.86f + 0.06f * cos(glow).toFloat()),
                        top = size.height * (0.12f + 0.05f * sin(glow).toFloat())
                    ) {
                        drawCircle(brush = topRightOrbBrush, radius = primaryOrbRadius, center = Offset.Zero)
                    }
                    translate(
                        left = size.width * (0.10f + 0.05f * sin(glow + 1.2).toFloat()),
                        top = size.height * (0.48f + 0.05f * cos(glow + 0.6).toFloat())
                    ) {
                        drawCircle(brush = midLeftOrbBrush, radius = midOrbRadius, center = Offset.Zero)
                    }
                    translate(
                        left = size.width * (0.76f - 0.07f * sin(glow + 2.4).toFloat()),
                        top = size.height * (0.88f + 0.04f * cos(glow + 1.8).toFloat())
                    ) {
                        drawCircle(brush = bottomOrbBrush, radius = bottomOrbRadius, center = Offset.Zero)
                    }

                    // 3. Soft sweeping silk sky wave ribbon (reused Path, zero allocation)
                    val waveShift = sin(breeze).toFloat() * waveAmpPx
                    ribbonPath.rewind()
                    ribbonPath.moveTo(0f, size.height * 0.80f + waveShift)
                    ribbonPath.cubicTo(
                        size.width * 0.30f, size.height * 0.75f - waveShift * 0.8f,
                        size.width * 0.68f, size.height * 0.88f + waveShift,
                        size.width, size.height * 0.81f - waveShift * 0.6f
                    )
                    ribbonPath.lineTo(size.width, size.height)
                    ribbonPath.lineTo(0f, size.height)
                    ribbonPath.close()
                    drawPath(path = ribbonPath, brush = ribbonBrush)

                    // 4. Refined organic forget-me-not floral corner accents (cached unit petal path)
                    val sway1 = sin(breeze).toFloat() * 4f
                    val sway2 = cos(breeze + 1.3).toFloat() * 4.5f
                    val sway3 = sin(breeze + 2.4).toFloat() * 3.5f

                    drawCachedForgetMeNot(
                        petalPath = unitFlowerPetalPath,
                        petalBrush = petalBrushLarge,
                        center = Offset(size.width * 0.91f, size.height * 0.09f),
                        radius = flowerRadiusLarge,
                        haloAlpha = 0.42f,
                        pollenAlpha = 0.38f,
                        rotationDegrees = -14f + sway1
                    )
                    drawCachedForgetMeNot(
                        petalPath = unitFlowerPetalPath,
                        petalBrush = petalBrushSoft,
                        center = Offset(size.width * 0.81f, size.height * 0.14f),
                        radius = flowerRadiusSmall,
                        haloAlpha = 0.32f,
                        pollenAlpha = 0.28f,
                        rotationDegrees = 22f - sway2 * 0.7f
                    )
                    drawCachedForgetMeNot(
                        petalPath = unitFlowerPetalPath,
                        petalBrush = petalBrushLarge,
                        center = Offset(size.width * 0.08f, size.height * 0.87f),
                        radius = flowerRadiusBottom,
                        haloAlpha = 0.38f,
                        pollenAlpha = 0.34f,
                        rotationDegrees = 18f + sway2
                    )
                    drawCachedForgetMeNot(
                        petalPath = unitFlowerPetalPath,
                        petalBrush = petalBrushSoft,
                        center = Offset(size.width * 0.19f, size.height * 0.92f),
                        radius = flowerRadiusBottomSmall,
                        haloAlpha = 0.30f,
                        pollenAlpha = 0.26f,
                        rotationDegrees = -10f + sway3
                    )

                    // 5. Gentle drifting translucent petals & dew motes (zero allocation)
                    for (i in SkyDriftSeeds.indices) {
                        val seed = SkyDriftSeeds[i]
                        val progress = (drift * seed.cyclesPerLoop + seed.phaseOffset) % 1f
                        val edgeFade = sin(progress * PI).toFloat().coerceIn(0f, 1f)
                        val effectiveAlpha = seed.alpha * edgeFade
                        if (effectiveAlpha <= 0.01f) continue

                        val swayX = sin(progress * 2.0 * PI + seed.phaseOffset * 6.28).toFloat() * seedSwayPx[i]
                        val cx = size.width * seed.xFraction + swayX
                        val cy = -marginY + progress * (size.height + marginY * 2f)

                        if (seed.isDewMote) {
                            val r = seedSizePx[i] * 2.1f
                            translate(left = cx, top = cy) {
                                drawCircle(
                                    brush = dewBrush,
                                    radius = r,
                                    center = Offset.Zero,
                                    alpha = (edgeFade * 0.85f).coerceIn(0f, 1f)
                                )
                            }
                        } else {
                            val s = seedSizePx[i]
                            withTransform({
                                translate(left = cx, top = cy)
                                rotate(degrees = progress * 80f + seed.phaseOffset * 180f, pivot = Offset.Zero)
                                scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
                            }) {
                                drawPath(
                                    path = unitDriftingPetalPath,
                                    color = ForgetMeNotBlue.copy(alpha = effectiveAlpha)
                                )
                            }
                        }
                    }
                }
            }
    )
}

private fun buildUnitFlowerPetalPath(): Path = Path().apply {
    moveTo(0f, 0f)
    cubicTo(-0.44f, -0.36f, -0.34f, -0.98f, 0f, -0.90f)
    cubicTo(0.34f, -0.98f, 0.44f, -0.36f, 0f, 0f)
    close()
}

private fun buildUnitDriftingPetalPath(): Path = Path().apply {
    moveTo(0f, 0.5f)
    cubicTo(-0.7f, 0.1f, -0.55f, -0.7f, 0f, -0.55f)
    cubicTo(0.55f, -0.7f, 0.7f, 0.1f, 0f, 0.5f)
    close()
}

private fun DrawScope.drawCachedForgetMeNot(
    petalPath: Path,
    petalBrush: Brush,
    center: Offset,
    radius: Float,
    haloAlpha: Float,
    pollenAlpha: Float,
    rotationDegrees: Float = 0f
) {
    withTransform({
        translate(left = center.x, top = center.y)
        rotate(degrees = rotationDegrees, pivot = Offset.Zero)
        scale(scaleX = radius, scaleY = radius, pivot = Offset.Zero)
    }) {
        for (i in 0 until 5) {
            rotate(degrees = i * 72f, pivot = Offset.Zero) {
                drawPath(path = petalPath, brush = petalBrush)
            }
        }
        drawCircle(
            color = PureWhite.copy(alpha = haloAlpha),
            radius = 0.22f,
            center = Offset.Zero
        )
        drawCircle(
            color = PollenYellow.copy(alpha = pollenAlpha),
            radius = 0.13f,
            center = Offset.Zero
        )
    }
}

/**
 * Adds faint horizontal memo lines inside a note or dedication card.
 */
fun Modifier.ruledMemoLines(
    lineSpacing: Dp = 26.dp,
    lineColor: Color = SubtleBorder.copy(alpha = 0.65f)
): Modifier = this.drawBehind {
    val spacingPx = lineSpacing.toPx()
    val startX = 14.dp.toPx()
    val endX = size.width - startX
    val strokeW = 0.9.dp.toPx()
    var y = spacingPx
    while (y < size.height - 6.dp.toPx()) {
        drawLine(
            color = lineColor,
            start = Offset(startX, y),
            end = Offset(endX, y),
            strokeWidth = strokeW
        )
        y += spacingPx
    }
}

/**
 * Cute looping hopping white bunny animation (in Midnight Navy outline) with
 * bobbing ears, responsive ground shadow, and drifting forget-me-not blooms.
 * Uses drawWithCache for reusable paths and smooth 60/120fps rendering.
 */
@Composable
fun HoppingBunnyLoader(
    modifier: Modifier = Modifier,
    size: Dp = 132.dp
) {
    val reducedMotion = rememberReducedMotion()
    val hopPhase: State<Float>
    val trailShift: State<Float>

    if (reducedMotion) {
        hopPhase = remember { mutableFloatStateOf(0f) }
        trailShift = remember { mutableFloatStateOf(0f) }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "hopping_bunny")
        hopPhase = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 760, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "hop_phase"
        )
        trailShift = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1520, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "trail_shift"
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Hopping white bunny loading animation" }
            .testTag("hopping_bunny_loader")
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val strokeW = (w * 0.028f).coerceIn(2f, 3.6f)
                val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val unitPetal = buildUnitFlowerPetalPath()
                val smilePath = Path().apply {
                    moveTo(w * 0.67f, h * 0.52f)
                    quadraticTo(w * 0.70f, h * 0.545f, w * 0.725f, h * 0.515f)
                }
                val trailPetalBrush = Brush.verticalGradient(
                    colors = listOf(ForgetMeNotBlue, PureWhite.copy(alpha = 0.7f)),
                    startY = -1f,
                    endY = 0f
                )

                onDrawBehind {
                    val phase = hopPhase.value
                    val trail = trailShift.value
                    val jumpArc = if (reducedMotion) 0f else sin(phase * PI).toFloat().coerceAtLeast(0f)
                    val squashStretch = if (reducedMotion) 0f else (cos(phase * 2.0 * PI).toFloat() * 0.05f)
                    val earBob = if (reducedMotion) 0f else (jumpArc * 14f)

                    // Subtle scrolling forget-me-not ground trail
                    val groundY = h * 0.84f
                    val flowerRadius = w * 0.06f
                    for (i in 0..2) {
                        val rawX = ((1f - trail) + i * 0.36f) % 1.08f
                        val flowerX = w * (0.08f + rawX * 0.84f)
                        val distFromCenter = abs(flowerX - w * 0.5f) / (w * 0.5f)
                        val flowerAlpha = (1f - distFromCenter * 0.85f).coerceIn(0.15f, 0.7f)
                        withTransform({
                            translate(left = flowerX, top = groundY + h * 0.05f)
                            scale(scaleX = flowerRadius, scaleY = flowerRadius, pivot = Offset.Zero)
                        }) {
                            for (p in 0 until 5) {
                                rotate(degrees = p * 72f, pivot = Offset.Zero) {
                                    drawPath(path = unitPetal, brush = trailPetalBrush, alpha = flowerAlpha)
                                }
                            }
                            drawCircle(PureWhite.copy(alpha = flowerAlpha), radius = 0.22f, center = Offset.Zero)
                            drawCircle(PollenYellow.copy(alpha = flowerAlpha), radius = 0.13f, center = Offset.Zero)
                        }
                    }

                    // Ground shadow shrinks as bunny hops higher
                    val shadowScale = 1f - jumpArc * 0.34f
                    val shadowW = w * 0.40f * shadowScale
                    val shadowH = h * 0.07f * shadowScale
                    drawOval(
                        color = DeepBlue.copy(alpha = 0.18f * shadowScale),
                        topLeft = Offset((w - shadowW) / 2f, groundY - shadowH / 2f),
                        size = Size(shadowW, shadowH)
                    )

                    // Hopping bunny transform
                    val liftPx = jumpArc * h * 0.24f
                    val tiltDeg = if (reducedMotion) 0f else (cos(phase * PI).toFloat() * -7f)

                    withTransform({
                        translate(left = 0f, top = -liftPx)
                        rotate(degrees = tiltDeg, pivot = Offset(w * 0.5f, h * 0.58f))
                        scale(
                            scaleX = 1f - squashStretch,
                            scaleY = 1f + squashStretch,
                            pivot = Offset(w * 0.5f, h * 0.72f)
                        )
                    }) {
                        drawCircle(PureWhite, radius = w * 0.075f, center = Offset(w * 0.28f, h * 0.66f))
                        drawCircle(MidnightNavy, radius = w * 0.075f, center = Offset(w * 0.28f, h * 0.66f), style = stroke)

                        rotate(jumpArc * 22f, pivot = Offset(w * 0.36f, h * 0.72f)) {
                            drawOval(PureWhite, Offset(w * 0.28f, h * 0.69f), Size(w * 0.16f, h * 0.09f))
                            drawOval(MidnightNavy, Offset(w * 0.28f, h * 0.69f), Size(w * 0.16f, h * 0.09f), style = stroke)
                        }

                        drawOval(PureWhite, Offset(w * 0.30f, h * 0.48f), Size(w * 0.38f, h * 0.28f))
                        drawOval(MidnightNavy, Offset(w * 0.30f, h * 0.48f), Size(w * 0.38f, h * 0.28f), style = stroke)

                        rotate(-jumpArc * 18f, pivot = Offset(w * 0.60f, h * 0.68f)) {
                            drawOval(PureWhite, Offset(w * 0.55f, h * 0.67f), Size(w * 0.14f, h * 0.085f))
                            drawOval(MidnightNavy, Offset(w * 0.55f, h * 0.67f), Size(w * 0.14f, h * 0.085f), style = stroke)
                        }

                        // Ears anchored directly into the top crown of the head (head x=0.43..0.75, top y=0.33)
                        rotate(-20f - earBob, pivot = Offset(w * 0.535f, h * 0.365f)) {
                            drawOval(PureWhite, Offset(w * 0.48f, h * 0.13f), Size(w * 0.11f, h * 0.26f))
                            drawOval(MidnightNavy, Offset(w * 0.48f, h * 0.13f), Size(w * 0.11f, h * 0.26f), style = stroke)
                            drawOval(SkyBlue.copy(alpha = 0.85f), Offset(w * 0.51f, h * 0.18f), Size(w * 0.05f, h * 0.16f))
                        }
                        rotate(-7f - earBob * 0.78f, pivot = Offset(w * 0.605f, h * 0.36f)) {
                            drawOval(PureWhite, Offset(w * 0.55f, h * 0.12f), Size(w * 0.11f, h * 0.26f))
                            drawOval(MidnightNavy, Offset(w * 0.55f, h * 0.12f), Size(w * 0.11f, h * 0.26f), style = stroke)
                            drawOval(SkyBlue.copy(alpha = 0.85f), Offset(w * 0.58f, h * 0.17f), Size(w * 0.05f, h * 0.16f))
                        }

                        drawOval(PureWhite, Offset(w * 0.43f, h * 0.33f), Size(w * 0.32f, h * 0.27f))
                        drawOval(MidnightNavy, Offset(w * 0.43f, h * 0.33f), Size(w * 0.32f, h * 0.27f), style = stroke)

                        drawCircle(MidnightNavy, radius = w * 0.022f, center = Offset(w * 0.62f, h * 0.44f))
                        drawCircle(SkyBlue, radius = w * 0.036f, center = Offset(w * 0.56f, h * 0.50f))
                        drawCircle(MidnightNavy, radius = w * 0.017f, center = Offset(w * 0.72f, h * 0.48f))
                        drawPath(smilePath, MidnightNavy, style = stroke)
                    }
                }
            }
    )
}

/**
 * Full-screen cute loading screen shown on app launch and while syncing data.
 */
@Composable
fun HoppingBunnyLoadingScreen(
    caption: String = stringResource(R.string.bunny_loading_caption),
    modifier: Modifier = Modifier
) {
    ForgetMeNotStationeryBackground(modifier = modifier.testTag("bunny_loading_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HoppingBunnyLoader(size = 152.dp)
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ForgetMeNotFlower(bloomed = true, size = 22.dp)
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MidnightNavy
                )
                ForgetMeNotFlower(bloomed = true, size = 22.dp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = DeepBlue
            )
        }
    }
}
