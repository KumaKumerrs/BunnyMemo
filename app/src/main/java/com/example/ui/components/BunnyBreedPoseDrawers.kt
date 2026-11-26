package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.SkyBlue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawRestingBreedBunny(
    breed: BunnyBreed,
    p: BunnyBreedPalette,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val isPointed = breed.markingStyle == BunnyMarkingStyle.POINTED_EXTREMITIES
    drawOval(SkyBlue.copy(alpha = 0.42f), Offset(w * 0.12f, h * 0.78f), Size(w * 0.76f, h * 0.14f))

    val tailColor = if (isPointed) p.secondaryPatch else p.primaryCoat
    drawCircle(tailColor, radius = w * 0.09f, center = Offset(w * 0.20f, h * 0.68f))
    drawCircle(p.outlineInk, radius = w * 0.09f, center = Offset(w * 0.20f, h * 0.68f), style = stroke)

    if (breed.markingStyle == BunnyMarkingStyle.ANGORA_CLOUD) {
        drawScallopedFluffRing(
            center = Offset(w * 0.49f, h * 0.65f),
            rx = w * 0.29f,
            ry = h * 0.19f,
            puffCount = 11,
            puffRadius = w * 0.068f,
            fill = p.primaryCoat,
            outline = p.outlineInk,
            stroke = stroke
        )
    } else {
        drawOval(p.primaryCoat, Offset(w * 0.20f, h * 0.46f), Size(w * 0.58f, h * 0.38f))
        drawBodyMarkings(breed, p, w * 0.20f, h * 0.46f, w * 0.58f, h * 0.38f, w, h)
        drawOval(p.outlineInk, Offset(w * 0.20f, h * 0.46f), Size(w * 0.58f, h * 0.38f), style = stroke)
    }

    val earColor1 = if (isPointed || breed.markingStyle == BunnyMarkingStyle.DUTCH_SADDLE ||
        breed.markingStyle == BunnyMarkingStyle.ENGLISH_SPOTTED ||
        breed.markingStyle == BunnyMarkingStyle.HARLEQUIN_SPLIT
    ) p.secondaryPatch else p.primaryCoat
    val earColor2 = if (breed.markingStyle == BunnyMarkingStyle.HARLEQUIN_SPLIT) p.primaryCoat else earColor1

    if (breed.earStyle != BunnyEarStyle.LOP) {
        val earLen = when (breed.earStyle) {
            BunnyEarStyle.SHORT_DWARF -> h * 0.20f
            BunnyEarStyle.TALL_BROAD -> h * 0.35f
            else -> h * 0.30f
        }
        rotate(-46f, pivot = Offset(w * 0.57f, h * 0.42f)) {
            drawOval(earColor1, Offset(w * 0.51f, h * 0.44f - earLen), Size(w * 0.12f, earLen))
            drawOval(p.outlineInk, Offset(w * 0.51f, h * 0.44f - earLen), Size(w * 0.12f, earLen), style = stroke)
            drawOval(p.innerEar.copy(alpha = 0.78f), Offset(w * 0.54f, h * 0.46f - earLen * 0.84f), Size(w * 0.06f, earLen * 0.6f))
        }
        rotate(-28f, pivot = Offset(w * 0.64f, h * 0.41f)) {
            drawOval(earColor2, Offset(w * 0.58f, h * 0.43f - earLen), Size(w * 0.12f, earLen))
            drawOval(p.outlineInk, Offset(w * 0.58f, h * 0.43f - earLen), Size(w * 0.12f, earLen), style = stroke)
            drawOval(p.innerEar.copy(alpha = 0.78f), Offset(w * 0.61f, h * 0.45f - earLen * 0.84f), Size(w * 0.06f, earLen * 0.6f))
        }
    }

    if (breed.markingStyle == BunnyMarkingStyle.LION_MANE) {
        drawScallopedFluffRing(
            center = Offset(w * 0.64f, h * 0.55f),
            rx = w * 0.21f,
            ry = h * 0.18f,
            puffCount = 10,
            puffRadius = w * 0.056f,
            fill = p.secondaryPatch,
            outline = p.outlineInk,
            stroke = stroke
        )
    }

    drawOval(p.primaryCoat, Offset(w * 0.46f, h * 0.38f), Size(w * 0.38f, h * 0.34f))
    drawHeadMarkings(breed, p, w * 0.46f, h * 0.38f, w * 0.38f, h * 0.34f, w, h)
    drawOval(p.outlineInk, Offset(w * 0.46f, h * 0.38f), Size(w * 0.38f, h * 0.34f), style = stroke)

    if (breed.earStyle == BunnyEarStyle.LOP) {
        rotate(16f, pivot = Offset(w * 0.56f, h * 0.47f)) {
            drawOval(p.secondaryPatch, Offset(w * 0.48f, h * 0.45f), Size(w * 0.13f, h * 0.27f))
            drawOval(p.outlineInk, Offset(w * 0.48f, h * 0.45f), Size(w * 0.13f, h * 0.27f), style = stroke)
        }
    }

    val closedEye = Path().apply {
        moveTo(w * 0.62f, h * 0.53f)
        quadraticTo(w * 0.67f, h * 0.57f, w * 0.72f, h * 0.53f)
    }
    if (breed.markingStyle == BunnyMarkingStyle.HOTOT_EYELINER) {
        drawCircle(p.secondaryPatch, radius = w * 0.045f, center = Offset(w * 0.67f, h * 0.54f))
    }
    drawPath(closedEye, p.outlineInk, style = stroke)
    drawCircle(p.blushCheek, radius = w * 0.04f, center = Offset(w * 0.60f, h * 0.58f))
    drawCircle(p.noseColor, radius = w * 0.019f, center = Offset(w * 0.78f, h * 0.56f))

    drawInlineForgetMeNot(Offset(w * 0.84f, h * 0.76f), w * 0.11f)
}

