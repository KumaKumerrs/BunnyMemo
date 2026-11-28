package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.SkyBlue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawBreedBunnyPose(
    pose: BunnyPose,
    breed: BunnyBreed,
    isDarkTheme: Boolean,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val palette = breed.palette(isDarkTheme)
    when (pose) {
        BunnyPose.GREETING -> drawGreetingBreedBunny(breed, palette, w, h, stroke)
        BunnyPose.RESTING -> drawRestingBreedBunny(breed, palette, w, h, stroke)
        BunnyPose.ENVELOPE -> drawEnvelopeBreedBunny(breed, palette, w, h, stroke)
        BunnyPose.CELEBRATING -> {
            // Scale uniformly (anchored bottom-left) so the bunny is not horizontally squashed.
            scale(scale = 0.84f, pivot = Offset(0f, h)) {
                drawGreetingBreedBunny(breed, palette, w, h, stroke)
            }
            drawCircle(PollenYellow, radius = w * 0.035f, center = Offset(w * 0.80f, h * 0.22f))
            drawCircle(SkyBlue, radius = w * 0.028f, center = Offset(w * 0.90f, h * 0.34f))
            drawInlineForgetMeNot(Offset(w * 0.80f, h * 0.62f), w * 0.17f)
        }
        BunnyPose.STUDYING, BunnyPose.PATTED, BunnyPose.GUARD -> {
            drawGreetingBreedBunny(breed, palette, w, h, stroke)
        }
    }
}

private fun DrawScope.drawGreetingBreedBunny(
    breed: BunnyBreed,
    p: BunnyBreedPalette,
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

    // Back/upright ears drawn behind head unless Holland Lop (which drapes beside head)
    if (breed.earStyle != BunnyEarStyle.LOP) {
        drawCrownEars(
            earStyle = breed.earStyle,
            leftColor = leftEarColor,
            rightColor = rightEarColor,
            innerColor = p.innerEar,
            outlineColor = p.outlineInk,
            headCenterX = w * 0.50f,
            crownY = h * 0.34f,
            w = w,
            h = h,
            stroke = stroke
        )
    }

    // Tail
    val tailColor = if (isPointed) p.secondaryPatch else p.primaryCoat
    drawCircle(tailColor, radius = w * 0.08f, center = Offset(w * 0.25f, h * 0.78f))
    drawCircle(p.outlineInk, radius = w * 0.08f, center = Offset(w * 0.25f, h * 0.78f), style = stroke)

    // Body (Angora gets fluffy wool scallops around torso)
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
        drawOval(p.primaryCoat, Offset(w * 0.28f, h * 0.54f), Size(w * 0.44f, h * 0.36f))
        drawBodyMarkings(breed, p, w * 0.28f, h * 0.54f, w * 0.44f, h * 0.36f, w, h)
        drawOval(p.outlineInk, Offset(w * 0.28f, h * 0.54f), Size(w * 0.44f, h * 0.36f), style = stroke)
    }

    // Lionhead fluffy mane halo behind head
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

    // Head shape (Netherland Dwarf is slightly rounder/chibi)
    val headLeft = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) w * 0.255f else w * 0.27f
    val headTop = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) h * 0.32f else h * 0.30f
    val headW = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) w * 0.49f else w * 0.46f
    val headH = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) h * 0.36f else h * 0.35f

    drawOval(p.primaryCoat, Offset(headLeft, headTop), Size(headW, headH))
    drawHeadMarkings(breed, p, headLeft, headTop, headW, headH, w, h)
    drawOval(p.outlineInk, Offset(headLeft, headTop), Size(headW, headH), style = stroke)

    // Holland Lop drooping ears drawn beside cheeks
    if (breed.earStyle == BunnyEarStyle.LOP) {
        drawLopEarsGreeting(p, w, h, stroke)
    }

    // Angora forehead bangs fluff
    if (breed.markingStyle == BunnyMarkingStyle.ANGORA_CLOUD) {
        drawCircle(p.primaryCoat, radius = w * 0.068f, center = Offset(w * 0.44f, h * 0.32f))
        drawCircle(p.primaryCoat, radius = w * 0.072f, center = Offset(w * 0.52f, h * 0.31f))
        drawCircle(p.primaryCoat, radius = w * 0.062f, center = Offset(w * 0.58f, h * 0.33f))
    }

    // Facial features (eyes, Hotot rings, blush, nose, smile)
    drawGreetingFace(breed, p, w, h, stroke)

    // Front paws
    val pawColor = if (isPointed) p.secondaryPatch else p.primaryCoat
    drawOval(pawColor, Offset(w * 0.34f, h * 0.82f), Size(w * 0.12f, h * 0.09f))
    drawOval(p.outlineInk, Offset(w * 0.34f, h * 0.82f), Size(w * 0.12f, h * 0.09f), style = stroke)
    drawOval(pawColor, Offset(w * 0.54f, h * 0.82f), Size(w * 0.12f, h * 0.09f))
    drawOval(p.outlineInk, Offset(w * 0.54f, h * 0.82f), Size(w * 0.12f, h * 0.09f), style = stroke)
}

