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
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.PureWhite
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
    val reducedMotion = rememberReducedMotion()
    val scaleAnim = remember { Animatable(if (bloomed) 1f else 0.92f) }

    LaunchedEffect(bloomed, animatePop, reducedMotion) {
        if (reducedMotion) {
            scaleAnim.snapTo(1f)
        } else if (bloomed && animatePop) {
            scaleAnim.snapTo(0.65f)
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else if (bloomed) {
            scaleAnim.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        } else {
            scaleAnim.animateTo(0.92f, tween(180))
        }
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

        scale(scaleAnim.value, pivot = Offset(cx, cy)) {
            if (bloomed) {
                drawBloomedForgetMeNot(Offset(cx, cy), r)
            } else {
                drawClosedForgetMeNotBud(Offset(cx, cy), r)
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
        color = CloudWhite,
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
        color = PureWhite,
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
 * Small white bunny drawn in a simple Midnight Navy (#20364D) outline per Page 3 of the brief.
 */
@Composable
fun SmallBunnyIllustration(
    pose: BunnyPose,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDesc: String? = null
) {
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

        when (pose) {
            BunnyPose.GREETING -> drawGreetingBunny(w, h, stroke)
            BunnyPose.RESTING -> drawRestingBunny(w, h, stroke)
            BunnyPose.ENVELOPE -> drawEnvelopeBunny(w, h, stroke)
            BunnyPose.CELEBRATING -> drawCelebratingBunny(w, h, stroke)
        }
    }
}

private fun DrawScope.drawGreetingBunny(w: Float, h: Float, stroke: Stroke) {
    // Ears anchored symmetrically onto the top crown of the head (head center x=0.50w, top y=0.30h)
    rotate(-10f, pivot = Offset(w * 0.395f, h * 0.34f)) {
        drawOval(PureWhite, Offset(w * 0.33f, h * 0.06f), Size(w * 0.13f, h * 0.32f))
        drawOval(MidnightNavy, Offset(w * 0.33f, h * 0.06f), Size(w * 0.13f, h * 0.32f), style = stroke)
        drawOval(SkyBlue.copy(alpha = 0.68f), Offset(w * 0.365f, h * 0.12f), Size(w * 0.06f, h * 0.19f))
    }
    rotate(10f, pivot = Offset(w * 0.605f, h * 0.34f)) {
        drawOval(PureWhite, Offset(w * 0.54f, h * 0.06f), Size(w * 0.13f, h * 0.32f))
        drawOval(MidnightNavy, Offset(w * 0.54f, h * 0.06f), Size(w * 0.13f, h * 0.32f), style = stroke)
        drawOval(SkyBlue.copy(alpha = 0.68f), Offset(w * 0.575f, h * 0.12f), Size(w * 0.06f, h * 0.19f))
    }

    // Little fluffy tail
    drawCircle(PureWhite, radius = w * 0.08f, center = Offset(w * 0.25f, h * 0.78f))
    drawCircle(MidnightNavy, radius = w * 0.08f, center = Offset(w * 0.25f, h * 0.78f), style = stroke)

    // Body
    drawOval(PureWhite, Offset(w * 0.28f, h * 0.54f), Size(w * 0.44f, h * 0.36f))
    drawOval(MidnightNavy, Offset(w * 0.28f, h * 0.54f), Size(w * 0.44f, h * 0.36f), style = stroke)

    // Head
    drawOval(PureWhite, Offset(w * 0.27f, h * 0.30f), Size(w * 0.46f, h * 0.35f))
    drawOval(MidnightNavy, Offset(w * 0.27f, h * 0.30f), Size(w * 0.46f, h * 0.35f), style = stroke)

    // Gentle eyes & blush
    drawCircle(MidnightNavy, radius = w * 0.028f, center = Offset(w * 0.42f, h * 0.45f))
    drawCircle(MidnightNavy, radius = w * 0.028f, center = Offset(w * 0.58f, h * 0.45f))
    drawCircle(SkyBlue, radius = w * 0.042f, center = Offset(w * 0.35f, h * 0.51f))
    drawCircle(SkyBlue, radius = w * 0.042f, center = Offset(w * 0.65f, h * 0.51f))

    // Tiny smile / nose
    drawCircle(MidnightNavy, radius = w * 0.018f, center = Offset(w * 0.50f, h * 0.50f))
    val smile = Path().apply {
        moveTo(w * 0.46f, h * 0.535f)
        quadraticTo(w * 0.50f, h * 0.565f, w * 0.54f, h * 0.535f)
    }
    drawPath(smile, MidnightNavy, style = stroke)

    // Little front paws
    drawOval(PureWhite, Offset(w * 0.34f, h * 0.82f), Size(w * 0.12f, h * 0.09f))
    drawOval(MidnightNavy, Offset(w * 0.34f, h * 0.82f), Size(w * 0.12f, h * 0.09f), style = stroke)
    drawOval(PureWhite, Offset(w * 0.54f, h * 0.82f), Size(w * 0.12f, h * 0.09f))
    drawOval(MidnightNavy, Offset(w * 0.54f, h * 0.82f), Size(w * 0.12f, h * 0.09f), style = stroke)
}

private fun DrawScope.drawRestingBunny(w: Float, h: Float, stroke: Stroke) {
    // Soft resting ground shadow
    drawOval(SkyBlue.copy(alpha = 0.45f), Offset(w * 0.12f, h * 0.78f), Size(w * 0.76f, h * 0.14f))

    // Fluffy round tail
    drawCircle(PureWhite, radius = w * 0.09f, center = Offset(w * 0.20f, h * 0.68f))
    drawCircle(MidnightNavy, radius = w * 0.09f, center = Offset(w * 0.20f, h * 0.68f), style = stroke)

    // Curled resting loaf body
    drawOval(PureWhite, Offset(w * 0.20f, h * 0.46f), Size(w * 0.58f, h * 0.38f))
    drawOval(MidnightNavy, Offset(w * 0.20f, h * 0.46f), Size(w * 0.58f, h * 0.38f), style = stroke)

    // Folded ears anchored directly to the upper crown of the resting head (head x=0.46..0.84, top y=0.38)
    rotate(-46f, pivot = Offset(w * 0.57f, h * 0.42f)) {
        drawOval(PureWhite, Offset(w * 0.51f, h * 0.16f), Size(w * 0.12f, h * 0.30f))
        drawOval(MidnightNavy, Offset(w * 0.51f, h * 0.16f), Size(w * 0.12f, h * 0.30f), style = stroke)
        drawOval(SkyBlue.copy(alpha = 0.68f), Offset(w * 0.54f, h * 0.21f), Size(w * 0.06f, h * 0.18f))
    }
    rotate(-28f, pivot = Offset(w * 0.64f, h * 0.41f)) {
        drawOval(PureWhite, Offset(w * 0.58f, h * 0.15f), Size(w * 0.12f, h * 0.30f))
        drawOval(MidnightNavy, Offset(w * 0.58f, h * 0.15f), Size(w * 0.12f, h * 0.30f), style = stroke)
        drawOval(SkyBlue.copy(alpha = 0.68f), Offset(w * 0.61f, h * 0.20f), Size(w * 0.06f, h * 0.18f))
    }

    // Head nestled forward (drawn over ear bases so ears join seamlessly into the crown)
    drawOval(PureWhite, Offset(w * 0.46f, h * 0.38f), Size(w * 0.38f, h * 0.34f))
    drawOval(MidnightNavy, Offset(w * 0.46f, h * 0.38f), Size(w * 0.38f, h * 0.34f), style = stroke)

    // Peaceful closed resting eye
    val closedEye = Path().apply {
        moveTo(w * 0.62f, h * 0.53f)
        quadraticTo(w * 0.67f, h * 0.57f, w * 0.72f, h * 0.53f)
    }
    drawPath(closedEye, MidnightNavy, style = stroke)
    drawCircle(SkyBlue, radius = w * 0.04f, center = Offset(w * 0.60f, h * 0.58f))
    drawCircle(MidnightNavy, radius = w * 0.018f, center = Offset(w * 0.78f, h * 0.56f))

    // Tiny forget-me-not resting beside bunny
    drawBloomedForgetMeNot(Offset(w * 0.84f, h * 0.76f), w * 0.11f)
}

private fun DrawScope.drawEnvelopeBunny(w: Float, h: Float, stroke: Stroke) {
    // Ears aligned symmetrically onto head crown (head center x=0.40w, top y=0.27h)
    rotate(-9f, pivot = Offset(w * 0.315f, h * 0.31f)) {
        drawOval(PureWhite, Offset(w * 0.255f, h * 0.05f), Size(w * 0.12f, h * 0.29f))
        drawOval(MidnightNavy, Offset(w * 0.255f, h * 0.05f), Size(w * 0.12f, h * 0.29f), style = stroke)
        drawOval(SkyBlue.copy(alpha = 0.68f), Offset(w * 0.287f, h * 0.11f), Size(w * 0.056f, h * 0.17f))
    }
    rotate(9f, pivot = Offset(w * 0.485f, h * 0.31f)) {
        drawOval(PureWhite, Offset(w * 0.425f, h * 0.05f), Size(w * 0.12f, h * 0.29f))
        drawOval(MidnightNavy, Offset(w * 0.425f, h * 0.05f), Size(w * 0.12f, h * 0.29f), style = stroke)
        drawOval(SkyBlue.copy(alpha = 0.68f), Offset(w * 0.457f, h * 0.11f), Size(w * 0.056f, h * 0.17f))
    }

    // Body & head
    drawOval(PureWhite, Offset(w * 0.18f, h * 0.52f), Size(w * 0.42f, h * 0.36f))
    drawOval(MidnightNavy, Offset(w * 0.18f, h * 0.52f), Size(w * 0.42f, h * 0.36f), style = stroke)

    drawOval(PureWhite, Offset(w * 0.19f, h * 0.27f), Size(w * 0.42f, h * 0.34f))
    drawOval(MidnightNavy, Offset(w * 0.19f, h * 0.27f), Size(w * 0.42f, h * 0.34f), style = stroke)

    drawCircle(MidnightNavy, radius = w * 0.025f, center = Offset(w * 0.34f, h * 0.42f))
    drawCircle(MidnightNavy, radius = w * 0.025f, center = Offset(w * 0.48f, h * 0.42f))
    drawCircle(SkyBlue, radius = w * 0.036f, center = Offset(w * 0.28f, h * 0.47f))

    // Envelope carried in front paws
    val envLeft = w * 0.46f
    val envTop = h * 0.50f
    val envW = w * 0.42f
    val envH = h * 0.30f
    drawRoundRect(
        color = PureWhite,
        topLeft = Offset(envLeft, envTop),
        size = Size(envW, envH),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
    )
    drawRoundRect(
        color = MidnightNavy,
        topLeft = Offset(envLeft, envTop),
        size = Size(envW, envH),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
        style = stroke
    )
    // Envelope flap
    val flap = Path().apply {
        moveTo(envLeft + w * 0.02f, envTop + h * 0.02f)
        lineTo(envLeft + envW / 2f, envTop + envH * 0.56f)
        lineTo(envLeft + envW - w * 0.02f, envTop + h * 0.02f)
    }
    drawPath(flap, MidnightNavy, style = stroke)

    // Pollen yellow wax seal on envelope
    drawCircle(
        color = PollenYellow,
        radius = w * 0.055f,
        center = Offset(envLeft + envW / 2f, envTop + envH * 0.58f)
    )
    drawCircle(
        color = ForgetMeNotBlue,
        radius = w * 0.025f,
        center = Offset(envLeft + envW / 2f, envTop + envH * 0.58f)
    )
}

private fun DrawScope.drawCelebratingBunny(w: Float, h: Float, stroke: Stroke) {
    // Joyful bunny with blooming forget-me-not right beside it
    drawGreetingBunny(w * 0.84f, h, stroke)

    // Happy sparkle accents in pollen yellow
    drawCircle(PollenYellow, radius = w * 0.035f, center = Offset(w * 0.80f, h * 0.22f))
    drawCircle(SkyBlue, radius = w * 0.028f, center = Offset(w * 0.90f, h * 0.34f))

    // Opened forget-me-not bloom beside the bunny
    drawBloomedForgetMeNot(Offset(w * 0.80f, h * 0.62f), w * 0.17f)
}

@Composable
fun BunnyHeadingRow(
    title: String,
    subtitle: String? = null,
    pose: BunnyPose = BunnyPose.GREETING,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallBunnyIllustration(
            pose = pose,
            size = 48.dp,
            contentDesc = "Forget-Me-Not bunny"
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
