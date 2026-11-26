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
import androidx.compose.runtime.key
import com.example.ui.theme.BunnyFlowerCrown
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SkyCanvasBottom
import com.example.ui.theme.SkyCanvasMid
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
    val isDark = LocalIsDarkTheme.current
    val canvasTop = SkyCanvasTop
    val canvasCloud = CloudWhite
    val canvasMid = SkyCanvasMid
    val canvasBottom = SkyCanvasBottom
    val orbBlue = ForgetMeNotBlue
    val orbSky = SkyBlue
    val orbHighlight = PureWhite
    val orbPollen = PollenYellow

    key(isDark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    // Pre-compute ALL brushes and geometry once per viewport size & theme change.
                    // Rendering statically avoids continuous 60/120fps full-screen GPU redraws
                    // behind scrolling lists, search input, and modal animations.
                    val skyGradient = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to canvasTop,
                            0.38f to canvasCloud,
                            0.74f to canvasMid,
                            1.0f to canvasBottom
                        )
                    )

                    val primaryOrbRadius = (size.maxDimension * 0.56f).coerceAtLeast(1f)
                    val midOrbRadius = primaryOrbRadius * 0.84f
                    val bottomOrbRadius = primaryOrbRadius * 0.88f

                    val topRightOrbBrush = Brush.radialGradient(
                        colors = listOf(
                            orbBlue.copy(alpha = if (isDark) 0.14f else 0.22f),
                            orbSky.copy(alpha = if (isDark) 0.10f else 0.14f),
                            Color.Transparent
                        ),
                        center = Offset.Zero,
                        radius = primaryOrbRadius
                    )
                    val midLeftOrbBrush = Brush.radialGradient(
                        colors = listOf(
                            orbHighlight.copy(alpha = if (isDark) 0.18f else 0.60f),
                            orbSky.copy(alpha = if (isDark) 0.08f else 0.12f),
                            Color.Transparent
                        ),
                        center = Offset.Zero,
                        radius = midOrbRadius
                    )
                    val bottomOrbBrush = Brush.radialGradient(
                        colors = listOf(
                            orbBlue.copy(alpha = if (isDark) 0.12f else 0.17f),
                            orbPollen.copy(alpha = if (isDark) 0.06f else 0.08f),
                            Color.Transparent
                        ),
                        center = Offset.Zero,
                        radius = bottomOrbRadius
                    )

                    val ribbonBrush = Brush.verticalGradient(
                        colors = listOf(
                            orbSky.copy(alpha = if (isDark) 0.20f else 0.28f),
                            orbBlue.copy(alpha = if (isDark) 0.10f else 0.16f)
                        ),
                        startY = size.height * 0.74f,
                        endY = size.height
                    )
                val ribbonPath = Path().apply {
                    moveTo(0f, size.height * 0.80f)
                    cubicTo(
                        size.width * 0.30f, size.height * 0.75f,
                        size.width * 0.68f, size.height * 0.88f,
                        size.width, size.height * 0.81f
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }

                // Scale floral accents gracefully on tablets without growing oversized
                val densityScale = (size.minDimension / 390.dp.toPx()).coerceIn(0.95f, 1.35f)
                val flowerRadiusLarge = 34.dp.toPx() * densityScale
                val flowerRadiusSmall = 19.dp.toPx() * densityScale
                val flowerRadiusBottom = 38.dp.toPx() * densityScale
                val flowerRadiusBottomSmall = 20.dp.toPx() * densityScale

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

                val marginY = 24.dp.toPx()
                val seedSwayPx = FloatArray(SkyDriftSeeds.size) { i -> SkyDriftSeeds[i].swayAmplitudeDp.dp.toPx() }
                val seedSizePx = FloatArray(SkyDriftSeeds.size) { i -> SkyDriftSeeds[i].sizeDp.dp.toPx() }

                onDrawBehind {
                    // 1. Airy 60% Forget-Me-Not Sky-Blue canvas
                    drawRect(brush = skyGradient)

                    // 2. Soft watercolor orbs
                    translate(left = size.width * 0.88f, top = size.height * 0.12f) {
                        drawCircle(brush = topRightOrbBrush, radius = primaryOrbRadius, center = Offset.Zero)
                    }
                    translate(left = size.width * 0.12f, top = size.height * 0.50f) {
                        drawCircle(brush = midLeftOrbBrush, radius = midOrbRadius, center = Offset.Zero)
                    }
                    translate(left = size.width * 0.74f, top = size.height * 0.88f) {
                        drawCircle(brush = bottomOrbBrush, radius = bottomOrbRadius, center = Offset.Zero)
                    }

                    // 3. Soft sweeping silk sky wave ribbon
                    drawPath(path = ribbonPath, brush = ribbonBrush)

                    // 4. Refined organic forget-me-not floral corner accents
                    drawCachedForgetMeNot(
                        petalPath = unitFlowerPetalPath,
                        petalBrush = petalBrushLarge,
                        center = Offset(size.width * 0.91f, size.height * 0.09f),
                        radius = flowerRadiusLarge,
                        haloAlpha = 0.42f,
                        pollenAlpha = 0.38f,
                        rotationDegrees = -14f
                    )
                    drawCachedForgetMeNot(
                        petalPath = unitFlowerPetalPath,
                        petalBrush = petalBrushSoft,
                        center = Offset(size.width * 0.81f, size.height * 0.14f),
                        radius = flowerRadiusSmall,
                        haloAlpha = 0.32f,
                        pollenAlpha = 0.28f,
                        rotationDegrees = 22f
                    )
                    drawCachedForgetMeNot(
                        petalPath = unitFlowerPetalPath,
                        petalBrush = petalBrushLarge,
                        center = Offset(size.width * 0.08f, size.height * 0.87f),
                        radius = flowerRadiusBottom,
                        haloAlpha = 0.38f,
                        pollenAlpha = 0.34f,
                        rotationDegrees = 18f
                    )
                    drawCachedForgetMeNot(
                        petalPath = unitFlowerPetalPath,
                        petalBrush = petalBrushSoft,
                        center = Offset(size.width * 0.19f, size.height * 0.92f),
                        radius = flowerRadiusBottomSmall,
                        haloAlpha = 0.30f,
                        pollenAlpha = 0.26f,
                        rotationDegrees = -10f
                    )

                    // 5. Delicate static translucent petals & dew accents
                    val staticPhase = 0.28f
                    for (i in SkyDriftSeeds.indices) {
                        val seed = SkyDriftSeeds[i]
                        val progress = (staticPhase * seed.cyclesPerLoop + seed.phaseOffset) % 1f
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
    size: Dp = 132.dp,
    breed: BunnyBreed = BunnyBreed.forCurrentMonth()
) {
    val isDarkTheme = LocalIsDarkTheme.current
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
            .semantics { contentDescription = "Hopping ${breed.breedName} bunny loading animation" }
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
                    colors = listOf(ForgetMeNotBlue, BunnyFlowerCrown.copy(alpha = 0.7f)),
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
                            drawCircle(BunnyFlowerCrown.copy(alpha = flowerAlpha), radius = 0.22f, center = Offset.Zero)
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
                        drawHoppingBreedBunny(
                            breed = breed,
                            isDarkTheme = isDarkTheme,
                            w = w,
                            h = h,
                            stroke = stroke,
                            jumpArc = jumpArc,
                            earBob = earBob,
                            smilePath = smilePath
                        )
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
    breed: BunnyBreed = BunnyBreed.forCurrentMonth(),
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
            HoppingBunnyLoader(size = 152.dp, breed = breed)
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
