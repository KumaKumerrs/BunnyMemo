package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.SkyBlue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class BunnyCompanionExpression {
    NEUTRAL,
    TALKING,
    LISTENING,
    HAPPY,
    SAD,
    PATTED,
    CHEEK_PINCHED
}

/**
 * Interactive animated monthly-breed bunny avatar supporting live expressions
 * (happy, sad with tears, talking, blissful head-pat squish, and squishy cheek pinching).
 */
@Composable
fun BunnyCompanionAvatar(
    breed: BunnyBreed,
    expression: BunnyCompanionExpression,
    isTalking: Boolean,
    patPulse: Float,
    leftCheekPull: Float,
    rightCheekPull: Float,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    contentDesc: String? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val reducedMotion = rememberReducedMotion()
    val needsExpressiveParticles = !reducedMotion && (
        expression == BunnyCompanionExpression.PATTED ||
            expression == BunnyCompanionExpression.HAPPY ||
            expression == BunnyCompanionExpression.LISTENING ||
            expression == BunnyCompanionExpression.SAD
        )

    val idleBreathState = if (reducedMotion) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        val breathTransition = rememberInfiniteTransition(label = "month_bunny_breath")
        breathTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (expression == BunnyCompanionExpression.SAD) 2800 else 2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bunny_breath"
        )
    }

    val talkCycleState = if (reducedMotion || !isTalking) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        val talkTransition = rememberInfiniteTransition(label = "month_bunny_talk")
        talkTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bunny_talk"
        )
    }

    val floatCycleState = if (!needsExpressiveParticles) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        val particleTransition = rememberInfiniteTransition(label = "month_bunny_particles")
        particleTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bunny_particles"
        )
    }

    val earDroopState = animateFloatAsState(
        targetValue = when (expression) {
            BunnyCompanionExpression.SAD -> 1f
            BunnyCompanionExpression.PATTED -> 0.35f
            BunnyCompanionExpression.HAPPY -> -0.22f
            BunnyCompanionExpression.LISTENING -> -0.35f
            BunnyCompanionExpression.CHEEK_PINCHED -> 0.18f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bunny_ear_droop"
    )

    val squishAmountState = animateFloatAsState(
        targetValue = when {
            patPulse > 0.05f -> 0.14f * patPulse
            expression == BunnyCompanionExpression.PATTED -> 0.08f
            expression == BunnyCompanionExpression.CHEEK_PINCHED -> 0.06f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bunny_squish"
    )

    val animatedLeftPullState = animateFloatAsState(
        targetValue = leftCheekPull.coerceIn(0f, 1.4f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "left_cheek_pull"
    )
    val animatedRightPullState = animateFloatAsState(
        targetValue = rightCheekPull.coerceIn(0f, 1.4f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "right_cheek_pull"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDesc != null) Modifier.semantics { contentDescription = contentDesc }
                else Modifier
            )
    ) {
        val idleBreath = idleBreathState.value
        val talkCycle = talkCycleState.value
        val floatCycle = floatCycleState.value
        val earDroop = earDroopState.value
        val squishAmount = squishAmountState.value
        val animatedLeftPull = animatedLeftPullState.value
        val animatedRightPull = animatedRightPullState.value

        val w = this.size.width
        val h = this.size.height
        val palette = breed.palette(isDarkTheme)
        val strokeW = (w * 0.036f).coerceIn(2.0f, 4.0f)
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val hopOffsetY = if (reducedMotion) 0f else when (expression) {
            BunnyCompanionExpression.HAPPY -> -h * 0.045f * abs(sin(floatCycle * PI)).toFloat()
            BunnyCompanionExpression.SAD -> h * 0.025f * idleBreath
            BunnyCompanionExpression.PATTED -> h * 0.02f * idleBreath
            else -> -h * 0.018f * idleBreath
        }

        val scaleX = 1f + squishAmount * 0.65f
        val scaleY = 1f - squishAmount * 0.80f
        val headTiltDeg = when (expression) {
            BunnyCompanionExpression.SAD -> -4.5f
            BunnyCompanionExpression.LISTENING -> 6.5f
            BunnyCompanionExpression.PATTED -> 4f * sin(floatCycle * PI).toFloat()
            BunnyCompanionExpression.CHEEK_PINCHED -> (animatedRightPull - animatedLeftPull) * 6f
            BunnyCompanionExpression.HAPPY -> (floatCycle - 0.5f) * 5f
            else -> 0f
        }

        // Soft shadow pad underneath bunny
        drawOval(
            color = palette.outlineInk.copy(alpha = 0.14f),
            topLeft = Offset(w * 0.24f, h * 0.86f),
            size = Size(w * 0.52f, h * 0.09f)
        )

        translate(left = 0f, top = hopOffsetY) {
            scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset(w * 0.5f, h * 0.85f)) {
                rotate(degrees = headTiltDeg, pivot = Offset(w * 0.5f, h * 0.66f)) {
                    drawInteractiveBreedBunny(
                        breed = breed,
                        p = palette,
                        expression = expression,
                        isTalking = isTalking,
                        talkOpen = if (isTalking) talkCycle else 0f,
                        earDroop = earDroop,
                        leftCheekPull = animatedLeftPull,
                        rightCheekPull = animatedRightPull,
                        w = w,
                        h = h,
                        stroke = stroke
                    )
                }
            }
        }

        // Floating expression particles (hearts on pat, sparkles on happy, teardrops on sad, squeak lines on pinch)
        if (!reducedMotion) {
            when (expression) {
                BunnyCompanionExpression.PATTED -> {
                    val rise = floatCycle * h * 0.10f
                    drawTinyHeart(
                        center = Offset(w * 0.19f, h * 0.22f - rise),
                        size = w * 0.075f,
                        color = Color(0xFFFF7AA2).copy(alpha = 0.92f - floatCycle * 0.25f)
                    )
                    drawTinyHeart(
                        center = Offset(w * 0.81f, h * 0.26f - rise * 0.7f),
                        size = w * 0.06f,
                        color = Color(0xFFFF96B8).copy(alpha = 0.88f)
                    )
                }
                BunnyCompanionExpression.HAPPY -> {
                    drawTinySparkle(
                        center = Offset(w * 0.16f, h * 0.24f - floatCycle * h * 0.05f),
                        radius = w * 0.055f,
                        color = PollenYellow
                    )
                    drawTinySparkle(
                        center = Offset(w * 0.85f, h * 0.20f + floatCycle * h * 0.04f),
                        radius = w * 0.048f,
                        color = SkyBlue
                    )
                }
                BunnyCompanionExpression.CHEEK_PINCHED -> {
                    // Cute comic surprise / squeak sparks by cheeks
                    val sparkColor = Color(0xFFFF7F9D)
                    drawLine(
                        color = sparkColor,
                        start = Offset(w * 0.12f, h * 0.48f),
                        end = Offset(w * 0.18f, h * 0.52f),
                        strokeWidth = strokeW * 0.9f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = sparkColor,
                        start = Offset(w * 0.11f, h * 0.56f),
                        end = Offset(w * 0.17f, h * 0.56f),
                        strokeWidth = strokeW * 0.9f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = sparkColor,
                        start = Offset(w * 0.88f, h * 0.48f),
                        end = Offset(w * 0.82f, h * 0.52f),
                        strokeWidth = strokeW * 0.9f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = sparkColor,
                        start = Offset(w * 0.89f, h * 0.56f),
                        end = Offset(w * 0.83f, h * 0.56f),
                        strokeWidth = strokeW * 0.9f,
                        cap = StrokeCap.Round
                    )
                }
                BunnyCompanionExpression.LISTENING -> {
                    // Pulsing sound-wave arcs beside the perked ear while listening to voice memo
                    val waveAlpha = (0.45f + floatCycle * 0.45f).coerceIn(0.2f, 0.95f)
                    val waveStroke = Stroke(width = strokeW * 0.85f, cap = StrokeCap.Round)
                    drawArc(
                        color = ForgetMeNotBlue.copy(alpha = waveAlpha),
                        startAngle = -50f,
                        sweepAngle = 75f,
                        useCenter = false,
                        topLeft = Offset(w * 0.72f, h * 0.14f),
                        size = Size(w * 0.12f, h * 0.14f),
                        style = waveStroke
                    )
                    drawArc(
                        color = SkyBlue.copy(alpha = 0.95f - floatCycle * 0.35f),
                        startAngle = -55f,
                        sweepAngle = 85f,
                        useCenter = false,
                        topLeft = Offset(w * 0.76f, h * 0.09f),
                        size = Size(w * 0.18f, h * 0.22f),
                        style = waveStroke
                    )
                    drawTinySparkle(
                        center = Offset(w * 0.17f, h * 0.22f),
                        radius = w * 0.042f,
                        color = ForgetMeNotBlue.copy(alpha = 0.75f)
                    )
                }
                BunnyCompanionExpression.SAD -> {
                    // Glistening soft tear bead below left eye
                    val dropOffset = floatCycle * h * 0.04f
                    drawCircle(
                        color = ForgetMeNotBlue.copy(alpha = 0.85f),
                        radius = w * 0.025f,
                        center = Offset(w * 0.37f, h * 0.51f + dropOffset)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = w * 0.009f,
                        center = Offset(w * 0.365f, h * 0.503f + dropOffset)
                    )
                }
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawInteractiveBreedBunny(
    breed: BunnyBreed,
    p: BunnyBreedPalette,
    expression: BunnyCompanionExpression,
    isTalking: Boolean,
    talkOpen: Float,
    earDroop: Float,
    leftCheekPull: Float,
    rightCheekPull: Float,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val isPointed = breed.markingStyle == BunnyMarkingStyle.POINTED_EXTREMITIES
    val isHarlequin = breed.markingStyle == BunnyMarkingStyle.HARLEQUIN_SPLIT
    val isDutch = breed.markingStyle == BunnyMarkingStyle.DUTCH_SADDLE
    val isSpotted = breed.markingStyle == BunnyMarkingStyle.ENGLISH_SPOTTED

    val leftEarColor = when {
        isPointed || isDutch || isSpotted || isHarlequin -> p.secondaryPatch
        else -> p.primaryCoat
    }
    val rightEarColor = when {
        isPointed || isDutch || isSpotted -> p.secondaryPatch
        else -> p.primaryCoat
    }

    // Upright ears with expressive droop or perk rotation
    if (breed.earStyle != BunnyEarStyle.LOP) {
        drawExpressiveCrownEars(
            earStyle = breed.earStyle,
            leftColor = leftEarColor,
            rightColor = rightEarColor,
            innerColor = p.innerEar,
            outlineColor = p.outlineInk,
            headCenterX = w * 0.50f,
            crownY = h * 0.35f,
            earDroop = earDroop,
            w = w,
            h = h,
            stroke = stroke
        )
    }

    // Tail
    val tailColor = if (isPointed) p.secondaryPatch else p.primaryCoat
    drawCircle(tailColor, radius = w * 0.08f, center = Offset(w * 0.25f, h * 0.78f))
    drawCircle(p.outlineInk, radius = w * 0.08f, center = Offset(w * 0.25f, h * 0.78f), style = stroke)

    // Body
    if (breed.markingStyle == BunnyMarkingStyle.ANGORA_CLOUD) {
        drawScallopedFluffRing(
            center = Offset(w * 0.50f, h * 0.72f),
            rx = w * 0.24f,
            ry = h * 0.19f,
            puffCount = 10,
            puffRadius = w * 0.065f,
            fill = p.primaryCoat,
            outline = p.outlineInk,
            stroke = stroke
        )
    } else {
        drawOval(p.primaryCoat, Offset(w * 0.28f, h * 0.54f), Size(w * 0.44f, h * 0.35f))
        drawBodyMarkings(breed, p, w * 0.28f, h * 0.54f, w * 0.44f, h * 0.35f, w, h)
        drawOval(p.outlineInk, Offset(w * 0.28f, h * 0.54f), Size(w * 0.44f, h * 0.35f), style = stroke)
    }

    // Lionhead mane halo
    if (breed.markingStyle == BunnyMarkingStyle.LION_MANE) {
        drawScallopedFluffRing(
            center = Offset(w * 0.50f, h * 0.49f),
            rx = w * 0.26f,
            ry = h * 0.20f,
            puffCount = 12,
            puffRadius = w * 0.062f,
            fill = p.secondaryPatch,
            outline = p.outlineInk,
            stroke = stroke
        )
    }

    // Head with cheek stretch when pinched!
    val baseLeft = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) w * 0.25f else w * 0.265f
    val headTop = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) h * 0.32f else h * 0.30f
    val baseW = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) w * 0.50f else w * 0.47f
    val headH = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) h * 0.36f else h * 0.35f

    val leftExpand = w * 0.055f * leftCheekPull
    val rightExpand = w * 0.055f * rightCheekPull
    val headLeft = baseLeft - leftExpand
    val headW = baseW + leftExpand + rightExpand

    drawOval(p.primaryCoat, Offset(headLeft, headTop), Size(headW, headH))
    drawHeadMarkings(breed, p, headLeft, headTop, headW, headH, w, h)
    drawOval(p.outlineInk, Offset(headLeft, headTop), Size(headW, headH), style = stroke)

    // Extra pinched puffy cheek bulge on left / right when squeezed
    if (leftCheekPull > 0.05f) {
        val bulgeCx = headLeft + w * 0.045f
        val bulgeCy = headTop + headH * 0.62f
        val bulgeColor = if (isHarlequin) p.secondaryPatch else p.primaryCoat
        drawCircle(bulgeColor, radius = w * (0.065f + 0.025f * leftCheekPull), center = Offset(bulgeCx, bulgeCy))
        drawArc(
            color = p.outlineInk,
            startAngle = 105f,
            sweepAngle = 155f,
            useCenter = false,
            topLeft = Offset(bulgeCx - w * (0.065f + 0.025f * leftCheekPull), bulgeCy - w * (0.065f + 0.025f * leftCheekPull)),
            size = Size(w * (0.13f + 0.05f * leftCheekPull), w * (0.13f + 0.05f * leftCheekPull)),
            style = stroke
        )
    }
    if (rightCheekPull > 0.05f) {
        val bulgeCx = headLeft + headW - w * 0.045f
        val bulgeCy = headTop + headH * 0.62f
        drawCircle(p.primaryCoat, radius = w * (0.065f + 0.025f * rightCheekPull), center = Offset(bulgeCx, bulgeCy))
        drawArc(
            color = p.outlineInk,
            startAngle = -80f,
            sweepAngle = 155f,
            useCenter = false,
            topLeft = Offset(bulgeCx - w * (0.065f + 0.025f * rightCheekPull), bulgeCy - w * (0.065f + 0.025f * rightCheekPull)),
            size = Size(w * (0.13f + 0.05f * rightCheekPull), w * (0.13f + 0.05f * rightCheekPull)),
            style = stroke
        )
    }

    // Holland Lop drooping ears
    if (breed.earStyle == BunnyEarStyle.LOP) {
        val lopAngleOffset = earDroop * 10f
        rotate(14f + lopAngleOffset, pivot = Offset(w * 0.28f, h * 0.42f)) {
            drawOval(p.secondaryPatch, Offset(headLeft - w * 0.095f, h * 0.37f), Size(w * 0.145f, h * 0.34f))
            drawOval(p.outlineInk, Offset(headLeft - w * 0.095f, h * 0.37f), Size(w * 0.145f, h * 0.34f), style = stroke)
            drawOval(p.innerEar.copy(alpha = 0.72f), Offset(headLeft - w * 0.06f, h * 0.45f), Size(w * 0.065f, h * 0.19f))
        }
        rotate(-14f - lopAngleOffset, pivot = Offset(w * 0.72f, h * 0.42f)) {
            drawOval(p.secondaryPatch, Offset(headLeft + headW - w * 0.05f, h * 0.37f), Size(w * 0.145f, h * 0.34f))
            drawOval(p.outlineInk, Offset(headLeft + headW - w * 0.05f, h * 0.37f), Size(w * 0.145f, h * 0.34f), style = stroke)
            drawOval(p.innerEar.copy(alpha = 0.72f), Offset(headLeft + headW - w * 0.015f, h * 0.45f), Size(w * 0.065f, h * 0.19f))
        }
    }

    // Angora forehead bangs fluff
    if (breed.markingStyle == BunnyMarkingStyle.ANGORA_CLOUD) {
        drawCircle(p.primaryCoat, radius = w * 0.068f, center = Offset(w * 0.44f, h * 0.32f))
        drawCircle(p.primaryCoat, radius = w * 0.072f, center = Offset(w * 0.52f, h * 0.31f))
        drawCircle(p.primaryCoat, radius = w * 0.062f, center = Offset(w * 0.58f, h * 0.33f))
    }

    // Expressive face (eyes, blush, nose, mouth)
    drawExpressiveFace(
        breed = breed,
        p = p,
        expression = expression,
        isTalking = isTalking,
        talkOpen = talkOpen,
        leftCheekPull = leftCheekPull,
        rightCheekPull = rightCheekPull,
        w = w,
        h = h,
        stroke = stroke
    )

    // Front paws
    val pawColor = if (isPointed) p.secondaryPatch else p.primaryCoat
    val leftPawY = if (expression == BunnyCompanionExpression.HAPPY) h * 0.79f else h * 0.82f
    val rightPawY = if (expression == BunnyCompanionExpression.HAPPY) h * 0.79f else h * 0.82f
    drawOval(pawColor, Offset(w * 0.34f, leftPawY), Size(w * 0.12f, h * 0.09f))
    drawOval(p.outlineInk, Offset(w * 0.34f, leftPawY), Size(w * 0.12f, h * 0.09f), style = stroke)
    drawOval(pawColor, Offset(w * 0.54f, rightPawY), Size(w * 0.12f, h * 0.09f))
    drawOval(p.outlineInk, Offset(w * 0.54f, rightPawY), Size(w * 0.12f, h * 0.09f), style = stroke)
}