internal fun DrawScope.drawCrownEars(
    earStyle: BunnyEarStyle,
    leftColor: Color,
    rightColor: Color,
    innerColor: Color,
    outlineColor: Color,
    headCenterX: Float,
    crownY: Float,
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
    val angleDeg = when (earStyle) {
        BunnyEarStyle.SHORT_DWARF -> 11f
        BunnyEarStyle.TALL_BROAD -> 14f
        BunnyEarStyle.MANE_SHORT -> 12f
        else -> 10f
    }

    val leftPivotX = headCenterX - w * 0.105f
    val rightPivotX = headCenterX + w * 0.105f

    rotate(-angleDeg, pivot = Offset(leftPivotX, crownY)) {
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

    rotate(angleDeg, pivot = Offset(rightPivotX, crownY)) {
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

private fun DrawScope.drawLopEarsGreeting(p: BunnyBreedPalette, w: Float, h: Float, stroke: Stroke) {
    rotate(14f, pivot = Offset(w * 0.28f, h * 0.42f)) {
        drawOval(p.secondaryPatch, Offset(w * 0.17f, h * 0.37f), Size(w * 0.145f, h * 0.34f))
        drawOval(p.outlineInk, Offset(w * 0.17f, h * 0.37f), Size(w * 0.145f, h * 0.34f), style = stroke)
        drawOval(p.innerEar.copy(alpha = 0.72f), Offset(w * 0.205f, h * 0.45f), Size(w * 0.065f, h * 0.19f))
    }
    rotate(-14f, pivot = Offset(w * 0.72f, h * 0.42f)) {
        drawOval(p.secondaryPatch, Offset(w * 0.685f, h * 0.37f), Size(w * 0.145f, h * 0.34f))
        drawOval(p.outlineInk, Offset(w * 0.685f, h * 0.37f), Size(w * 0.145f, h * 0.34f), style = stroke)
        drawOval(p.innerEar.copy(alpha = 0.72f), Offset(w * 0.73f, h * 0.45f), Size(w * 0.065f, h * 0.19f))
    }
}

internal fun DrawScope.drawBodyMarkings(
    breed: BunnyBreed,
    p: BunnyBreedPalette,
    bx: Float,
    by: Float,
    bw: Float,
    bh: Float,
    w: Float,
    h: Float
) {
    val bodyClip = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(bx, by, bx + bw, by + bh))
    }
    clipPath(bodyClip) {
        when (breed.markingStyle) {
            BunnyMarkingStyle.DUTCH_SADDLE -> {
                drawRect(
                    color = p.secondaryPatch,
                    topLeft = Offset(bx, by + bh * 0.34f),
                    size = Size(bw, bh * 0.68f)
                )
            }
            BunnyMarkingStyle.CREAM_BELLY,
            BunnyMarkingStyle.LOP_CHEEKS,
            BunnyMarkingStyle.DWARF_MARTEN -> {
                drawOval(
                    color = p.bellyCoat,
                    topLeft = Offset(bx + bw * 0.22f, by + bh * 0.10f),
                    size = Size(bw * 0.56f, bh * 0.76f)
                )
            }
            BunnyMarkingStyle.ENGLISH_SPOTTED -> {
                drawCircle(p.secondaryPatch, radius = w * 0.042f, center = Offset(bx + bw * 0.26f, by + bh * 0.36f))
                drawCircle(p.secondaryPatch, radius = w * 0.032f, center = Offset(bx + bw * 0.74f, by + bh * 0.42f))
                drawCircle(p.secondaryPatch, radius = w * 0.035f, center = Offset(bx + bw * 0.54f, by + bh * 0.66f))
                drawCircle(p.secondaryPatch, radius = w * 0.025f, center = Offset(bx + bw * 0.30f, by + bh * 0.68f))
            }
            BunnyMarkingStyle.HARLEQUIN_SPLIT -> {
                drawRect(
                    color = p.secondaryPatch,
                    topLeft = Offset(bx + bw * 0.50f, by),
                    size = Size(bw * 0.52f, bh)
                )
            }
            else -> Unit
        }
    }
}

internal fun DrawScope.drawHeadMarkings(
    breed: BunnyBreed,
    p: BunnyBreedPalette,
    hx: Float,
    hy: Float,
    hw: Float,
    hh: Float,
    w: Float,
    h: Float
) {
    val headClip = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(hx, hy, hx + hw, hy + hh))
    }
    clipPath(headClip) {
        when (breed.markingStyle) {
            BunnyMarkingStyle.DUTCH_SADDLE -> {
                drawOval(p.secondaryPatch, Offset(hx - hw * 0.08f, hy), Size(hw * 0.44f, hh * 0.88f))
                drawOval(p.secondaryPatch, Offset(hx + hw * 0.64f, hy), Size(hw * 0.44f, hh * 0.88f))
            }
            BunnyMarkingStyle.HARLEQUIN_SPLIT -> {
                drawRect(
                    color = p.secondaryPatch,
                    topLeft = Offset(hx, hy),
                    size = Size(hw * 0.50f, hh * 0.92f)
                )
            }
            BunnyMarkingStyle.DWARF_MARTEN -> {
                drawOval(
                    color = p.bellyCoat,
                    topLeft = Offset(hx + hw * 0.18f, hy + hh * 0.54f),
                    size = Size(hw * 0.64f, hh * 0.46f)
                )
            }
            else -> Unit
        }
    }
}

