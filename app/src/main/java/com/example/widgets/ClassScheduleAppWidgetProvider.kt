package com.example.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.data.ClassColorKey
import com.example.data.PlannerDateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClassScheduleAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = WidgetDataLoader.load(context)
                updateWidgets(context, appWidgetManager, appWidgetIds, snapshot)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val ROW_IDS = intArrayOf(
            R.id.widget_cls_row_0,
            R.id.widget_cls_row_1,
            R.id.widget_cls_row_2,
            R.id.widget_cls_row_3
        )
        private val BAR_IDS = intArrayOf(
            R.id.widget_cls_bar_0,
            R.id.widget_cls_bar_1,
            R.id.widget_cls_bar_2,
            R.id.widget_cls_bar_3
        )
        private val SUBJECT_IDS = intArrayOf(
            R.id.widget_cls_subject_0,
            R.id.widget_cls_subject_1,
            R.id.widget_cls_subject_2,
            R.id.widget_cls_subject_3
        )
        private val META_IDS = intArrayOf(
            R.id.widget_cls_meta_0,
            R.id.widget_cls_meta_1,
            R.id.widget_cls_meta_2,
            R.id.widget_cls_meta_3
        )
        private val TAG_IDS = intArrayOf(
            R.id.widget_cls_tag_0,
            R.id.widget_cls_tag_1,
            R.id.widget_cls_tag_2,
            R.id.widget_cls_tag_3
        )
        private val BELL_IDS = intArrayOf(
            R.id.widget_cls_bell_0,
            R.id.widget_cls_bell_1,
            R.id.widget_cls_bell_2,
            R.id.widget_cls_bell_3
        )

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetSnapshot
        ) {
            val today = snapshot.today
            val nowMin = snapshot.currentMinutesOfDay
            val classesToday = snapshot.todayClasses

            val openClassesIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1201,
                tab = WidgetIntents.TAB_CLASSES
            )
            val addClassIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1202,
                tab = WidgetIntents.TAB_CLASSES,
                openAction = WidgetIntents.ACTION_OPEN_ADD_CLASS
            )

            // Identify active (NOW) or soonest upcoming (NEXT) class index
            val nowIdx = classesToday.indexOfFirst { nowMin in it.startTimeMinutes until it.endTimeMinutes }
            val nextIdx = if (nowIdx < 0) {
                classesToday.indexOfFirst { it.startTimeMinutes >= nowMin }
            } else {
                -1
            }

            val dayLabel = PlannerDateUtils.formatFullDateHeader(today)

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_class_schedule).apply {
                    setTextViewText(R.id.widget_cls_title, "Class Schedule")
                    setTextViewText(R.id.widget_cls_subtitle, dayLabel)
                    setTextViewText(
                        R.id.widget_cls_count_badge,
                        if (classesToday.size == 1) "1 class" else "${classesToday.size} classes"
                    )

                    setOnClickPendingIntent(android.R.id.background, openClassesIntent)
                    setOnClickPendingIntent(R.id.widget_cls_header, openClassesIntent)
                    setOnClickPendingIntent(R.id.widget_cls_add_btn, addClassIntent)

                    if (classesToday.isEmpty()) {
                        ROW_IDS.forEach { setViewVisibility(it, View.GONE) }
                        setViewVisibility(R.id.widget_cls_more, View.GONE)
                        setViewVisibility(R.id.widget_cls_empty_card, View.VISIBLE)
                        setOnClickPendingIntent(R.id.widget_cls_empty_card, openClassesIntent)

                        val upcoming = snapshot.nextUpcomingClass
                        if (upcoming != null) {
                            val rel = WidgetDataLoader.formatCompactDateOrRelative(upcoming.date, today)
                            val roomPart = upcoming.schedule.room?.let { " · $it" } ?: ""
                            setTextViewText(R.id.widget_cls_empty_title, "No classes today")
                            setTextViewText(
                                R.id.widget_cls_empty_subtitle,
                                "Next: ${upcoming.schedule.subject} · $rel, ${PlannerDateUtils.formatTimeOfDay(upcoming.schedule.startTimeMinutes)}$roomPart"
                            )
                        } else {
                            setTextViewText(R.id.widget_cls_empty_title, "No classes scheduled yet")
                            setTextViewText(
                                R.id.widget_cls_empty_subtitle,
                                "Tap + to add subjects and semester reminders."
                            )
                        }
                    } else {
                        setViewVisibility(R.id.widget_cls_empty_card, View.GONE)
                        val visible = classesToday.take(ROW_IDS.size)

                        for (i in ROW_IDS.indices) {
                            if (i < visible.size) {
                                val cls = visible[i]
                                setViewVisibility(ROW_IDS[i], View.VISIBLE)
                                setOnClickPendingIntent(ROW_IDS[i], openClassesIntent)
                                setImageViewResource(BAR_IDS[i], barDrawableFor(cls.colorKeyEnum()))
                                setTextViewText(SUBJECT_IDS[i], cls.subject)
                                val meta = buildString {
                                    append(cls.formattedTimeRange())
                                    if (!cls.room.isNullOrBlank()) {
                                        append(" · ")
                                        append(cls.room)
                                    }
                                }
                                setTextViewText(META_IDS[i], meta)

                                when (i) {
                                    nowIdx -> {
                                        setViewVisibility(TAG_IDS[i], View.VISIBLE)
                                        setTextViewText(TAG_IDS[i], "NOW")
                                        setInt(TAG_IDS[i], "setBackgroundResource", R.drawable.widget_pill_deep_blue)
                                        setTextColor(TAG_IDS[i], Color.parseColor("#FCFEFF"))
                                    }
                                    nextIdx -> {
                                        setViewVisibility(TAG_IDS[i], View.VISIBLE)
                                        setTextViewText(TAG_IDS[i], "NEXT")
                                        setInt(TAG_IDS[i], "setBackgroundResource", R.drawable.widget_pill_sage)
                                        setTextColor(TAG_IDS[i], Color.parseColor("#35917A"))
                                    }
                                    else -> {
                                        setViewVisibility(TAG_IDS[i], View.GONE)
                                    }
                                }

                                setViewVisibility(
                                    BELL_IDS[i],
                                    if (cls.notifyEnabled) View.VISIBLE else View.GONE
                                )
                            } else {
                                setViewVisibility(ROW_IDS[i], View.GONE)
                            }
                        }

                        val overflow = classesToday.size - visible.size
                        if (overflow > 0) {
                            setViewVisibility(R.id.widget_cls_more, View.VISIBLE)
                            setTextViewText(R.id.widget_cls_more, "+$overflow more today · View all")
                            setOnClickPendingIntent(R.id.widget_cls_more, openClassesIntent)
                        } else {
                            setViewVisibility(R.id.widget_cls_more, View.GONE)
                        }
                    }
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun barDrawableFor(key: ClassColorKey): Int = when (key) {
            ClassColorKey.SKY -> R.drawable.widget_bar_sky
            ClassColorKey.CORNFLOWER -> R.drawable.widget_bar_cornflower
            ClassColorKey.SAGE -> R.drawable.widget_bar_sage
            ClassColorKey.POLLEN -> R.drawable.widget_bar_pollen
        }
    }
}