private fun DrawScope.drawExpressiveCrownEars(
    earStyle: BunnyEarStyle,
    leftColor: Color,
    rightColor: Color,
    innerColor: Color,
    outlineColor: Color,
    headCenterX: Float,
    crownY: Float,
    earDroop: Float,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val earW = when (earStyle) {
        BunnyEarStyle.SHORT_DWARF, BunnyEarStyle.MANE_SHORT -> w * 0.115f
        BunnyEarStyle.TALL_BROAD -> w * 0.145f
        else -> w * 0.13f
    }
    val earH = when (earStyle) {
        BunnyEarStyle.SHORT_DWARF -> h * 0.21f
        BunnyEarStyle.TALL_BROAD -> h * 0.38f
        BunnyEarStyle.MANE_SHORT -> h * 0.23f
        BunnyEarStyle.TASSELED_ANGORA -> h * 0.29f
        else -> h * 0.32f
    }
    val earTopY = when (earStyle) {
        BunnyEarStyle.SHORT_DWARF -> h * 0.17f
        BunnyEarStyle.TALL_BROAD -> h * 0.02f
        BunnyEarStyle.MANE_SHORT -> h * 0.13f
        BunnyEarStyle.TASSELED_ANGORA -> h * 0.08f
        else -> h * 0.06f
    }
    val baseAngle = when (earStyle) {
        BunnyEarStyle.SHORT_DWARF -> 11f
        BunnyEarStyle.TALL_BROAD -> 14f
        BunnyEarStyle.MANE_SHORT -> 12f
        else -> 10f
    }
    // Sad droops ears wide and downward (-32/+32 extra tilt), Happy perks them upright
    val angleDelta = earDroop * 34f
    val leftPivotX = headCenterX - w * 0.105f
    val rightPivotX = headCenterX + w * 0.105f

    rotate(-(baseAngle + angleDelta), pivot = Offset(leftPivotX, crownY)) {
        val leftX = leftPivotX - earW / 2f
        drawOval(leftColor, Offset(leftX, earTopY), Size(earW, earH))
        drawOval(outlineColor, Offset(leftX, earTopY), Size(earW, earH), style = stroke)
        drawOval(
            innerColor.copy(alpha = 0.82f),
            Offset(leftX + earW * 0.26f, earTopY + earH * 0.19f),
            Size(earW * 0.48f, earH * 0.58f)
        )
        if (earStyle == BunnyEarStyle.TASSELED_ANGORA) {
            drawCircle(leftColor, radius = earW * 0.36f, center = Offset(leftPivotX, earTopY))
            drawCircle(outlineColor, radius = earW * 0.36f, center = Offset(leftPivotX, earTopY), style = stroke)
        }
    }

    rotate(baseAngle + angleDelta, pivot = Offset(rightPivotX, crownY)) {
        val rightX = rightPivotX - earW / 2f
        drawOval(rightColor, Offset(rightX, earTopY), Size(earW, earH))
        drawOval(outlineColor, Offset(rightX, earTopY), Size(earW, earH), style = stroke)
        drawOval(
            innerColor.copy(alpha = 0.82f),
            Offset(rightX + earW * 0.26f, earTopY + earH * 0.19f),
            Size(earW * 0.48f, earH * 0.58f)
        )
        if (earStyle == BunnyEarStyle.TASSELED_ANGORA) {
            drawCircle(rightColor, radius = earW * 0.36f, center = Offset(rightPivotX, earTopY))
            drawCircle(outlineColor, radius = earW * 0.36f, center = Offset(rightPivotX, earTopY), style = stroke)
        }
    }
}

