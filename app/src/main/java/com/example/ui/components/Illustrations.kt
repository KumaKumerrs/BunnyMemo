package com.example.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BunnyFlowerCrown
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.SkyBlue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isReducedMotionEnabled(context) }
}

private fun isReducedMotionEnabled(context: Context): Boolean {
    return try {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    } catch (_: Exception) {
        false
    }
}

/**
 * Five-petal forget-me-not flower (#86B5F4 petals + #F5D579 pollen yellow center).
 * Unfinished steps show a gentle closed bud. Respects reduced-motion settings.
 */
@Composable
fun ForgetMeNotFlower(
    bloomed: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animatePop: Boolean = false,
    contentDesc: String? = null
) {
    val scaleValue: Float = if (animatePop) {
        val reducedMotion = rememberReducedMotion()
        val scaleAnim = remember { Animatable(if (bloomed) 1f else 0.92f) }

        LaunchedEffect(bloomed, animatePop, reducedMotion) {
            if (reducedMotion) {
                scaleAnim.snapTo(1f)
            } else if (bloomed) {
                scaleAnim.snapTo(0.65f)
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            } else {
                scaleAnim.animateTo(0.92f, tween(180))
            }
        }
        scaleAnim.value
    } else {
        if (bloomed) 1f else 0.92f
    }

    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDesc != null) Modifier.semantics { contentDescription = contentDesc }
                else Modifier
            )
    ) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension / 2f

        if (scaleValue == 1f) {
            if (bloomed) {
                drawBloomedForgetMeNot(Offset(cx, cy), r)
            } else {
                drawClosedForgetMeNotBud(Offset(cx, cy), r)
            }
        } else {
            scale(scaleValue, pivot = Offset(cx, cy)) {
                if (bloomed) {
                    drawBloomedForgetMeNot(Offset(cx, cy), r)
                } else {
                    drawClosedForgetMeNotBud(Offset(cx, cy), r)
                }
            }
        }
    }
}

private fun DrawScope.drawBloomedForgetMeNot(center: Offset, radius: Float) {
    val petalDist = radius * 0.48f
    val petalRadius = radius * 0.38f

    for (i in 0 until 5) {
        val angleRad = ((-90.0 + i * 72.0) * PI / 180.0)
        val px = center.x + (petalDist * cos(angleRad)).toFloat()
        val py = center.y + (petalDist * sin(angleRad)).toFloat()
        drawCircle(
            color = ForgetMeNotBlue,
            radius = petalRadius,
            center = Offset(px, py)
        )
    }

    // Tiny cloud-white crown ring
    drawCircle(
        color = BunnyFlowerCrown,
        radius = radius * 0.30f,
        center = center
    )
    // Pollen yellow center
    drawCircle(
        color = PollenYellow,
        radius = radius * 0.22f,
        center = center
    )
}

private fun DrawScope.drawClosedForgetMeNotBud(center: Offset, radius: Float) {
    // Gentle outlined bud waiting to bloom
    val strokeW = (radius * 0.14f).coerceAtLeast(1.4f)
    drawCircle(
        color = SkyBlue.copy(alpha = 0.45f),
        radius = radius * 0.44f,
        center = center
    )
    drawCircle(
        color = ForgetMeNotBlue,
        radius = radius * 0.44f,
        center = center,
        style = Stroke(width = strokeW)
    )
    drawCircle(
        color = BunnyFlowerCrown,
        radius = radius * 0.14f,
        center = center
    )
}

/**
 * Displays forget-me-not blooms paired with readable count and percentage.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowerProgressRow(
    completed: Int,
    total: Int,
    summaryText: String,
    modifier: Modifier = Modifier,
    celebrateFinal: Boolean = false,
    flowerSize: Dp = 22.dp
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (total > 0) {
            val displayCount = total.coerceAtMost(10)
            val bloomedSlots = if (total <= 10) completed else ((completed * 10) / total)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (i in 0 until displayCount) {
                    val isBloomed = i < bloomedSlots
                    val isLastJustBloomed = celebrateFinal && i == displayCount - 1 && isBloomed
                    ForgetMeNotFlower(
                        bloomed = isBloomed,
                        size = flowerSize,
                        animatePop = isLastJustBloomed
                    )
                }
            }
        }
        if (summaryText.isNotBlank()) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

enum class BunnyPose {
    GREETING,
    RESTING,
    ENVELOPE,
    CELEBRATING
}

/**
 * Monthly breed bunny illustration drawn with crisp Midnight Navy outline and breed-specific markings.
 * Defaults automatically to the current month's breed unless an explicit [breed] is passed.
 */
@Composable
fun SmallBunnyIllustration(
    pose: BunnyPose,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    breed: BunnyBreed = BunnyBreed.forCurrentMonth(),
    contentDesc: String? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDesc != null) Modifier.semantics { contentDescription = contentDesc }
                else Modifier
            )
    ) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = (w * 0.042f).coerceIn(1.8f, 3.6f)
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawBreedBunnyPose(
            pose = pose,
            breed = breed,
            isDarkTheme = isDarkTheme,
            w = w,
            h = h,
            stroke = stroke
        )
    }
}

@Composable
fun BunnyHeadingRow(
    title: String,
    subtitle: String? = null,
    pose: BunnyPose = BunnyPose.GREETING,
    breed: BunnyBreed = BunnyBreed.forCurrentMonth(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallBunnyIllustration(
            pose = pose,
            size = 48.dp,
            breed = breed,
            contentDesc = "${breed.breedName} bunny"
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MidnightNavy
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
