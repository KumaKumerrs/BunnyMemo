package com.example.focus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Floating Anti-Doomscroll Bunny overlay container rendered over distracting social apps.
 *
 * Progressively scales from a tiny peeking bunny (0..35%) -> concerned bunny (35..70%)
 * -> giant blocking bunny (70..99%) -> full-screen takeover shield (100%) that nudges
 * the user back to Bunny Memo and productivity, with a respectful 5-min pause escape hatch.
 */
class BunnyOverlayView(
    context: Context,
    private val onBackToProductivity: () -> Unit,
    private val onPauseFiveMinutes: () -> Unit
) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val bunnyCanvasView = BunnyCanvasDrawableView(context)
    private val takeoverOverlayColumn: LinearLayout
    private val headlineText: TextView
    private val detailText: TextView
    private val timerChipText: TextView
    private val compactBadgeText: TextView
    private val backButton: TextView
    private val secondaryActionButton: TextView

    private var currentProgress: Float = 0f
    private var currentAppName: String = "Social Media"
    private var currentElapsedSec: Int = 0
    private var currentMaxSec: Int = 180
    private var isFocusSessionMode: Boolean = false

    init {
        setBackgroundColor(Color.TRANSPARENT)

        // Growing bunny illustration canvas
        addView(
            bunnyCanvasView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )

        // Small floating countdown badge shown while growing (progress < 1f)
        compactBadgeText = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#FCFEFF"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = createPillDrawable(Color.parseColor("#D92B4360"), dp(16).toFloat())
        }
        addView(
            compactBadgeText,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(4)
            }
        )

        // Full-screen takeover message & action panel (shown at 100%)
        takeoverOverlayColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
            visibility = GONE
        }

        timerChipText = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#2B4360"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = createPillDrawable(Color.parseColor("#F7D56E"), dp(20).toFloat())
        }

        headlineText = TextView(context).apply {
            textSize = 24f
            setTextColor(Color.parseColor("#FCFEFF"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(6))
        }

        detailText = TextView(context).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#DCECFD"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }

        backButton = TextView(context).apply {
            text = "Back to Bunny Memo"
            textSize = 16f
            setTextColor(Color.parseColor("#FCFEFF"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = dp(52)
            setPadding(dp(28), dp(14), dp(28), dp(14))
            background = createPillDrawable(Color.parseColor("#3B82DC"), dp(28).toFloat())
            isClickable = true
            isFocusable = true
            setOnClickListener { onBackToProductivity() }
        }

        secondaryActionButton = TextView(context).apply {
            text = "Pause guard for 5 min"
            textSize = 13f
            setTextColor(Color.parseColor("#C7DFF8"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onPauseFiveMinutes() }
        }

        takeoverOverlayColumn.addView(
            timerChipText,
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
        takeoverOverlayColumn.addView(
            headlineText,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
        takeoverOverlayColumn.addView(
            detailText,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
        takeoverOverlayColumn.addView(
            backButton,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        )
        takeoverOverlayColumn.addView(
            secondaryActionButton,
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )

        addView(
            takeoverOverlayColumn,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        )
    }

    fun updateState(
        progress: Float,
        appName: String,
        elapsedSeconds: Int,
        maxSeconds: Int,
        focusSessionCountdown: String? = null,
        focusTaskTitle: String? = null
    ) {
        isFocusSessionMode = focusSessionCountdown != null
        currentProgress = if (isFocusSessionMode) 1f else progress.coerceIn(0f, 1f)
        currentAppName = appName
        currentElapsedSec = elapsedSeconds
        currentMaxSec = maxSeconds.coerceAtLeast(1)

        val isTakeover = isFocusSessionMode || currentProgress >= 0.995f
        bunnyCanvasView.setProgress(currentProgress)

        if (isTakeover) {
            setBackgroundColor(Color.parseColor("#F20B1324"))
            compactBadgeText.visibility = GONE
            takeoverOverlayColumn.visibility = VISIBLE
            if (isFocusSessionMode) {
                timerChipText.text = "Focus Session · $focusSessionCountdown left"
                headlineText.text = "$appName is paused during focus!"
                detailText.text = if (!focusTaskTitle.isNullOrBlank()) {
                    "Stay committed to \"$focusTaskTitle\" — your bunny is cheering you on."
                } else {
                    "Your Pomodoro focus timer is running. Step back and finish strong!"
                }
                backButton.text = "Return to Focus Timer"
                secondaryActionButton.text = "Pause Focus Session"
            } else {
                timerChipText.text = "${DoomscrollGuardManager.formatDurationShort(elapsedSeconds)} on $appName"
                headlineText.text = "Time to bloom, not doomscroll!"
                detailText.text = "Your Bunny Memo guardian filled the screen so you can step back and finish today's plans."
                backButton.text = "Back to Bunny Memo"
                secondaryActionButton.text = "Pause guard for 5 min"
            }
            (bunnyCanvasView.layoutParams as LayoutParams).apply {
                bottomMargin = dp(220)
            }
            bunnyCanvasView.requestLayout()
        } else {
            setBackgroundColor(Color.TRANSPARENT)
            takeoverOverlayColumn.visibility = GONE
            compactBadgeText.visibility = VISIBLE
            val pct = (currentProgress * 100f).toInt()
            compactBadgeText.text = when {
                currentProgress < 0.35f -> "${DoomscrollGuardManager.formatDurationShort(elapsedSeconds)} · peeking"
                currentProgress < 0.70f -> "$pct% · bunny growing!"
                else -> "$pct% · almost full screen!"
            }
            (bunnyCanvasView.layoutParams as LayoutParams).apply {
                bottomMargin = 0
            }
            bunnyCanvasView.requestLayout()
        }
    }

    private fun createPillDrawable(fillColor: Int, radiusPx: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fillColor)
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private class BunnyCanvasDrawableView(context: Context) : View(context) {
        private var progress: Float = 0f

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#2B4360")
        }

        fun setProgress(value: Float) {
            progress = value.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val size = min(w, h)
            if (size <= 4f) return

            val ox = (w - size) / 2f
            val oy = (h - size) / 2f
            val strokeW = (size * 0.026f).coerceIn(3f, 10f)
            strokePaint.strokeWidth = strokeW

            // Soft sky halo behind bunny when growing big
            if (progress > 0.25f) {
                fillPaint.color = Color.argb(((progress * 110f).toInt()).coerceIn(25, 115), 124, 182, 249)
                canvas.drawCircle(ox + size * 0.50f, oy + size * 0.56f, size * 0.43f, fillPaint)
            }

            // Left & right ears anchored into head crown
            val earTilt = 10f + progress * 8f
            drawEar(canvas, ox + size * 0.39f, oy + size * 0.35f, size, -earTilt)
            drawEar(canvas, ox + size * 0.61f, oy + size * 0.35f, size, earTilt)

            // Body
            val bodyRect = RectF(ox + size * 0.24f, oy + size * 0.53f, ox + size * 0.76f, oy + size * 0.90f)
            fillPaint.color = Color.parseColor("#FCFEFF")
            canvas.drawOval(bodyRect, fillPaint)
            canvas.drawOval(bodyRect, strokePaint)

            // Head
            val headRect = RectF(ox + size * 0.23f, oy + size * 0.28f, ox + size * 0.77f, oy + size * 0.67f)
            fillPaint.color = Color.parseColor("#FCFEFF")
            canvas.drawOval(headRect, fillPaint)
            canvas.drawOval(headRect, strokePaint)

            // Blush cheeks (turn warmer terracotta/pollen as scroll gets longer)
            fillPaint.color = if (progress >= 0.70f) {
                Color.parseColor("#F5B0B8")
            } else {
                Color.parseColor("#CDE5FD")
            }
            canvas.drawCircle(ox + size * 0.33f, oy + size * 0.52f, size * 0.048f, fillPaint)
            canvas.drawCircle(ox + size * 0.67f, oy + size * 0.52f, size * 0.048f, fillPaint)

            // Eyes
            fillPaint.color = Color.parseColor("#2B4360")
            val eyeR = size * 0.030f
            canvas.drawCircle(ox + size * 0.41f, oy + size * 0.45f, eyeR, fillPaint)
            canvas.drawCircle(ox + size * 0.59f, oy + size * 0.45f, eyeR, fillPaint)

            // Progressive eyebrows (cute determined stare as user keeps doomscrolling!)
            if (progress >= 0.35f) {
                val intensity = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                val browDip = size * 0.028f * intensity
                canvas.drawLine(
                    ox + size * 0.35f, oy + size * 0.39f - browDip * 0.3f,
                    ox + size * 0.45f, oy + size * 0.39f + browDip,
                    strokePaint
                )
                canvas.drawLine(
                    ox + size * 0.65f, oy + size * 0.39f - browDip * 0.3f,
                    ox + size * 0.55f, oy + size * 0.39f + browDip,
                    strokePaint
                )
            }

            // Tiny nose
            canvas.drawCircle(ox + size * 0.50f, oy + size * 0.50f, size * 0.019f, fillPaint)

            // Mouth transitions from cute smile -> tiny o -> gentle frown at full takeover
            val mouthPath = Path()
            when {
                progress < 0.45f -> {
                    mouthPath.moveTo(ox + size * 0.45f, oy + size * 0.545f)
                    mouthPath.quadTo(ox + size * 0.50f, oy + size * 0.58f, ox + size * 0.55f, oy + size * 0.545f)
                    canvas.drawPath(mouthPath, strokePaint)
                }
                progress < 0.80f -> {
                    canvas.drawCircle(ox + size * 0.50f, oy + size * 0.555f, size * 0.022f, strokePaint)
                }
                else -> {
                    mouthPath.moveTo(ox + size * 0.44f, oy + size * 0.575f)
                    mouthPath.quadTo(ox + size * 0.50f, oy + size * 0.535f, ox + size * 0.56f, oy + size * 0.575f)
                    canvas.drawPath(mouthPath, strokePaint)
                }
            }

            // Little front paws holding a forget-me-not flower
            val leftPaw = RectF(ox + size * 0.31f, oy + size * 0.78f, ox + size * 0.45f, oy + size * 0.88f)
            val rightPaw = RectF(ox + size * 0.55f, oy + size * 0.78f, ox + size * 0.69f, oy + size * 0.88f)
            fillPaint.color = Color.parseColor("#FCFEFF")
            canvas.drawOval(leftPaw, fillPaint)
            canvas.drawOval(leftPaw, strokePaint)
            canvas.drawOval(rightPaw, fillPaint)
            canvas.drawOval(rightPaw, strokePaint)

            // Forget-me-not flower perched on bunny's head crown
            drawForgetMeNot(canvas, ox + size * 0.64f, oy + size * 0.29f, size * 0.082f)
        }

        private fun drawEar(canvas: Canvas, pivotX: Float, pivotY: Float, size: Float, angleDeg: Float) {
            canvas.save()
            canvas.rotate(angleDeg, pivotX, pivotY)
            val outer = RectF(
                pivotX - size * 0.072f,
                pivotY - size * 0.29f,
                pivotX + size * 0.072f,
                pivotY + size * 0.03f
            )
            fillPaint.color = Color.parseColor("#FCFEFF")
            canvas.drawOval(outer, fillPaint)
            canvas.drawOval(outer, strokePaint)

            val inner = RectF(
                pivotX - size * 0.034f,
                pivotY - size * 0.23f,
                pivotX + size * 0.034f,
                pivotY - size * 0.03f
            )
            fillPaint.color = Color.parseColor("#CDE5FD")
            canvas.drawOval(inner, fillPaint)
            canvas.restore()
        }

        private fun drawForgetMeNot(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
            val petalDist = radius * 0.48f
            val petalR = radius * 0.38f
            for (i in 0 until 5) {
                val rad = ((-90.0 + i * 72.0) * PI / 180.0)
                val px = cx + (petalDist * cos(rad)).toFloat()
                val py = cy + (petalDist * sin(rad)).toFloat()
                fillPaint.color = Color.parseColor("#7CB6F9")
                canvas.drawCircle(px, py, petalR, fillPaint)
            }
            fillPaint.color = Color.parseColor("#FCFEFF")
            canvas.drawCircle(cx, cy, radius * 0.30f, fillPaint)
            fillPaint.color = Color.parseColor("#F7D56E")
            canvas.drawCircle(cx, cy, radius * 0.21f, fillPaint)
        }
    }
}