private fun DrawScope.drawExpressiveFace(
    breed: BunnyBreed,
    p: BunnyBreedPalette,
    expression: BunnyCompanionExpression,
    isTalking: Boolean,
    talkOpen: Float,
    leftCheekPull: Float,
    rightCheekPull: Float,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val eyeY = h * 0.45f
    val leftEyeX = w * 0.41f
    val rightEyeX = w * 0.59f

    // Breed eye markings underneath eyes
    if (breed.markingStyle == BunnyMarkingStyle.HOTOT_EYELINER) {
        drawCircle(p.secondaryPatch, radius = w * 0.048f, center = Offset(leftEyeX, eyeY))
        drawCircle(p.secondaryPatch, radius = w * 0.048f, center = Offset(rightEyeX, eyeY))
    } else if (breed.markingStyle == BunnyMarkingStyle.ENGLISH_SPOTTED) {
        drawOval(p.secondaryPatch, Offset(leftEyeX - w * 0.048f, eyeY - h * 0.042f), Size(w * 0.096f, h * 0.084f))
        drawOval(p.secondaryPatch, Offset(rightEyeX - w * 0.048f, eyeY - h * 0.042f), Size(w * 0.096f, h * 0.084f))
    } else if (breed.markingStyle == BunnyMarkingStyle.DWARF_MARTEN) {
        drawCircle(p.bellyCoat, radius = w * 0.046f, center = Offset(leftEyeX, eyeY))
        drawCircle(p.bellyCoat, radius = w * 0.046f, center = Offset(rightEyeX, eyeY))
    }

    val eyeRadius = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) w * 0.034f else w * 0.029f
    val eyeStroke = Stroke(width = stroke.width * 0.95f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (expression) {
        BunnyCompanionExpression.HAPPY -> {
            // Joyful upward crescent ^ ^ eyes
            val leftEye = Path().apply {
                moveTo(leftEyeX - w * 0.036f, eyeY + h * 0.01f)
                quadraticTo(leftEyeX, eyeY - h * 0.035f, leftEyeX + w * 0.036f, eyeY + h * 0.01f)
            }
            val rightEye = Path().apply {
                moveTo(rightEyeX - w * 0.036f, eyeY + h * 0.01f)
                quadraticTo(rightEyeX, eyeY - h * 0.035f, rightEyeX + w * 0.036f, eyeY + h * 0.01f)
            }
            drawPath(leftEye, p.outlineInk, style = eyeStroke)
            drawPath(rightEye, p.outlineInk, style = eyeStroke)
        }

        BunnyCompanionExpression.PATTED -> {
            // Blissful relaxed downward curve ˘ ˘ eyes
            val leftEye = Path().apply {
                moveTo(leftEyeX - w * 0.038f, eyeY - h * 0.008f)
                quadraticTo(leftEyeX, eyeY + h * 0.026f, leftEyeX + w * 0.038f, eyeY - h * 0.008f)
            }
            val rightEye = Path().apply {
                moveTo(rightEyeX - w * 0.038f, eyeY - h * 0.008f)
                quadraticTo(rightEyeX, eyeY + h * 0.026f, rightEyeX + w * 0.038f, eyeY - h * 0.008f)
            }
            drawPath(leftEye, p.outlineInk, style = eyeStroke)
            drawPath(rightEye, p.outlineInk, style = eyeStroke)
        }

        BunnyCompanionExpression.CHEEK_PINCHED -> {
            // Squished cute > < eyes!
            val leftChevron = Path().apply {
                moveTo(leftEyeX - w * 0.032f, eyeY - h * 0.022f)
                lineTo(leftEyeX + w * 0.022f, eyeY)
                lineTo(leftEyeX - w * 0.032f, eyeY + h * 0.022f)
            }
            val rightChevron = Path().apply {
                moveTo(rightEyeX + w * 0.032f, eyeY - h * 0.022f)
                lineTo(rightEyeX - w * 0.022f, eyeY)
                lineTo(rightEyeX + w * 0.032f, eyeY + h * 0.022f)
            }
            drawPath(leftChevron, p.outlineInk, style = eyeStroke)
            drawPath(rightChevron, p.outlineInk, style = eyeStroke)
        }

        BunnyCompanionExpression.SAD -> {
            // Teary worried eyes + angled sad eyebrows
            drawCircle(p.eyeColor, radius = eyeRadius * 0.95f, center = Offset(leftEyeX, eyeY + h * 0.01f))
            drawCircle(p.eyeColor, radius = eyeRadius * 0.95f, center = Offset(rightEyeX, eyeY + h * 0.01f))
            drawCircle(Color.White, radius = eyeRadius * 0.34f, center = Offset(leftEyeX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.12f))
            drawCircle(Color.White, radius = eyeRadius * 0.34f, center = Offset(rightEyeX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.12f))
            // Glistening blue lower tear rim
            drawArc(
                color = ForgetMeNotBlue,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(leftEyeX - eyeRadius * 1.1f, eyeY - eyeRadius * 0.2f),
                size = Size(eyeRadius * 2.2f, eyeRadius * 1.8f),
                style = Stroke(width = stroke.width * 0.7f, cap = StrokeCap.Round)
            )
            // Worried eyebrows (/ \)
            drawLine(
                color = p.outlineInk,
                start = Offset(leftEyeX - w * 0.035f, eyeY - h * 0.032f),
                end = Offset(leftEyeX + w * 0.025f, eyeY - h * 0.052f),
                strokeWidth = stroke.width * 0.75f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = p.outlineInk,
                start = Offset(rightEyeX - w * 0.025f, eyeY - h * 0.052f),
                end = Offset(rightEyeX + w * 0.035f, eyeY - h * 0.032f),
                strokeWidth = stroke.width * 0.75f,
                cap = StrokeCap.Round
            )
        }

        BunnyCompanionExpression.NEUTRAL,
        BunnyCompanionExpression.TALKING,
        BunnyCompanionExpression.LISTENING -> {
            val scale = if (expression == BunnyCompanionExpression.LISTENING) 1.08f else 1.0f
            drawCircle(p.eyeColor, radius = eyeRadius * scale, center = Offset(leftEyeX, eyeY))
            drawCircle(p.eyeColor, radius = eyeRadius * scale, center = Offset(rightEyeX, eyeY))
            drawCircle(Color.White, radius = eyeRadius * 0.36f, center = Offset(leftEyeX - eyeRadius * 0.3f, eyeY - eyeRadius * 0.3f))
            drawCircle(Color.White, radius = eyeRadius * 0.36f, center = Offset(rightEyeX - eyeRadius * 0.3f, eyeY - eyeRadius * 0.3f))
        }
    }

    // Extra rosy blush on pat / pinch / happy
    val blushAlpha = when (expression) {
        BunnyCompanionExpression.PATTED, BunnyCompanionExpression.CHEEK_PINCHED -> 0.96f
        BunnyCompanionExpression.SAD -> 0.45f
        else -> 0.85f
    }
    val blushScale = if (expression == BunnyCompanionExpression.PATTED || expression == BunnyCompanionExpression.CHEEK_PINCHED) 1.24f else 1.0f
    val blushColor = if (expression == BunnyCompanionExpression.CHEEK_PINCHED) {
        Color(0xFFFF8AA6).copy(alpha = blushAlpha)
    } else {
        p.blushCheek.copy(alpha = blushAlpha)
    }

    drawCircle(
        color = blushColor,
        radius = w * 0.042f * blushScale,
        center = Offset(w * 0.34f - w * 0.038f * leftCheekPull, h * 0.515f)
    )
    drawCircle(
        color = blushColor,
        radius = w * 0.042f * blushScale,
        center = Offset(w * 0.66f + w * 0.038f * rightCheekPull, h * 0.515f)
    )

    // Nose & breed muzzle marking
    when (breed.markingStyle) {
        BunnyMarkingStyle.POINTED_EXTREMITIES -> {
            drawOval(p.secondaryPatch, Offset(w * 0.445f, h * 0.475f), Size(w * 0.11f, h * 0.068f))
        }
        BunnyMarkingStyle.ENGLISH_SPOTTED -> {
            drawCircle(p.noseColor, radius = w * 0.023f, center = Offset(w * 0.475f, h * 0.50f))
            drawCircle(p.noseColor, radius = w * 0.023f, center = Offset(w * 0.525f, h * 0.50f))
            drawCircle(p.noseColor, radius = w * 0.019f, center = Offset(w * 0.50f, h * 0.485f))
        }
        else -> {
            drawCircle(p.noseColor, radius = w * 0.019f, center = Offset(w * 0.50f, h * 0.50f))
        }
    }

    // Expressive mouth (animated when talking with 8-bit voice!)
    when {
        isTalking && talkOpen > 0.35f -> {
            val openH = h * (0.022f + 0.022f * talkOpen)
            drawOval(
                color = Color(0xFFE77F96),
                topLeft = Offset(w * 0.472f, h * 0.528f),
                size = Size(w * 0.056f, openH)
            )
            drawOval(
                color = p.outlineInk,
                topLeft = Offset(w * 0.472f, h * 0.528f),
                size = Size(w * 0.056f, openH),
                style = Stroke(width = stroke.width * 0.72f)
            )
        }
        expression == BunnyCompanionExpression.SAD -> {
            val frown = Path().apply {
                moveTo(w * 0.455f, h * 0.555f)
                quadraticTo(w * 0.50f, h * 0.525f, w * 0.545f, h * 0.555f)
            }
            drawPath(frown, p.outlineInk, style = stroke)
        }
        expression == BunnyCompanionExpression.CHEEK_PINCHED -> {
            // Squishy wavy "ω" mouth
            val wavy = Path().apply {
                moveTo(w * 0.452f, h * 0.538f)
                quadraticTo(w * 0.476f, h * 0.562f, w * 0.50f, h * 0.538f)
                quadraticTo(w * 0.524f, h * 0.562f, w * 0.548f, h * 0.538f)
            }
            drawPath(wavy, p.outlineInk, style = stroke)
        }
        expression == BunnyCompanionExpression.LISTENING -> {
            // Attentive cute "o" listening mouth
            drawCircle(
                color = Color(0xFFE77F96).copy(alpha = 0.78f),
                radius = w * 0.020f,
                center = Offset(w * 0.50f, h * 0.542f)
            )
            drawCircle(
                color = p.outlineInk,
                radius = w * 0.020f,
                center = Offset(w * 0.50f, h * 0.542f),
                style = Stroke(width = stroke.width * 0.72f)
            )
        }
        expression == BunnyCompanionExpression.HAPPY || expression == BunnyCompanionExpression.PATTED -> {
            // Cheerful ":3" double-curve bunny smile
            val happyMouth = Path().apply {
                moveTo(w * 0.448f, h * 0.532f)
                quadraticTo(w * 0.474f, h * 0.568f, w * 0.50f, h * 0.536f)
                quadraticTo(w * 0.526f, h * 0.568f, w * 0.552f, h * 0.532f)
            }
            drawPath(happyMouth, p.outlineInk, style = stroke)
        }
        else -> {
            val smile = Path().apply {
                moveTo(w * 0.46f, h * 0.535f)
                quadraticTo(w * 0.50f, h * 0.565f, w * 0.54f, h * 0.535f)
            }
            drawPath(smile, p.outlineInk, style = stroke)
        }
    }
}

private fun DrawScope.drawTinyHeart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.45f)
        cubicTo(
            center.x - size * 0.65f, center.y + size * 0.05f,
            center.x - size * 0.55f, center.y - size * 0.55f,
            center.x, center.y - size * 0.15f
        )
        cubicTo(
            center.x + size * 0.55f, center.y - size * 0.55f,
            center.x + size * 0.65f, center.y + size * 0.05f,
            center.x, center.y + size * 0.45f
        )
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawTinySparkle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}