internal fun DrawScope.drawEnvelopeBreedBunny(
    breed: BunnyBreed,
    p: BunnyBreedPalette,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val isPointed = breed.markingStyle == BunnyMarkingStyle.POINTED_EXTREMITIES
    val earLeft = if (isPointed || breed.markingStyle == BunnyMarkingStyle.DUTCH_SADDLE ||
        breed.markingStyle == BunnyMarkingStyle.ENGLISH_SPOTTED ||
        breed.markingStyle == BunnyMarkingStyle.HARLEQUIN_SPLIT
    ) p.secondaryPatch else p.primaryCoat
    val earRight = if (breed.markingStyle == BunnyMarkingStyle.HARLEQUIN_SPLIT) p.primaryCoat else earLeft

    if (breed.earStyle != BunnyEarStyle.LOP) {
        drawCrownEars(
            earStyle = breed.earStyle,
            leftColor = earLeft,
            rightColor = earRight,
            innerColor = p.innerEar,
            outlineColor = p.outlineInk,
            headCenterX = w * 0.40f,
            crownY = h * 0.31f,
            w = w,
            h = h,
            stroke = stroke
        )
    }

    drawOval(p.primaryCoat, Offset(w * 0.18f, h * 0.52f), Size(w * 0.42f, h * 0.36f))
    drawBodyMarkings(breed, p, w * 0.18f, h * 0.52f, w * 0.42f, h * 0.36f, w, h)
    drawOval(p.outlineInk, Offset(w * 0.18f, h * 0.52f), Size(w * 0.42f, h * 0.36f), style = stroke)

    if (breed.markingStyle == BunnyMarkingStyle.LION_MANE) {
        drawScallopedFluffRing(
            center = Offset(w * 0.40f, h * 0.44f),
            rx = w * 0.23f,
            ry = h * 0.18f,
            puffCount = 10,
            puffRadius = w * 0.056f,
            fill = p.secondaryPatch,
            outline = p.outlineInk,
            stroke = stroke
        )
    }

    drawOval(p.primaryCoat, Offset(w * 0.19f, h * 0.27f), Size(w * 0.42f, h * 0.34f))
    drawHeadMarkings(breed, p, w * 0.19f, h * 0.27f, w * 0.42f, h * 0.34f, w, h)
    drawOval(p.outlineInk, Offset(w * 0.19f, h * 0.27f), Size(w * 0.42f, h * 0.34f), style = stroke)

    if (breed.earStyle == BunnyEarStyle.LOP) {
        rotate(12f, pivot = Offset(w * 0.21f, h * 0.39f)) {
            drawOval(p.secondaryPatch, Offset(w * 0.12f, h * 0.35f), Size(w * 0.12f, h * 0.28f))
            drawOval(p.outlineInk, Offset(w * 0.12f, h * 0.35f), Size(w * 0.12f, h * 0.28f), style = stroke)
        }
    }

    if (breed.markingStyle == BunnyMarkingStyle.HOTOT_EYELINER) {
        drawCircle(p.secondaryPatch, radius = w * 0.042f, center = Offset(w * 0.34f, h * 0.42f))
        drawCircle(p.secondaryPatch, radius = w * 0.042f, center = Offset(w * 0.48f, h * 0.42f))
    }
    drawCircle(p.eyeColor, radius = w * 0.025f, center = Offset(w * 0.34f, h * 0.42f))
    drawCircle(p.eyeColor, radius = w * 0.025f, center = Offset(w * 0.48f, h * 0.42f))
    drawCircle(p.blushCheek, radius = w * 0.036f, center = Offset(w * 0.28f, h * 0.47f))

    val envLeft = w * 0.46f
    val envTop = h * 0.50f
    val envW = w * 0.42f
    val envH = h * 0.30f
    drawRoundRect(
        color = Color(0xFFFCFEFF),
        topLeft = Offset(envLeft, envTop),
        size = Size(envW, envH),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
    )
    drawRoundRect(
        color = p.outlineInk,
        topLeft = Offset(envLeft, envTop),
        size = Size(envW, envH),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
        style = stroke
    )
    val flap = Path().apply {
        moveTo(envLeft + w * 0.02f, envTop + h * 0.02f)
        lineTo(envLeft + envW / 2f, envTop + envH * 0.56f)
        lineTo(envLeft + envW - w * 0.02f, envTop + h * 0.02f)
    }
    drawPath(flap, p.outlineInk, style = stroke)
    drawCircle(PollenYellow, radius = w * 0.055f, center = Offset(envLeft + envW / 2f, envTop + envH * 0.58f))
    drawCircle(ForgetMeNotBlue, radius = w * 0.025f, center = Offset(envLeft + envW / 2f, envTop + envH * 0.58f))
}

