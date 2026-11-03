package org.fossify.calendar.fragments

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import org.fossify.calendar.R
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.databinding.FragmentWeekGridBinding
import org.fossify.calendar.databinding.WeekGridDayBinding
import org.fossify.calendar.databinding.WeekGridEventBinding
import org.fossify.calendar.extensions.gridZoneMarker
import org.fossify.calendar.extensions.ThemeSlot
import org.fossify.calendar.extensions.applyCategoryFont
import org.fossify.calendar.extensions.applyThemeFont
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.formatEventClock
import org.fossify.calendar.extensions.launchNewEventIntent
import org.fossify.calendar.extensions.launchNewTaskIntent
import org.fossify.calendar.extensions.readableOn
import org.fossify.calendar.extensions.regularDayBoxBorderColor
import org.fossify.calendar.extensions.themeColor
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_CENTER
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_START
import org.fossify.calendar.helpers.EVENT_ID
import org.fossify.calendar.helpers.EVENT_OCCURRENCE_TS
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.formatDayBoxHeader
import org.fossify.calendar.helpers.dayFrameColor
import org.fossify.calendar.helpers.hasDayOff
import org.fossify.calendar.helpers.IS_TASK_COMPLETED
import org.fossify.calendar.helpers.REGULAR_DAY_BOX_BORDER_DP
import org.fossify.calendar.helpers.SPECIAL_DAY_FRAME_DP
import org.fossify.calendar.helpers.SPECIAL_DAY_FRAME_GAP_DP
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.calendar.helpers.TYPE_EVENT
import org.fossify.calendar.helpers.TYPE_TASK
import org.fossify.calendar.helpers.WEEK_START_TIMESTAMP
import org.fossify.calendar.helpers.getActivityToOpen
import org.fossify.calendar.models.Event
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.fossify.commons.models.RadioItem
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import kotlin.math.roundToInt

// One week rendered as the Pimlical-style "day box" grid: days 0-4 get full-height boxes,
// the last two days share the bottom-right cell (split). Each box is a header bar (date) plus a
// vertical list of that day's events as text lines.
class WeekGridFragment : Fragment() {
    private lateinit var binding: FragmentWeekGridBinding
    private var weekStartTS = 0L
    private var weekStartDateTime = DateTime()
    private var dayCells = listOf<WeekGridDayBinding>()
    private var mWasDestroyed = false

