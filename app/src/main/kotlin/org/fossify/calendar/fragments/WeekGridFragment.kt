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
import org.fossify.calendar.R
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.databinding.FragmentWeekGridBinding
import org.fossify.calendar.databinding.WeekGridDayBinding
import org.fossify.calendar.databinding.WeekGridEventBinding
import org.fossify.calendar.extensions.ThemeSlot
import org.fossify.calendar.extensions.applyCategoryFont
import org.fossify.calendar.extensions.applyThemeFont
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.formatEventClock
import org.fossify.calendar.extensions.launchNewEventIntent
import org.fossify.calendar.extensions.launchNewTaskIntent
import org.fossify.calendar.extensions.themeColor
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_CENTER
import org.fossify.calendar.helpers.DAY_BOX_ALIGN_START
import org.fossify.calendar.helpers.EVENT_ID
import org.fossify.calendar.helpers.EVENT_OCCURRENCE_TS
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.formatDayBoxHeader
import org.fossify.calendar.helpers.IS_TASK_COMPLETED
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
                .forEach { addEventLine(container, it, dayDateTime, dimPastEvents, dimCompletedTasks) }
        }
    }

    private fun addEventLine(container: LinearLayout, event: Event, dayDateTime: DateTime, dimPastEvents: Boolean, dimCompletedTasks: Boolean) {
        val ctx = requireContext()
        val line = WeekGridEventBinding.inflate(layoutInflater, container, false)
        val label = if (event.getIsAllDay()) {
            event.title
        } else {
            "${formatEventClock(event.startTS, ctx.config.eventTimeFormat)} ${event.title}"
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