internal fun DrawScope.drawHoppingBreedBunny(
    breed: BunnyBreed,
    isDarkTheme: Boolean,
    w: Float,
    h: Float,
    stroke: Stroke,
    jumpArc: Float,
    earBob: Float,
    smilePath: Path
) {
    val p = breed.palette(isDarkTheme)
    val isPointed = breed.markingStyle == BunnyMarkingStyle.POINTED_EXTREMITIES
    val tailColor = if (isPointed) p.secondaryPatch else p.primaryCoat
    val pawColor = if (isPointed) p.secondaryPatch else p.primaryCoat

    drawCircle(tailColor, radius = w * 0.075f, center = Offset(w * 0.28f, h * 0.66f))
    drawCircle(p.outlineInk, radius = w * 0.075f, center = Offset(w * 0.28f, h * 0.66f), style = stroke)

    rotate(jumpArc * 22f, pivot = Offset(w * 0.36f, h * 0.72f)) {
        drawOval(pawColor, Offset(w * 0.28f, h * 0.69f), Size(w * 0.16f, h * 0.09f))
        drawOval(p.outlineInk, Offset(w * 0.28f, h * 0.69f), Size(w * 0.16f, h * 0.09f), style = stroke)
    }

    drawOval(p.primaryCoat, Offset(w * 0.30f, h * 0.48f), Size(w * 0.38f, h * 0.28f))
    drawBodyMarkings(breed, p, w * 0.30f, h * 0.48f, w * 0.38f, h * 0.28f, w, h)
    drawOval(p.outlineInk, Offset(w * 0.30f, h * 0.48f), Size(w * 0.38f, h * 0.28f), style = stroke)

    rotate(-jumpArc * 18f, pivot = Offset(w * 0.60f, h * 0.68f)) {
        drawOval(pawColor, Offset(w * 0.55f, h * 0.67f), Size(w * 0.14f, h * 0.085f))
        drawOval(p.outlineInk, Offset(w * 0.55f, h * 0.67f), Size(w * 0.14f, h * 0.085f), style = stroke)
    }

    val earBackColor = if (isPointed || breed.markingStyle == BunnyMarkingStyle.DUTCH_SADDLE ||
        breed.markingStyle == BunnyMarkingStyle.ENGLISH_SPOTTED ||
        breed.markingStyle == BunnyMarkingStyle.HARLEQUIN_SPLIT
    ) p.secondaryPatch else p.primaryCoat
    val earFrontColor = if (breed.markingStyle == BunnyMarkingStyle.HARLEQUIN_SPLIT) p.primaryCoat else earBackColor

    if (breed.earStyle != BunnyEarStyle.LOP) {
        val earLen = when (breed.earStyle) {
            BunnyEarStyle.SHORT_DWARF -> h * 0.18f
            BunnyEarStyle.TALL_BROAD -> h * 0.31f
            else -> h * 0.26f
        }
        rotate(-20f - earBob, pivot = Offset(w * 0.535f, h * 0.365f)) {
            drawOval(earBackColor, Offset(w * 0.48f, h * 0.39f - earLen), Size(w * 0.11f, earLen))
            drawOval(p.outlineInk, Offset(w * 0.48f, h * 0.39f - earLen), Size(w * 0.11f, earLen), style = stroke)
            drawOval(p.innerEar.copy(alpha = 0.85f), Offset(w * 0.51f, h * 0.41f - earLen * 0.85f), Size(w * 0.05f, earLen * 0.6f))
        }
        rotate(-7f - earBob * 0.78f, pivot = Offset(w * 0.605f, h * 0.36f)) {
            drawOval(earFrontColor, Offset(w * 0.55f, h * 0.38f - earLen), Size(w * 0.11f, earLen))
            drawOval(p.outlineInk, Offset(w * 0.55f, h * 0.38f - earLen), Size(w * 0.11f, earLen), style = stroke)
            drawOval(p.innerEar.copy(alpha = 0.85f), Offset(w * 0.58f, h * 0.40f - earLen * 0.85f), Size(w * 0.05f, earLen * 0.6f))
        }
    }

    if (breed.markingStyle == BunnyMarkingStyle.LION_MANE) {
        drawScallopedFluffRing(
            center = Offset(w * 0.59f, h * 0.46f),
            rx = w * 0.18f,
            ry = h * 0.15f,
            puffCount = 9,
            puffRadius = w * 0.048f,
            fill = p.secondaryPatch,
            outline = p.outlineInk,
            stroke = stroke
        )
    }

    drawOval(p.primaryCoat, Offset(w * 0.43f, h * 0.33f), Size(w * 0.32f, h * 0.27f))
    drawHeadMarkings(breed, p, w * 0.43f, h * 0.33f, w * 0.32f, h * 0.27f, w, h)
    drawOval(p.outlineInk, Offset(w * 0.43f, h * 0.33f), Size(w * 0.32f, h * 0.27f), style = stroke)

    if (breed.earStyle == BunnyEarStyle.LOP) {
        rotate(16f + earBob * 0.4f, pivot = Offset(w * 0.51f, h * 0.41f)) {
            drawOval(p.secondaryPatch, Offset(w * 0.45f, h * 0.39f), Size(w * 0.11f, h * 0.23f))
            drawOval(p.outlineInk, Offset(w * 0.45f, h * 0.39f), Size(w * 0.11f, h * 0.23f), style = stroke)
        }
    }

    if (breed.markingStyle == BunnyMarkingStyle.HOTOT_EYELINER) {
        drawCircle(p.secondaryPatch, radius = w * 0.036f, center = Offset(w * 0.62f, h * 0.44f))
    }
    drawCircle(p.eyeColor, radius = w * 0.022f, center = Offset(w * 0.62f, h * 0.44f))
    drawCircle(p.blushCheek, radius = w * 0.036f, center = Offset(w * 0.56f, h * 0.50f))
    drawCircle(p.noseColor, radius = w * 0.017f, center = Offset(w * 0.72f, h * 0.48f))
    drawPath(smilePath, p.outlineInk, style = stroke)
}

internal fun DrawScope.drawInlineForgetMeNot(center: Offset, radius: Float) {
    val petalDist = radius * 0.48f
    val petalRadius = radius * 0.38f
    for (i in 0 until 5) {
        val angleRad = ((-90.0 + i * 72.0) * PI / 180.0)
        val px = center.x + (petalDist * cos(angleRad)).toFloat()
        val py = center.y + (petalDist * sin(angleRad)).toFloat()
        drawCircle(ForgetMeNotBlue, radius = petalRadius, center = Offset(px, py))
    }
    drawCircle(Color(0xFFFCFEFF), radius = radius * 0.30f, center = center)
    drawCircle(PollenYellow, radius = radius * 0.22f, center = center)
}
