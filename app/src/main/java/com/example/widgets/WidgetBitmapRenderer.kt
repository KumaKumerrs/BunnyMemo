package com.example.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.data.CalendarDate
import com.example.data.PlannerDateUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders anti-aliased Forget-Me-Not visual components (Month Calendar Grid and 7-Day Habit Bloom Strip)
 * into compact bitmaps suitable for Android AppWidget RemoteViews.
 */
object WidgetBitmapRenderer {

    private val COLOR_DEEP_BLUE = Color.parseColor("#3B82DC")
    private val COLOR_FORGET_ME_NOT = Color.parseColor("#7CB6F9")
    private val COLOR_SKY_FILL = Color.parseColor("#DDEEFF")
    private val COLOR_POLLEN = Color.parseColor("#F7D56E")
    private val COLOR_MIDNIGHT = Color.parseColor("#2B4360")
    private val COLOR_MUTED = Color.parseColor("#6B85A3")
    private val COLOR_PURE_WHITE = Color.parseColor("#FCFEFF")
    private val COLOR_BUD_OUTLINE = Color.parseColor("#C7DFF8")

    fun renderMonthCalendarBitmap(
        today: CalendarDate,
        scheduledDates: Set<CalendarDate>,
        habitBloomDates: Set<CalendarDate>,
        widthPx: Int = 720,
        heightPx: Int = 460
    ): Bitmap {
        val monthStart = CalendarDate(today.year, today.month, 1)
        val cells = PlannerDateUtils.buildMonthGrid(monthStart)
        val weeks = cells.chunked(7)
        val rowCount = weeks.size.coerceAtLeast(5)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val headerHeight = heightPx * 0.14f
        val gridHeight = heightPx - headerHeight - 8f
        val colWidth = widthPx / 7f
        val rowHeight = gridHeight / rowCount

        val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = (colWidth * 0.24f).coerceIn(18f, 26f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val dayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (min(colWidth, rowHeight) * 0.36f).coerceIn(20f, 30f)
            textAlign = Paint.Align.CENTER
        }

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Weekday headers
        val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")
        val headerBaseline = headerHeight * 0.68f
        weekdays.forEachIndexed { col, label ->
            canvas.drawText(label, colWidth * col + colWidth / 2f, headerBaseline, weekdayPaint)
        }

        // Month day cells
        weeks.forEachIndexed { rowIdx, week ->
            val top = headerHeight + rowIdx * rowHeight
            val centerY = top + rowHeight * 0.46f

            week.forEachIndexed { colIdx, day ->
                if (day != null) {
                    val centerX = colIdx * colWidth + colWidth / 2f
                    val isToday = day == today
                    val isScheduled = day in scheduledDates
                    val isBloomed = day in habitBloomDates

                    val pillRadius = min(colWidth, rowHeight) * 0.36f
                    if (isToday) {
                        circlePaint.color = COLOR_DEEP_BLUE
                        canvas.drawCircle(centerX, centerY, pillRadius, circlePaint)
                    } else if (isScheduled) {
                        circlePaint.color = COLOR_SKY_FILL
                        canvas.drawCircle(centerX, centerY, pillRadius * 0.92f, circlePaint)
                    }

                    dayTextPaint.color = if (isToday) COLOR_PURE_WHITE else COLOR_MIDNIGHT
                    dayTextPaint.typeface = if (isToday || isScheduled) {
                        Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    } else {
                        Typeface.DEFAULT
                    }
                    val textOffset = (dayTextPaint.descent() + dayTextPaint.ascent()) / 2f
                    canvas.drawText(day.day.toString(), centerX, centerY - textOffset, dayTextPaint)

                    // Bottom indicator: mini forget-me-not flower for completed habit day, or dot for scheduled day
                    val indicatorY = top + rowHeight * 0.87f
                    if (isBloomed) {
                        drawMiniForgetMeNot(
                            canvas = canvas,
                            cx = centerX,
                            cy = indicatorY,
                            radius = (min(colWidth, rowHeight) * 0.12f).coerceIn(6f, 10f),
                            bloomed = true
                        )
                    } else if (isScheduled) {
                        circlePaint.color = if (isToday) COLOR_POLLEN else COLOR_DEEP_BLUE
                        canvas.drawCircle(centerX, indicatorY, 4.2f, circlePaint)
                    }
                }
            }
        }

        return bitmap
    }

    fun renderWeeklyHabitStripBitmap(
        today: CalendarDate,
        habitBloomDates: Set<CalendarDate>,
        widthPx: Int = 700,
        heightPx: Int = 124
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colWidth = widthPx / 7f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }

