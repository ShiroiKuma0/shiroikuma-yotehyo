package org.fossify.calendar.fragments

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import org.fossify.calendar.databinding.FragmentWeekGridBinding
import org.fossify.calendar.databinding.WeekGridDayBinding
import org.fossify.calendar.databinding.WeekGridEventBinding
import org.fossify.calendar.extensions.ThemeSlot
import org.fossify.calendar.extensions.applyThemeFont
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.launchNewEventIntent
import org.fossify.calendar.extensions.themeColor
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_CENTER
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_START
import org.fossify.calendar.helpers.EVENT_ID
import org.fossify.calendar.helpers.EVENT_OCCURRENCE_TS
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.formatDayBoxHeader
import org.fossify.calendar.helpers.IS_TASK_COMPLETED
import org.fossify.calendar.helpers.WEEK_START_TIMESTAMP
import org.fossify.calendar.helpers.getActivityToOpen
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants

// One week rendered as the Pimlical-style "day box" grid: days 0-4 get full-height boxes,
// the last two days share the bottom-right cell (split). Each box is a header bar (date) plus a
// vertical list of that day's events as text lines.
class WeekGridFragment : Fragment() {
    private lateinit var binding: FragmentWeekGridBinding
    private var weekStartTS = 0L
    private var weekStartDateTime = DateTime()
    private var dayCells = listOf<WeekGridDayBinding>()
    private var mWasDestroyed = false

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

        for (i in 0 until 7) {
            val dayDateTime = weekStartDateTime.plusDays(i)
            val dayCode = Formatter.getDayCodeFromDateTime(dayDateTime)
            val isToday = dayCode == todayCode
            val dayOfWeek = dayDateTime.dayOfWeek
            val isWeekend = (dayOfWeek == DateTimeConstants.SATURDAY || dayOfWeek == DateTimeConstants.SUNDAY) && highlightWeekends
            val cell = dayCells[i]

            // Each box type (today / styled-weekend / normal) resolves its own header bg, text,
            // header border and box border slots; the today/weekend slots inherit the general ones.
            val headerColor = when {
                isToday -> ctx.themeColor(ThemeSlot.TODAY_HIGHLIGHT)
                isWeekend -> ctx.themeColor(ThemeSlot.WEEKEND)
                else -> ctx.themeColor(ThemeSlot.DAY_BOX_HEADER)
            }
            val textSlot = when {
                isToday -> ThemeSlot.TODAY_TEXT
                isWeekend -> ThemeSlot.WEEKEND_TEXT
                else -> ThemeSlot.DAY_BOX_HEADER_TEXT
            }
            // Color resolves through the slot system: today/weekend text inherits the day-box header
            // text (its default) until given an explicit override of its own.
            val headerTextColor = ctx.themeColor(textSlot)
            val headerBorderColor = when {
                isToday -> ctx.themeColor(ThemeSlot.TODAY_HEADER_BORDER)
                isWeekend -> ctx.themeColor(ThemeSlot.WEEKEND_HEADER_BORDER)
                else -> ctx.themeColor(ThemeSlot.DAY_BOX_HEADER_BORDER)
            }
            val boxBorderColor = when {
                isToday -> ctx.themeColor(ThemeSlot.TODAY_BOX_BORDER)
                isWeekend -> ctx.themeColor(ThemeSlot.WEEKEND_BOX_BORDER)
                else -> ctx.themeColor(ThemeSlot.DAY_BOX_BORDER)
            }
            val boxBorderPx = (resolveThickness(
                isToday, isWeekend, ctx.config.todayBoxBorderThickness, ctx.config.weekendBoxBorderThickness, generalBoxBorderDp
            ) * density).toInt()
            val headerBorderPx = (resolveThickness(
                isToday, isWeekend, ctx.config.todayHeaderBorderThickness, ctx.config.weekendHeaderBorderThickness, generalHeaderBorderDp
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
            cell.weekGridDayBox.background = GradientDrawable().apply {
                setColor(boxFill)
                if (boxBorderPx > 0) {
                    setStroke(boxBorderPx, boxBorderColor)
                }
            }
            cell.weekGridDayEvents.removeAllViews()
            // Long-press anywhere in the box (header or events area) to add a new event;
            // tapping an event line opens it. The events area fills the box (fillViewport) and
            // gets its own listener, since the ScrollView would otherwise swallow the long-press.
            val addEventOnLongPress = View.OnLongClickListener {
                requireContext().launchNewEventIntent(dayCode)
                true
            }
            cell.weekGridDayBox.setOnLongClickListener(addEventOnLongPress)
            cell.weekGridDayEvents.setOnLongClickListener(addEventOnLongPress)
        }
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

        for (i in 0 until 7) {
            val dayDateTime = weekStartDateTime.plusDays(i)
            val dayCode = Formatter.getDayCodeFromDateTime(dayDateTime)
            val dayStart = Formatter.getDayStartTS(dayCode)
            val dayEnd = Formatter.getDayEndTS(dayCode)
            val container = dayCells[i].weekGridDayEvents
            container.removeAllViews()

            events.asSequence()
                .filter { it.startTS <= dayEnd && it.endTS >= dayStart }
                .sortedWith(compareBy({ !it.getIsAllDay() }, { it.startTS }, { it.title }))
                .forEach { addEventLine(container, it, dimPastEvents, dimCompletedTasks) }
        }
    }

    private fun addEventLine(container: LinearLayout, event: Event, dimPastEvents: Boolean, dimCompletedTasks: Boolean) {
        val ctx = requireContext()
        val line = WeekGridEventBinding.inflate(layoutInflater, container, false)
        val label = if (event.getIsAllDay()) {
            event.title
        } else {
            "${Formatter.getTime(ctx, Formatter.getDateTimeFromTS(event.startTS))} ${event.title}"
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
        line.root.applyThemeFont(ThemeSlot.EVENT_TEXT)
        if (event.isTaskCompleted()) {
            line.root.paintFlags = line.root.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        line.root.setOnClickListener { openEvent(event) }
        container.addView(line.root)
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