private fun DrawScope.drawGreetingFace(
    breed: BunnyBreed,
    p: BunnyBreedPalette,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val eyeY = h * 0.45f
    val leftEyeX = w * 0.415f
    val rightEyeX = w * 0.585f

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

    val eyeRadius = if (breed.earStyle == BunnyEarStyle.SHORT_DWARF) w * 0.033f else w * 0.028f
    drawCircle(p.eyeColor, radius = eyeRadius, center = Offset(leftEyeX, eyeY))
    drawCircle(p.eyeColor, radius = eyeRadius, center = Offset(rightEyeX, eyeY))

    if (breed.markingStyle == BunnyMarkingStyle.HOTOT_EYELINER ||
        breed.markingStyle == BunnyMarkingStyle.ENGLISH_SPOTTED ||
        breed.earStyle == BunnyEarStyle.SHORT_DWARF ||
        breed.markingStyle == BunnyMarkingStyle.HARLEQUIN_SPLIT
    ) {
        drawCircle(Color.White, radius = eyeRadius * 0.34f, center = Offset(leftEyeX - eyeRadius * 0.3f, eyeY - eyeRadius * 0.3f))
        drawCircle(Color.White, radius = eyeRadius * 0.34f, center = Offset(rightEyeX - eyeRadius * 0.3f, eyeY - eyeRadius * 0.3f))
    }

    // Cheek blush
    drawCircle(p.blushCheek, radius = w * 0.04f, center = Offset(w * 0.345f, h * 0.51f))
    drawCircle(p.blushCheek, radius = w * 0.04f, center = Offset(w * 0.655f, h * 0.51f))

    // Nose & butterfly/point smudge
    when (breed.markingStyle) {
        BunnyMarkingStyle.POINTED_EXTREMITIES -> {
            drawOval(p.secondaryPatch, Offset(w * 0.445f, h * 0.475f), Size(w * 0.11f, h * 0.068f))
        }
        BunnyMarkingStyle.ENGLISH_SPOTTED -> {
            drawCircle(p.noseColor, radius = w * 0.024f, center = Offset(w * 0.475f, h * 0.50f))
            drawCircle(p.noseColor, radius = w * 0.024f, center = Offset(w * 0.525f, h * 0.50f))
            drawCircle(p.noseColor, radius = w * 0.020f, center = Offset(w * 0.50f, h * 0.485f))
        }
        else -> {
            drawCircle(p.noseColor, radius = w * 0.019f, center = Offset(w * 0.50f, h * 0.50f))
        }
    }

    val smile = Path().apply {
        moveTo(w * 0.46f, h * 0.535f)
        quadraticTo(w * 0.50f, h * 0.565f, w * 0.54f, h * 0.535f)
    }
    drawPath(smile, p.outlineInk, style = stroke)
}

internal fun DrawScope.drawScallopedFluffRing(
    center: Offset,
    rx: Float,
    ry: Float,
    puffCount: Int,
    puffRadius: Float,
    fill: Color,
    outline: Color,
    stroke: Stroke
) {
    for (i in 0 until puffCount) {
        val angle = (2.0 * PI * i) / puffCount
        val px = center.x + (rx * cos(angle)).toFloat()
        val py = center.y + (ry * sin(angle)).toFloat()
        drawCircle(outline, radius = puffRadius + stroke.width * 0.5f, center = Offset(px, py))
    }
    drawOval(
        color = fill,
        topLeft = Offset(center.x - rx, center.y - ry),
        size = Size(rx * 2f, ry * 2f)
    )
    for (i in 0 until puffCount) {
        val angle = (2.0 * PI * i) / puffCount
        val px = center.x + (rx * cos(angle)).toFloat()
        val py = center.y + (ry * sin(angle)).toFloat()
        drawCircle(fill, radius = puffRadius, center = Offset(px, py))
    }
}