        for (i in 0 until 7) {
            val day = today.plusDays(i - 6)
            val isToday = day == today
            val isBloomed = day in habitBloomDates
            val cx = i * colWidth + colWidth / 2f

            if (isToday) {
                bgPaint.color = COLOR_SKY_FILL
                val rect = RectF(
                    cx - colWidth * 0.42f,
                    6f,
                    cx + colWidth * 0.42f,
                    heightPx - 6f
                )
                canvas.drawRoundRect(rect, 22f, 22f, bgPaint)
            }

            drawMiniForgetMeNot(
                canvas = canvas,
                cx = cx,
                cy = heightPx * 0.39f,
                radius = 22f,
                bloomed = isBloomed
            )

            labelPaint.color = if (isToday) COLOR_DEEP_BLUE else COLOR_MUTED
            labelPaint.typeface = if (isToday) {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                Typeface.DEFAULT
            }
            val label = PlannerDateUtils.weekdayShortLabel(day.dayOfWeekIndex()).take(3)
            canvas.drawText(label, cx, heightPx * 0.85f, labelPaint)
        }

        return bitmap
    }

    /**
     * Renders ONLY the current 7-day week (Sunday..Saturday) as a clean, spacious single-row strip
     * with weekday headers, date pills, today highlight, scheduled item dots, and forget-me-not blooms.
     */
    fun renderCurrentWeekStripBitmap(
        today: CalendarDate,
        weekDates: List<CalendarDate>,
        scheduledDates: Set<CalendarDate>,
        habitBloomDates: Set<CalendarDate>,
        itemCountByDate: Map<CalendarDate, Int>,
        widthPx: Int = 760,
        heightPx: Int = 216
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (weekDates.isEmpty()) return bitmap

        val colWidth = widthPx / 7f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (colWidth * 0.22f).coerceIn(19f, 25f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (colWidth * 0.34f).coerceIn(28f, 38f)
            textAlign = Paint.Align.CENTER
        }

        weekDates.take(7).forEachIndexed { idx, day ->
            val cx = idx * colWidth + colWidth / 2f
            val isToday = day == today
            val isScheduled = day in scheduledDates
            val isBloomed = day in habitBloomDates
            val count = itemCountByDate[day] ?: 0

            val cardMarginX = colWidth * 0.08f
            val cardRect = RectF(
                idx * colWidth + cardMarginX,
                8f,
                (idx + 1) * colWidth - cardMarginX,
                heightPx - 8f
            )

            // Column card background
            bgPaint.style = Paint.Style.FILL
            bgPaint.color = when {
                isToday -> COLOR_DEEP_BLUE
                isScheduled -> COLOR_SKY_FILL
                else -> Color.parseColor("#F5FAFF")
            }
            canvas.drawRoundRect(cardRect, 24f, 24f, bgPaint)

            if (!isToday) {
                bgPaint.style = Paint.Style.STROKE
                bgPaint.strokeWidth = 2f
                bgPaint.color = if (isScheduled) COLOR_FORGET_ME_NOT else COLOR_BUD_OUTLINE
                canvas.drawRoundRect(cardRect, 24f, 24f, bgPaint)
                bgPaint.style = Paint.Style.FILL
            }

            // Weekday label (Sun..Sat)
            weekdayPaint.color = if (isToday) Color.parseColor("#DCECFD") else COLOR_MUTED
            val dayLabel = PlannerDateUtils.weekdayShortLabel(day.dayOfWeekIndex()).take(3).uppercase()
            canvas.drawText(dayLabel, cx, heightPx * 0.25f, weekdayPaint)

            // Day number
            dayNumPaint.color = if (isToday) COLOR_PURE_WHITE else COLOR_MIDNIGHT
            dayNumPaint.typeface = Typeface.create(
                Typeface.DEFAULT,
                if (isToday || isScheduled) Typeface.BOLD else Typeface.NORMAL
            )
            val numOffset = (dayNumPaint.descent() + dayNumPaint.ascent()) / 2f
            canvas.drawText(day.day.toString(), cx, heightPx * 0.54f - numOffset, dayNumPaint)

            // Bottom indicator: forget-me-not bloom or scheduled dots
            val indicatorY = heightPx * 0.80f
            if (isBloomed) {
                drawMiniForgetMeNot(
                    canvas = canvas,
                    cx = cx,
                    cy = indicatorY,
                    radius = 15f,
                    bloomed = true
                )
            } else if (count > 0) {
                val dotCount = count.coerceAtMost(3)
                val dotRadius = 5f
                val spacing = 14f
                val startX = cx - ((dotCount - 1) * spacing) / 2f
                bgPaint.color = if (isToday) COLOR_POLLEN else COLOR_DEEP_BLUE
                for (d in 0 until dotCount) {
                    canvas.drawCircle(startX + d * spacing, indicatorY, dotRadius, bgPaint)
                }
            }
        }

        return bitmap
    }

    private fun drawMiniForgetMeNot(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        bloomed: Boolean
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val petalDist = radius * 0.50f
        val petalRadius = radius * 0.38f

        if (bloomed) {
            for (i in 0 until 5) {
                val rad = ((-90.0 + i * 72.0) * PI / 180.0)
                val px = cx + (petalDist * cos(rad)).toFloat()
                val py = cy + (petalDist * sin(rad)).toFloat()
                paint.color = if (i % 2 == 0) COLOR_DEEP_BLUE else COLOR_FORGET_ME_NOT
                canvas.drawCircle(px, py, petalRadius, paint)
            }
            paint.color = COLOR_PURE_WHITE
            canvas.drawCircle(cx, cy, radius * 0.32f, paint)
            paint.color = COLOR_POLLEN
            canvas.drawCircle(cx, cy, radius * 0.21f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (radius * 0.13f).coerceAtLeast(2f)
            paint.color = COLOR_BUD_OUTLINE
            canvas.drawCircle(cx, cy, radius * 0.52f, paint)
            paint.style = Paint.Style.FILL
            paint.color = COLOR_SKY_FILL
            canvas.drawCircle(cx, cy, radius * 0.20f, paint)
        }
    }

    /**
     * Renders the Resizable Interactive Bunny Family widget illustration:
     * - Scales the Big Monthly Breed Bunny dynamically to fill the widget's resized dimensions
     * - Draws 3..5 adorable widget-exclusive baby bunnies peeking & hopping behind the big bunny
     * - Reflects live expression states: PATTED (squished + floating hearts), CHEEK_PINCHED (stretched cheeks + squeak sparks),
     *   HAPPY (sparkles + flower crown on all-habits-done), SAD (droopy ears + teardrop on missed task), or NEUTRAL.
     */
    fun renderInteractiveBunnyWidgetBitmap(
        month: Int,
        expression: com.example.ui.components.BunnyCompanionExpression,
        pinchSide: Int = 0, // -1 left, +1 right, 0 both
        babyBunnyCount: Int = 3,
        allHabitsDoneToday: Boolean = false,
        widthPx: Int = 640,
        heightPx: Int = 460
    ): Bitmap {
        val safeW = widthPx.coerceIn(280, 1400)
        val safeH = heightPx.coerceIn(200, 1400)
        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val breed = com.example.ui.components.BunnyBreed.forMonth(month)
        val composePalette = breed.palette(isDarkTheme = false)
        fun androidx.compose.ui.graphics.Color.toAndroidColor(): Int =
            Color.argb(
                (alpha * 255).toInt().coerceIn(0, 255),
                (red * 255).toInt().coerceIn(0, 255),
                (green * 255).toInt().coerceIn(0, 255),
                (blue * 255).toInt().coerceIn(0, 255)
            )

        val primaryColor = composePalette.primaryCoat.toAndroidColor()
        val secondaryColor = composePalette.secondaryPatch.toAndroidColor()
        val bellyColor = composePalette.bellyCoat.toAndroidColor()
        val innerEarColor = composePalette.innerEar.toAndroidColor()
        val blushColor = composePalette.blushCheek.toAndroidColor()
        val eyeColor = composePalette.eyeColor.toAndroidColor()
        val outlineColor = composePalette.outlineInk.toAndroidColor()
        val noseColor = composePalette.noseColor.toAndroidColor()

        val w = safeW.toFloat()
        val h = safeH.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Cozy sky-meadow backdrop hill
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E6F3FF")
        canvas.drawOval(RectF(-w * 0.15f, h * 0.64f, w * 1.15f, h * 1.24f), paint)
        paint.color = Color.parseColor("#D6ECFF")
        canvas.drawOval(RectF(w * 0.05f, h * 0.72f, w * 0.95f, h * 1.15f), paint)

        // Scatter tiny forget-me-not flowers across the meadow hill
        drawMiniForgetMeNot(canvas, w * 0.11f, h * 0.84f, min(w, h) * 0.036f, true)
        drawMiniForgetMeNot(canvas, w * 0.89f, h * 0.82f, min(w, h) * 0.038f, true)
        if (allHabitsDoneToday) {
            drawMiniForgetMeNot(canvas, w * 0.22f, h * 0.90f, min(w, h) * 0.032f, true)
            drawMiniForgetMeNot(canvas, w * 0.78f, h * 0.89f, min(w, h) * 0.032f, true)
        }

        val unit = min(w * 0.84f, h * 0.94f)
        val centerX = w * 0.50f
        val baseY = h * 0.88f

        // ------------------------------------------------------------------------
        // 1. WIDGET-EXCLUSIVE CUTE BABY BUNNIES PEEKING BEHIND THE BIG BUNNY
        // ------------------------------------------------------------------------
        data class BabyPlacement(
            val offsetX: Float,
            val offsetY: Float,
            val scale: Float,
            val tiltDeg: Float,
            val coatColor: Int,
            val patchColor: Int,
            val isLop: Boolean
        )

        val babySlots = listOf(
            // Left baby kit peeking out from behind big bunny's left shoulder
            BabyPlacement(
                offsetX = -unit * 0.36f,
                offsetY = -unit * 0.05f,
                scale = 0.36f,
                tiltDeg = -10f,
                coatColor = Color.parseColor("#FFFDF9"),
                patchColor = secondaryColor,
                isLop = false
            ),
            // Right baby kit peeking out from behind big bunny's right shoulder
            BabyPlacement(
                offsetX = unit * 0.36f,
                offsetY = -unit * 0.04f,
                scale = 0.35f,
                tiltDeg = 11f,
                coatColor = Color.parseColor("#FFF6E8"),
                patchColor = primaryColor,
                isLop = true
            ),
            // Top-left curious tiny baby kit peeking over the shoulder
            BabyPlacement(
                offsetX = -unit * 0.24f,
                offsetY = -unit * 0.34f,
                scale = 0.28f,
                tiltDeg = -7f,
                coatColor = primaryColor,
                patchColor = secondaryColor,
                isLop = false
            ),
            // Top-right playful tiny baby kit peeking behind right ear/shoulder (shown when resized larger or streak grows)
            BabyPlacement(
                offsetX = unit * 0.24f,
                offsetY = -unit * 0.33f,
                scale = 0.28f,
                tiltDeg = 8f,
                coatColor = Color.parseColor("#F5FAFF"),
                patchColor = secondaryColor,
                isLop = false
            ),
            // Far-left tiny meadow sprout kit
            BabyPlacement(
                offsetX = -unit * 0.49f,
                offsetY = unit * 0.02f,
                scale = 0.26f,
                tiltDeg = -13f,
                coatColor = Color.parseColor("#FFF2DE"),
                patchColor = secondaryColor,
                isLop = true
            )
        )

        val activeBabies = babySlots.take(babyBunnyCount.coerceIn(3, babySlots.size))
        activeBabies.forEachIndexed { idx, baby ->
            drawWidgetBabyBunnyKit(
                canvas = canvas,
                cx = centerX + baby.offsetX,
                bottomY = baseY + baby.offsetY,
                kitSize = unit * baby.scale,
                tiltDeg = baby.tiltDeg,
                coatColor = baby.coatColor,
                patchColor = baby.patchColor,
                innerEarColor = innerEarColor,
                blushColor = blushColor,
                outlineColor = outlineColor,
                isLop = baby.isLop,
                isHappy = expression == com.example.ui.components.BunnyCompanionExpression.HAPPY ||
                    expression == com.example.ui.components.BunnyCompanionExpression.PATTED ||
                    allHabitsDoneToday,
                kitIndex = idx
            )
        }

        // ------------------------------------------------------------------------
        // 2. FOREGROUND RESIZABLE BIG MONTHLY BREED BUNNY
        // ------------------------------------------------------------------------
        val isSquished = expression == com.example.ui.components.BunnyCompanionExpression.PATTED
        val isPinched = expression == com.example.ui.components.BunnyCompanionExpression.CHEEK_PINCHED
        val isSad = expression == com.example.ui.components.BunnyCompanionExpression.SAD
        val isHappy = expression == com.example.ui.components.BunnyCompanionExpression.HAPPY || allHabitsDoneToday

        val scaleX = when {
            isSquished -> 1.09f
            isPinched -> 1.06f
            else -> 1.0f
        }
        val scaleY = when {
            isSquished -> 0.90f
            isPinched -> 0.95f
            else -> 1.0f
        }

        canvas.save()
        canvas.scale(scaleX, scaleY, centerX, baseY)

        val strokeW = (unit * 0.032f).coerceIn(2.5f, 8f)
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = outlineColor
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Soft shadow pad under Big Bunny
        fillPaint.color = Color.argb(36, 43, 67, 96)
        canvas.drawOval(
            RectF(
                centerX - unit * 0.30f,
                baseY - unit * 0.06f,
                centerX + unit * 0.30f,
                baseY + unit * 0.04f
            ),
            fillPaint
        )

        val bodyRect = RectF(
            centerX - unit * 0.25f,
            baseY - unit * 0.44f,
            centerX + unit * 0.25f,
            baseY - unit * 0.01f
        )
        val headCenterY = baseY - unit * 0.54f
        val headRx = unit * 0.26f
        val headRy = unit * 0.22f

        // Upright or special ears behind head
        val earDroopDeg = when {
            isSad -> 22f
            isSquished -> 12f
            isHappy -> -6f
            else -> 0f
        }
        if (breed.earStyle != com.example.ui.components.BunnyEarStyle.LOP) {
            val earHeight = when (breed.earStyle) {
                com.example.ui.components.BunnyEarStyle.SHORT_DWARF -> unit * 0.21f
                com.example.ui.components.BunnyEarStyle.TALL_BROAD -> unit * 0.35f
                else -> unit * 0.29f
            }
            val earWidth = unit * 0.115f
            val leftEarX = centerX - unit * 0.11f
            val rightEarX = centerX + unit * 0.11f
            val earBaseY = headCenterY - headRy * 0.55f

            val earFillColor = if (
                breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.POINTED_EXTREMITIES ||
                breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.DUTCH_SADDLE ||
                breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.HARLEQUIN_SPLIT
            ) secondaryColor else primaryColor

            // Left ear
            canvas.save()
            canvas.rotate(-10f - earDroopDeg, leftEarX, earBaseY)
            val leftEarRect = RectF(leftEarX - earWidth / 2f, earBaseY - earHeight, leftEarX + earWidth / 2f, earBaseY + 8f)
            fillPaint.color = earFillColor
            canvas.drawRoundRect(leftEarRect, earWidth, earWidth, fillPaint)
            fillPaint.color = innerEarColor
            canvas.drawRoundRect(
                RectF(leftEarX - earWidth * 0.28f, earBaseY - earHeight * 0.84f, leftEarX + earWidth * 0.28f, earBaseY - 4f),
                earWidth,
                earWidth,
                fillPaint
            )
            canvas.drawRoundRect(leftEarRect, earWidth, earWidth, outlinePaint)
            canvas.restore()

            // Right ear
            canvas.save()
            canvas.rotate(10f + earDroopDeg, rightEarX, earBaseY)
            val rightEarRect = RectF(rightEarX - earWidth / 2f, earBaseY - earHeight, rightEarX + earWidth / 2f, earBaseY + 8f)
            fillPaint.color = if (breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.HARLEQUIN_SPLIT) primaryColor else earFillColor
            canvas.drawRoundRect(rightEarRect, earWidth, earWidth, fillPaint)
            fillPaint.color = innerEarColor
            canvas.drawRoundRect(
                RectF(rightEarX - earWidth * 0.28f, earBaseY - earHeight * 0.84f, rightEarX + earWidth * 0.28f, earBaseY - 4f),
                earWidth,
                earWidth,
                fillPaint
            )
            canvas.drawRoundRect(rightEarRect, earWidth, earWidth, outlinePaint)
            canvas.restore()
        }

        // Fluffy tail peeking out
        fillPaint.color = primaryColor
        canvas.drawCircle(centerX - unit * 0.24f, baseY - unit * 0.12f, unit * 0.068f, fillPaint)
        canvas.drawCircle(centerX - unit * 0.24f, baseY - unit * 0.12f, unit * 0.068f, outlinePaint)

        // Plump body
        fillPaint.color = if (breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.DUTCH_SADDLE) secondaryColor else primaryColor
        canvas.drawOval(bodyRect, fillPaint)

        // Soft belly patch
        fillPaint.color = bellyColor
        canvas.drawOval(
            RectF(
                centerX - unit * 0.15f,
                baseY - unit * 0.35f,
                centerX + unit * 0.15f,
                baseY - unit * 0.04f
            ),
            fillPaint
        )
        canvas.drawOval(bodyRect, outlinePaint)

        // Lionhead fluffy mane collar if April breed
        if (breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.LION_MANE) {
            fillPaint.color = secondaryColor
            val maneRadius = headRx * 1.16f
            canvas.drawCircle(centerX, headCenterY + unit * 0.03f, maneRadius, fillPaint)
            canvas.drawCircle(centerX, headCenterY + unit * 0.03f, maneRadius, outlinePaint)
        }

        // Cheek-pinch horizontal stretch
        val leftPullPx = if (isPinched && (pinchSide <= 0)) unit * 0.10f else 0f
        val rightPullPx = if (isPinched && (pinchSide >= 0)) unit * 0.10f else 0f

        val headRect = RectF(
            centerX - headRx - leftPullPx,
            headCenterY - headRy,
            centerX + headRx + rightPullPx,
            headCenterY + headRy
        )
        fillPaint.color = primaryColor
        canvas.drawOval(headRect, fillPaint)

        // Breed facial patches (Harlequin / Dutch / Spotted)
        if (breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.HARLEQUIN_SPLIT) {
            fillPaint.color = secondaryColor
            canvas.drawArc(headRect, 90f, 180f, true, fillPaint)
        } else if (breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.POINTED_EXTREMITIES) {
            fillPaint.color = secondaryColor
            canvas.drawOval(
                RectF(
                    centerX - unit * 0.075f,
                    headCenterY - unit * 0.01f,
                    centerX + unit * 0.075f,
                    headCenterY + unit * 0.09f
                ),
                fillPaint
            )
        }

        canvas.drawOval(headRect, outlinePaint)

        // Lop ears (if Holland Lop)
        if (breed.earStyle == com.example.ui.components.BunnyEarStyle.LOP) {
            val lopW = unit * 0.11f
            val lopH = unit * 0.27f
            fillPaint.color = secondaryColor
            val leftLop = RectF(centerX - headRx - lopW * 0.65f, headCenterY - headRy * 0.2f, centerX - headRx + lopW * 0.45f, headCenterY - headRy * 0.2f + lopH)
            val rightLop = RectF(centerX + headRx - lopW * 0.45f, headCenterY - headRy * 0.2f, centerX + headRx + lopW * 0.65f, headCenterY - headRy * 0.2f + lopH)
            canvas.drawRoundRect(leftLop, lopW, lopW, fillPaint)
            canvas.drawRoundRect(leftLop, lopW, lopW, outlinePaint)
            canvas.drawRoundRect(rightLop, lopW, lopW, fillPaint)
            canvas.drawRoundRect(rightLop, lopW, lopW, outlinePaint)
        }

        // Rosy chubby cheeks (extra big & bright when pinched!)
        val leftCheekX = centerX - headRx * 0.56f - leftPullPx * 0.72f
        val rightCheekX = centerX + headRx * 0.56f + rightPullPx * 0.72f
        val cheekY = headCenterY + headRy * 0.20f
        val cheekR = if (isPinched) unit * 0.068f else unit * 0.052f
        fillPaint.color = if (isPinched) Color.parseColor("#FF8DA8") else blushColor
        canvas.drawCircle(leftCheekX, cheekY, cheekR, fillPaint)
        canvas.drawCircle(rightCheekX, cheekY, cheekR, fillPaint)

        // Eyes (happy/patted arcs, pinched `> <` squeak eyes, sad droopy eyes + tear, or starry eyes)
        val leftEyeX = centerX - headRx * 0.38f
        val rightEyeX = centerX + headRx * 0.38f
        val eyeY = headCenterY - headRy * 0.08f
        val eyeR = unit * 0.032f

        when {
            isSquished || isHappy -> {
                // Happy ^ ^ curved eyes
                val arcSpan = unit * 0.048f
                canvas.drawArc(
                    RectF(leftEyeX - arcSpan, eyeY - arcSpan * 0.7f, leftEyeX + arcSpan, eyeY + arcSpan * 0.9f),
                    200f,
                    140f,
                    false,
                    outlinePaint
                )
                canvas.drawArc(
                    RectF(rightEyeX - arcSpan, eyeY - arcSpan * 0.7f, rightEyeX + arcSpan, eyeY + arcSpan * 0.9f),
                    200f,
                    140f,
                    false,
                    outlinePaint
                )
            }
            isPinched -> {
                // Playful > < squeak eyes
                val s = unit * 0.038f
                canvas.drawLine(leftEyeX - s, eyeY - s * 0.7f, leftEyeX + s * 0.6f, eyeY, outlinePaint)
                canvas.drawLine(leftEyeX + s * 0.6f, eyeY, leftEyeX - s, eyeY + s * 0.7f, outlinePaint)
                canvas.drawLine(rightEyeX + s, eyeY - s * 0.7f, rightEyeX - s * 0.6f, eyeY, outlinePaint)
                canvas.drawLine(rightEyeX - s * 0.6f, eyeY, rightEyeX + s, eyeY + s * 0.7f, outlinePaint)
            }
            else -> {
                if (breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.HOTOT_EYELINER ||
                    breed.markingStyle == com.example.ui.components.BunnyMarkingStyle.ENGLISH_SPOTTED
                ) {
                    fillPaint.color = secondaryColor
                    canvas.drawCircle(leftEyeX, eyeY, eyeR * 1.45f, fillPaint)
                    canvas.drawCircle(rightEyeX, eyeY, eyeR * 1.45f, fillPaint)
                }
                fillPaint.color = eyeColor
                canvas.drawCircle(leftEyeX, eyeY, eyeR, fillPaint)
                canvas.drawCircle(rightEyeX, eyeY, eyeR, fillPaint)
                fillPaint.color = COLOR_PURE_WHITE
                canvas.drawCircle(leftEyeX - eyeR * 0.3f, eyeY - eyeR * 0.3f, eyeR * 0.36f, fillPaint)
                canvas.drawCircle(rightEyeX - eyeR * 0.3f, eyeY - eyeR * 0.3f, eyeR * 0.36f, fillPaint)

                if (isSad) {
                    // Worried eyebrows + glistening teardrop
                    val b = unit * 0.042f
                    canvas.drawLine(leftEyeX - b, eyeY - eyeR * 1.3f, leftEyeX + b, eyeY - eyeR * 1.85f, outlinePaint)
                    canvas.drawLine(rightEyeX - b, eyeY - eyeR * 1.85f, rightEyeX + b, eyeY - eyeR * 1.3f, outlinePaint)
                    fillPaint.color = COLOR_FORGET_ME_NOT
                    canvas.drawCircle(leftEyeX - eyeR * 0.5f, eyeY + eyeR * 1.85f, unit * 0.022f, fillPaint)
                }
            }
        }

        // Cute nose & mouth
        val noseY = headCenterY + headRy * 0.14f
        fillPaint.color = noseColor
        canvas.drawOval(
            RectF(centerX - unit * 0.028f, noseY - unit * 0.018f, centerX + unit * 0.028f, noseY + unit * 0.018f),
            fillPaint
        )
        val mouthY = noseY + unit * 0.025f
        if (isSad) {
            canvas.drawArc(
                RectF(centerX - unit * 0.045f, mouthY, centerX + unit * 0.045f, mouthY + unit * 0.045f),
                200f,
                140f,
                false,
                outlinePaint
            )
        } else {
            // Cute "w" bunny smile
            val mw = unit * 0.042f
            canvas.drawArc(RectF(centerX - mw, mouthY - mw * 0.4f, centerX, mouthY + mw * 0.6f), 15f, 150f, false, outlinePaint)
            canvas.drawArc(RectF(centerX, mouthY - mw * 0.4f, centerX + mw, mouthY + mw * 0.6f), 15f, 150f, false, outlinePaint)
        }

        // Front paws
        val pawRadius = unit * 0.055f
        fillPaint.color = primaryColor
        canvas.drawCircle(centerX - unit * 0.12f, baseY - unit * 0.05f, pawRadius, fillPaint)
        canvas.drawCircle(centerX - unit * 0.12f, baseY - unit * 0.05f, pawRadius, outlinePaint)
        canvas.drawCircle(centerX + unit * 0.12f, baseY - unit * 0.05f, pawRadius, fillPaint)
        canvas.drawCircle(centerX + unit * 0.12f, baseY - unit * 0.05f, pawRadius, outlinePaint)

        // Forget-Me-Not flower crown when all habits are done today!
        if (allHabitsDoneToday) {
            val crownY = headCenterY - headRy * 0.88f
            val flowerR = unit * 0.048f
            drawMiniForgetMeNot(canvas, centerX - unit * 0.09f, crownY + unit * 0.01f, flowerR, true)
            drawMiniForgetMeNot(canvas, centerX + unit * 0.09f, crownY + unit * 0.01f, flowerR, true)
            drawMiniForgetMeNot(canvas, centerX, crownY - unit * 0.01f, flowerR * 1.15f, true)
        }

        canvas.restore()

        // ------------------------------------------------------------------------
        // 3. FLOATING EXPRESSION EFFECTS (HEARTS ON PAT, SPARKS ON PINCH, STARS ON HAPPY)
        // ------------------------------------------------------------------------
        when {
            isSquished -> {
                drawWidgetHeart(canvas, centerX - unit * 0.34f, headCenterY - unit * 0.25f, unit * 0.075f, Color.parseColor("#FF6F9C"))
                drawWidgetHeart(canvas, centerX + unit * 0.35f, headCenterY - unit * 0.22f, unit * 0.065f, Color.parseColor("#FF8BB1"))
                drawWidgetHeart(canvas, centerX, headCenterY - unit * 0.42f, unit * 0.055f, Color.parseColor("#FF6F9C"))
            }
            isPinched -> {
                val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokeW * 0.95f
                    color = Color.parseColor("#FF7597")
                    strokeCap = Paint.Cap.ROUND
                }
                val lx = centerX - headRx - leftPullPx - unit * 0.06f
                val rx = centerX + headRx + rightPullPx + unit * 0.06f
                canvas.drawLine(lx, headCenterY - unit * 0.04f, lx - unit * 0.06f, headCenterY - unit * 0.08f, sparkPaint)
                canvas.drawLine(lx, headCenterY + unit * 0.04f, lx - unit * 0.07f, headCenterY + unit * 0.04f, sparkPaint)
                canvas.drawLine(rx, headCenterY - unit * 0.04f, rx + unit * 0.06f, headCenterY - unit * 0.08f, sparkPaint)
                canvas.drawLine(rx, headCenterY + unit * 0.04f, rx + unit * 0.07f, headCenterY + unit * 0.04f, sparkPaint)
            }
            isHappy -> {
                drawWidgetSparkle(canvas, centerX - unit * 0.36f, headCenterY - unit * 0.24f, unit * 0.055f, COLOR_POLLEN)
                drawWidgetSparkle(canvas, centerX + unit * 0.36f, headCenterY - unit * 0.26f, unit * 0.052f, COLOR_DEEP_BLUE)
            }
        }

        return bitmap
    }

    private fun drawWidgetBabyBunnyKit(
        canvas: Canvas,
        cx: Float,
        bottomY: Float,
        kitSize: Float,
        tiltDeg: Float,
        coatColor: Int,
        patchColor: Int,
        innerEarColor: Int,
        blushColor: Int,
        outlineColor: Int,
        isLop: Boolean,
        isHappy: Boolean,
        kitIndex: Int
    ) {
        val strokeW = (kitSize * 0.048f).coerceIn(1.8f, 4.5f)
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = outlineColor
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        canvas.save()
        canvas.rotate(tiltDeg, cx, bottomY - kitSize * 0.3f)

        val bodyRect = RectF(
            cx - kitSize * 0.30f,
            bottomY - kitSize * 0.48f,
            cx + kitSize * 0.30f,
            bottomY
        )
        val headY = bottomY - kitSize * 0.56f
        val headRx = kitSize * 0.32f
        val headRy = kitSize * 0.27f

        // Baby ears
        if (!isLop) {
            val earW = kitSize * 0.14f
            val earH = kitSize * 0.32f
            val leftEar = RectF(cx - kitSize * 0.19f, headY - headRy - earH * 0.82f, cx - kitSize * 0.19f + earW, headY - headRy * 0.2f)
            val rightEar = RectF(cx + kitSize * 0.19f - earW, headY - headRy - earH * 0.82f, cx + kitSize * 0.19f, headY - headRy * 0.2f)
            fillPaint.color = if (kitIndex % 2 == 0) coatColor else patchColor
            canvas.drawRoundRect(leftEar, earW, earW, fillPaint)
            canvas.drawRoundRect(rightEar, earW, earW, fillPaint)
            fillPaint.color = innerEarColor
            canvas.drawRoundRect(
                RectF(leftEar.left + earW * 0.24f, leftEar.top + earH * 0.18f, leftEar.right - earW * 0.24f, leftEar.bottom - 3f),
                earW,
                earW,
                fillPaint
            )
            canvas.drawRoundRect(
                RectF(rightEar.left + earW * 0.24f, rightEar.top + earH * 0.18f, rightEar.right - earW * 0.24f, rightEar.bottom - 3f),
                earW,
                earW,
                fillPaint
            )
            canvas.drawRoundRect(leftEar, earW, earW, outlinePaint)
            canvas.drawRoundRect(rightEar, earW, earW, outlinePaint)
        }

        // Baby chubby body
        fillPaint.color = coatColor
        canvas.drawOval(bodyRect, fillPaint)
        canvas.drawOval(bodyRect, outlinePaint)

        // Baby round head
        val headRect = RectF(cx - headRx, headY - headRy, cx + headRx, headY + headRy)
        fillPaint.color = coatColor
        canvas.drawOval(headRect, fillPaint)
        canvas.drawOval(headRect, outlinePaint)

        if (isLop) {
            val lopW = kitSize * 0.13f
            val lopH = kitSize * 0.29f
            fillPaint.color = patchColor
            val leftLop = RectF(cx - headRx - lopW * 0.45f, headY - headRy * 0.15f, cx - headRx + lopW * 0.55f, headY - headRy * 0.15f + lopH)
            val rightLop = RectF(cx + headRx - lopW * 0.55f, headY - headRy * 0.15f, cx + headRx + lopW * 0.45f, headY - headRy * 0.15f + lopH)
            canvas.drawRoundRect(leftLop, lopW, lopW, fillPaint)
            canvas.drawRoundRect(leftLop, lopW, lopW, outlinePaint)
            canvas.drawRoundRect(rightLop, lopW, lopW, fillPaint)
            canvas.drawRoundRect(rightLop, lopW, lopW, outlinePaint)
        }

        // Baby rosy cheeks
        fillPaint.color = blushColor
        canvas.drawCircle(cx - headRx * 0.54f, headY + headRy * 0.18f, kitSize * 0.065f, fillPaint)
        canvas.drawCircle(cx + headRx * 0.54f, headY + headRy * 0.18f, kitSize * 0.065f, fillPaint)

        // Baby eyes
        val eyeY = headY - headRy * 0.06f
        val eyeOffset = headRx * 0.38f
        if (isHappy) {
            val arc = kitSize * 0.055f
            canvas.drawArc(RectF(cx - eyeOffset - arc, eyeY - arc * 0.6f, cx - eyeOffset + arc, eyeY + arc), 200f, 140f, false, outlinePaint)
            canvas.drawArc(RectF(cx + eyeOffset - arc, eyeY - arc * 0.6f, cx + eyeOffset + arc, eyeY + arc), 200f, 140f, false, outlinePaint)
        } else {
            val eyeR = kitSize * 0.042f
            fillPaint.color = outlineColor
            canvas.drawCircle(cx - eyeOffset, eyeY, eyeR, fillPaint)
            canvas.drawCircle(cx + eyeOffset, eyeY, eyeR, fillPaint)
            fillPaint.color = COLOR_PURE_WHITE
            canvas.drawCircle(cx - eyeOffset - eyeR * 0.28f, eyeY - eyeR * 0.28f, eyeR * 0.35f, fillPaint)
            canvas.drawCircle(cx + eyeOffset - eyeR * 0.28f, eyeY - eyeR * 0.28f, eyeR * 0.35f, fillPaint)
        }

        // Tiny pink nose
        fillPaint.color = Color.parseColor("#E38C9B")
        canvas.drawCircle(cx, headY + headRy * 0.15f, kitSize * 0.028f, fillPaint)

        // Tiny forget-me-not bud behind baby ear
        if (kitIndex == 0 || isHappy) {
            drawMiniForgetMeNot(canvas, cx + headRx * 0.62f, headY - headRy * 0.72f, kitSize * 0.085f, true)
        }

        canvas.restore()
    }

    private fun drawWidgetHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val r = size * 0.32f
        canvas.drawCircle(cx - r * 0.85f, cy - r * 0.35f, r, paint)
        canvas.drawCircle(cx + r * 0.85f, cy - r * 0.35f, r, paint)
        val path = android.graphics.Path().apply {
            moveTo(cx - r * 1.78f, cy - r * 0.10f)
            lineTo(cx, cy + size * 0.62f)
            lineTo(cx + r * 1.78f, cy - r * 0.10f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawWidgetSparkle(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (radius * 0.32f).coerceAtLeast(2.2f)
            this.color = color
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)
        val d = radius * 0.58f
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
        canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint)
    }
}