    // Per-day marks from the last fetch (a 休み entry, a category's whole-day frame colour). Kept
    // between fetches so a refresh restyles the boxes with the known marks instead of flashing plain.
    private val dayOffs = BooleanArray(7)
    private val dayFrameColors = IntArray(7) { THEME_UNSET }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        weekStartTS = requireArguments().getLong(WEEK_START_TIMESTAMP)
        weekStartDateTime = Formatter.getDateTimeFromTS(weekStartTS)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWeekGridBinding.inflate(inflater, container, false)
        dayCells = listOf(
            binding.weekGridCell0, binding.weekGridCell1, binding.weekGridCell2, binding.weekGridCell3,
            binding.weekGridCell4, binding.weekGridCell5, binding.weekGridCell6
        )
        binding.weekGridRoot.background = ColorDrawable(requireContext().getProperBackgroundColor())
        setupDayBoxes()
        fetchEvents()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Re-fetch on resume so newly created/edited events show up, and to pick up a date rollover
        // or theme change. Unlike the other fragments this view had no onResume refresh, so a new
        // event only appeared after the holder recreated the fragment (e.g. switching views).
        refreshEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mWasDestroyed = true
    }

    fun refreshEvents() {
        if (!mWasDestroyed && isAdded && context != null) {
            setupDayBoxes()
            fetchEvents()
        }
    }

    private fun setupDayBoxes() {
        val ctx = requireContext()
        val highlightWeekends = ctx.config.highlightWeekends
        val todayCode = Formatter.getTodayCode()
        val backgroundColor = ctx.getProperBackgroundColor()

        val headerGravity = when (ctx.config.dayBoxHeaderAlignment) {
            DAY_BOX_ALIGN_START -> Gravity.START
            DAY_BOX_ALIGN_CENTER -> Gravity.CENTER_HORIZONTAL
            else -> Gravity.END
        } or Gravity.CENTER_VERTICAL

        val density = ctx.resources.displayMetrics.density
        val generalBoxBorderDp = ctx.config.dayBoxBorderThickness
        val generalHeaderBorderDp = ctx.config.dayBoxHeaderBorderThickness
        val regularOutlineColor = ctx.regularDayBoxBorderColor()
        val regularOutlinePx = (REGULAR_DAY_BOX_BORDER_DP * density).roundToInt().coerceAtLeast(1)
        val specialFramePx = (SPECIAL_DAY_FRAME_DP * density).roundToInt()
        val specialFrameGapPx = (SPECIAL_DAY_FRAME_GAP_DP * density).roundToInt()

        for (i in 0 until 7) {
            val dayDateTime = weekStartDateTime.plusDays(i)
            val dayCode = Formatter.getDayCodeFromDateTime(dayDateTime)
            val isToday = dayCode == todayCode
            val dayOfWeek = dayDateTime.dayOfWeek
            val isWeekendDay = dayOfWeek == DateTimeConstants.SATURDAY || dayOfWeek == DateTimeConstants.SUNDAY
            val isWeekend = isWeekendDay && highlightWeekends
            // A day off (休み entry) has its own frame / date colours under 休み in 白い熊 予定表 UI.
            val isDayOff = dayOffs[i]
            val frameColor = dayFrameColors[i]
            val isFramed = frameColor != THEME_UNSET
            val cell = dayCells[i]

            // Variant C (see helpers/DayMarks): every box has the same thin yellow outline (today's is
            // the thick today border). A weekend / 休み / framed (holiday) day additionally gets a thin
            // frame in its colour a little inside that outline, and its date header filled in that
            // colour with the date in the weekend text colour — today included.
            val specialColor = when {
                isFramed -> frameColor
                isDayOff -> ctx.themeColor(ThemeSlot.DAY_OFF_FRAME)
                isWeekend -> ctx.themeColor(ThemeSlot.WEEKEND_BOX_BORDER)
                else -> null
            }
            val headerColor = when {
                specialColor != null -> specialColor
                isToday -> ctx.themeColor(ThemeSlot.TODAY_HIGHLIGHT)
                else -> ctx.themeColor(ThemeSlot.DAY_BOX_HEADER)
            }
            val textSlot = when {
                specialColor != null -> ThemeSlot.WEEKEND_TEXT
                isToday -> ThemeSlot.TODAY_TEXT
                else -> ThemeSlot.DAY_BOX_HEADER_TEXT
            }
            // Color resolves through the slot system: today/weekend text inherits the day-box header
            // text (its default) until given an explicit override of its own.
            val headerTextColor = (if (isDayOff && !isFramed) ctx.themeColor(ThemeSlot.DAY_OFF_DATE_TEXT) else ctx.themeColor(textSlot)).let { if (specialColor != null) it.readableOn(specialColor) else it }
            val headerBorderColor = when {
                specialColor != null -> specialColor
                isToday -> ctx.themeColor(ThemeSlot.TODAY_HEADER_BORDER)
                else -> ctx.themeColor(ThemeSlot.DAY_BOX_HEADER_BORDER)
            }
            val outlineColor = if (isToday) ctx.themeColor(ThemeSlot.TODAY_BOX_BORDER) else regularOutlineColor
            val outlinePx = if (isToday) {
                (resolveThickness(true, false, ctx.config.todayBoxBorderThickness, 0, generalBoxBorderDp) * density).toInt()
            } else {
                regularOutlinePx
            }
            val headerBorderPx = (resolveThickness(
                isToday && specialColor == null, false, ctx.config.todayHeaderBorderThickness, 0, generalHeaderBorderDp
            ) * density).toInt()

            cell.weekGridDayHeader.text = formatDayBoxHeader(dayDateTime.millis, ctx.config.dayBoxHeaderDateFormat)
            cell.weekGridDayHeader.background = GradientDrawable().apply {
                setColor(headerColor)
                if (headerBorderPx > 0) {
                    setStroke(headerBorderPx, headerBorderColor)
                }
            }
            cell.weekGridDayHeader.setTextColor(headerTextColor)
            cell.weekGridDayHeader.gravity = headerGravity
            cell.weekGridDayHeader.applyThemeFont(textSlot, Typeface.BOLD)

            val boxFill = if (isToday) ctx.themeColor(ThemeSlot.TODAY_BOX_FILL) else backgroundColor
            val outline = GradientDrawable().apply {
                setColor(boxFill)
                if (outlinePx > 0) {
                    setStroke(outlinePx, outlineColor)
                }
            }
            // Inset the box content past every stroke so opaque event backgrounds (category colors) and
            // the header don't paint over the outline or the inner frame.
            var contentInset = outlinePx
            cell.weekGridDayBox.background = if (specialColor != null) {
                val frameInset = outlinePx + specialFrameGapPx
                val frame = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(specialFramePx, specialColor)
                }
                contentInset = frameInset + specialFramePx
                LayerDrawable(arrayOf(outline, frame)).apply {
                    setLayerInset(1, frameInset, frameInset, frameInset, frameInset)
                }
            } else {
                outline
            }
            cell.weekGridDayBox.setPadding(contentInset, contentInset, contentInset, contentInset)
            cell.weekGridDayEvents.removeAllViews()
            // Tap the box / header / empty space -> open this day's day view (back returns here).
            // Long-press the header or empty space -> add a new event/task. (Per-event tap/long-press
            // is wired in addEventLine.) The events area fills the box (fillViewport) and carries its
            // own listeners, since the ScrollView would otherwise swallow them.
            val openDay = View.OnClickListener {
                (activity as? MainActivity)?.openDayFromMonthly(dayDateTime)
            }
            val addEventOnLongPress = View.OnLongClickListener {
                addEventOrTask(dayCode)
                true
            }
            cell.weekGridDayBox.setOnClickListener(openDay)
            cell.weekGridDayBox.setOnLongClickListener(addEventOnLongPress)
            cell.weekGridDayHeader.setOnClickListener(openDay)
            cell.weekGridDayHeader.setOnLongClickListener(addEventOnLongPress)
            cell.weekGridDayEvents.setOnClickListener(openDay)
            cell.weekGridDayEvents.setOnLongClickListener(addEventOnLongPress)
        }
        binding.weekGridLines.invalidate()
    }

    // Today/weekend thickness overrides the general value unless it is the inherit sentinel (< 0).
    private fun resolveThickness(isToday: Boolean, isWeekend: Boolean, todayDp: Int, weekendDp: Int, generalDp: Int) = when {
        isToday -> if (todayDp >= 0) todayDp else generalDp
        isWeekend -> if (weekendDp >= 0) weekendDp else generalDp
        else -> generalDp
    }

    private fun fetchEvents() {
        val ctx = context ?: return
        val weekEndTS = Formatter.getDayEndTS(Formatter.getDayCodeFromDateTime(weekStartDateTime.plusDays(6)))
        ctx.eventsHelper.getEvents(weekStartTS, weekEndTS) { events ->
            activity?.runOnUiThread {
                if (!mWasDestroyed && isAdded && context != null) {
                    fillDays(events)
                }
            }
        }
    }

    private fun fillDays(events: List<Event>) {
        val ctx = context ?: return
        val dimPastEvents = ctx.config.dimPastEvents
        val dimCompletedTasks = ctx.config.dimCompletedTasks

        val eventsPerDay = (0 until 7).map { i ->
            val dayCode = Formatter.getDayCodeFromDateTime(weekStartDateTime.plusDays(i))
            val dayStart = Formatter.getDayStartTS(dayCode)
            val dayEnd = Formatter.getDayEndTS(dayCode)
            events.filter { it.startTS <= dayEnd && it.endTS >= dayStart }
        }

        // Restyle the boxes first when a day's marks changed (a 休み entry, a framed category).
        var marksChanged = false
        eventsPerDay.forEachIndexed { i, dayEvents ->
            val isDayOff = dayEvents.hasDayOff()
            val frameColor = dayEvents.dayFrameColor()
            if (dayOffs[i] != isDayOff || dayFrameColors[i] != frameColor) {
                dayOffs[i] = isDayOff
                dayFrameColors[i] = frameColor
                marksChanged = true
            }
        }
        if (marksChanged) {
            setupDayBoxes()
        }

        for (i in 0 until 7) {
            val dayDateTime = weekStartDateTime.plusDays(i)
            val container = dayCells[i].weekGridDayEvents
            container.removeAllViews()

            eventsPerDay[i].asSequence()
                .sortedWith(compareBy({ !it.getIsAllDay() }, { it.startTS }, { it.title }))
                .forEach { addEventLine(container, it, dayDateTime, dimPastEvents, dimCompletedTasks) }
        }
    }

    private fun addEventLine(container: LinearLayout, event: Event, dayDateTime: DateTime, dimPastEvents: Boolean, dimCompletedTasks: Boolean) {
        val ctx = requireContext()
        val line = WeekGridEventBinding.inflate(layoutInflater, container, false)
        val label = if (event.getIsAllDay()) {
            event.title
        } else {
            "${formatEventClock(event.startTS, ctx.config.eventTimeFormat)} ${event.gridZoneMarker()}${event.title}"
        }

        var color = if (event.color != 0) event.color else ctx.themeColor(ThemeSlot.EVENT_TEXT)
        val shouldDim = if (event.isTask()) {
            dimCompletedTasks && event.isTaskCompleted()
        } else {
            dimPastEvents && event.isPastEvent
        }
        if (shouldDim) {
            color = color.adjustAlpha(MEDIUM_ALPHA)
        }

        line.root.text = label
        line.root.setTextColor(color)
        // Category (event type) styling: its own font and background when set, else the themed event font.
        if (event.categoryFontFamily.isNotEmpty() || event.categoryFontWeight > 0 || event.categoryFontSize > 0) {
            line.root.applyCategoryFont(event.categoryFontFamily, event.categoryFontWeight, event.categoryFontSize)
        } else {
            line.root.applyThemeFont(ThemeSlot.EVENT_TEXT)
        }
        if (event.categoryBackgroundColor != THEME_UNSET) {
            line.root.setBackgroundColor(event.categoryBackgroundColor)
        }
        if (event.isTaskCompleted()) {
            line.root.paintFlags = line.root.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        // Tap an event -> this day's day view (same as tapping the box); long-press -> its details.
        line.root.setOnClickListener { (activity as? MainActivity)?.openDayFromMonthly(dayDateTime) }
        line.root.setOnLongClickListener {
            openEvent(event)
            true
        }
        container.addView(line.root)
    }

    // Match the regular week view's add flow: offer the Event/Task chooser when tasks are enabled,
    // otherwise go straight to a new event.
    private fun addEventOrTask(dayCode: String) {
        val ctx = requireContext()
        if (ctx.config.allowCreatingTasks) {
            val items = arrayListOf(
                RadioItem(TYPE_EVENT, getString(R.string.event)),
                RadioItem(TYPE_TASK, getString(R.string.task))
            )
            RadioGroupDialog(requireActivity(), items) {
                if (it as Int == TYPE_TASK) ctx.launchNewTaskIntent(dayCode) else ctx.launchNewEventIntent(dayCode)
            }
        } else {
            ctx.launchNewEventIntent(dayCode)
        }
    }

    private fun openEvent(event: Event) {
        Intent(requireContext(), getActivityToOpen(event.isTask())).apply {
            putExtra(EVENT_ID, event.id)
            putExtra(EVENT_OCCURRENCE_TS, event.startTS)
            putExtra(IS_TASK_COMPLETED, event.isTaskCompleted())
            startActivity(this)
        }
    }
}
