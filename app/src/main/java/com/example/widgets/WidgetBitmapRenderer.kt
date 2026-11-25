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
}
