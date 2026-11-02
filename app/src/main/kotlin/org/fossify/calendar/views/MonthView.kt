package org.fossify.calendar.views

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.SparseIntArray
import android.view.View
import org.fossify.calendar.R
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.COLUMN_COUNT
import org.fossify.calendar.helpers.DAY_BOX_THICKNESS_INHERIT
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.ROW_COUNT
import org.fossify.calendar.helpers.REGULAR_DAY_BOX_BORDER_DP
import org.fossify.calendar.helpers.SPECIAL_DAY_FRAME_DP
import org.fossify.calendar.helpers.SPECIAL_DAY_FRAME_GAP_DP
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.calendar.helpers.dayFrameColor
import org.fossify.calendar.helpers.hasDayOff
import org.fossify.calendar.models.DayMonthly
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.MonthViewEvent
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.HIGHER_ALPHA
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.joda.time.DateTime
import org.joda.time.Days
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// used in the Monthly view fragment, 1 view per screen
class MonthView(context: Context, attrs: AttributeSet, defStyle: Int) : View(context, attrs, defStyle) {
    companion object {
        private const val BG_CORNER_RADIUS = 8f
        private const val EVENT_DOT_COLUMN_COUNT = 3
        private const val EVENT_DOT_ROW_COUNT = 1
    }

    private var textPaint: Paint
    private var eventTitlePaint: TextPaint
    private var gridPaint: Paint
    private var circleStrokePaint: Paint
    private var dayBoxBorderPaint: Paint
    private var plusTextPaint: Paint
    private var eventDotPaint: Paint
    private var config = context.config
    private var dayWidth = 0f
    private var dayHeight = 0f
    private var primaryColor = 0
    private var todayColor = 0
    private var todayBoxBorderColor = 0
    private var todayBoxBorderWidth = 0f
    private var dayBoxBorderColor = 0
    private var dayBoxBorderWidth = 0f
    private var weekendBoxBorderColor = 0
    private var weekendBoxBorderWidth = 0f
    private var specialFrameWidth = 0f
    private var specialFrameGap = 0f
    private var backgroundColor = 0
    private var textColor = 0
    private var weekendsTextColor = 0
    private var dayOffFrameColor = 0
    private var dayOffDateTextColor = 0
    private var weekDaysLetterHeight = 0
    private var eventTitleHeight = 0
    private var currDayOfWeek = 0
    private var smallPadding = 0
    private var maxEventsPerDay = 0
    private var horizontalOffset = 0
    private var showWeekNumbers = false
    private var dimPastEvents = true
    private var dimCompletedTasks = true
    private var highlightWeekends = false
    private var isPrintVersion = false
    private var isMonthDayView = false
    private var allEvents = ArrayList<MonthViewEvent>()
    private var bgRectF = RectF()
    private var dayBoxRectF = RectF()
    // per day index: whether it is a day off (休み entry), and its category whole-day frame colour
    private var dayOffs = BooleanArray(0)
    private var dayFrameColors = IntArray(0)
    private var dayHeaderPaint = Paint()
    private var dayTextRect = Rect()
    private var dayLetters = ArrayList<String>()
    private var days = ArrayList<DayMonthly>()
    private var dayVerticalOffsets = SparseIntArray()
    private var selectedDayCoords = Point(-1, -1)

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    init {
        primaryColor = context.getProperPrimaryColor()
        todayColor = context.themeColor(ThemeSlot.TODAY_HIGHLIGHT)
        backgroundColor = context.themeColor(ThemeSlot.BACKGROUND)
        textColor = context.getProperTextColor()
        weekendsTextColor = resolveWeekendsTextColor()
        showWeekNumbers = config.showWeekNumbers
        dimPastEvents = config.dimPastEvents
        dimCompletedTasks = config.dimCompletedTasks
        highlightWeekends = config.highlightWeekends

        smallPadding = resources.displayMetrics.density.toInt()
        val normalTextSize = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_text_size)
        weekDaysLetterHeight = normalTextSize * 2

        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = normalTextSize.toFloat()
            textAlign = Paint.Align.CENTER
            typeface = context.themeTypeface(ThemeSlot.TEXT)
        }

        eventDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        plusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            alpha = 175
            textSize = normalTextSize.toFloat()
            textAlign = Paint.Align.CENTER
            typeface = context.themeTypeface(ThemeSlot.TEXT)
        }

        gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.themeColor(ThemeSlot.GRID_LINES)
        }

        circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = resources.getDimension(R.dimen.circle_stroke_width)
            color = primaryColor
        }

        dayBoxBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        refreshTodayBoxBorder()
        refreshDayBoxBorders()

        val smallerTextSize = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.smaller_text_size)
        eventTitleHeight = smallerTextSize
        eventTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = smallerTextSize.toFloat()
            textAlign = Paint.Align.LEFT
            typeface = context.themeTypeface(ThemeSlot.EVENT_TEXT)
        }

        initWeekDayLetters()
        setupCurrentDayOfWeekIndex()
    }

    fun updateDays(newDays: ArrayList<DayMonthly>, isMonthDayView: Boolean) {
        this.isMonthDayView = isMonthDayView
        days = newDays
        showWeekNumbers = config.showWeekNumbers
        horizontalOffset = context.getWeekNumberWidth()
        refreshTodayBoxBorder()
        refreshDayBoxBorders()
        weekendsTextColor = resolveWeekendsTextColor()
        computeDayMarks()
        initWeekDayLetters()
        setupCurrentDayOfWeekIndex()
        groupAllEvents()
        invalidate()
    }

    private fun groupAllEvents() {
        days.forEach { day ->
            val dayIndexOnMonthView = day.indexOnMonthView

            day.dayEvents.forEach { event ->
                // make sure we properly handle events lasting multiple days and repeating ones
                val validDayEvent = isDayValid(event, day.code)
                val lastEvent = allEvents.lastOrNull { it.id == event.id }
                val notYetAddedOrIsRepeatingEvent = lastEvent == null || lastEvent.endTS <= event.startTS

                // handle overlapping repeating events e.g. an event that lasts 3 days, but repeats every 2 days has a one day overlap
                val canOverlap = event.endTS - event.startTS > event.repeatInterval
                val shouldAddEvent = notYetAddedOrIsRepeatingEvent || canOverlap && (lastEvent.startTS < event.startTS)

                if (shouldAddEvent && !validDayEvent) {
                    val daysCnt = getEventLastingDaysCount(event)

                    val monthViewEvent = MonthViewEvent(
                        id = event.id!!,
                        title = event.title,
                        startTS = event.startTS,
                        endTS = event.endTS,
                        color = event.color,
                        startDayIndex = dayIndexOnMonthView,
                        daysCnt = daysCnt,
                        originalStartDayIndex = dayIndexOnMonthView,
                        isAllDay = event.getIsAllDay(),
                        isPastEvent = event.isPastEvent,
                        isTask = event.isTask(),
                        isTaskCompleted = event.isTaskCompleted(),
                        isAttendeeInviteDeclined = event.isAttendeeInviteDeclined(),
                        isEventCanceled = event.isEventCanceled(),
                        categoryBackgroundColor = event.categoryBackgroundColor,
                        categoryFontFamily = event.categoryFontFamily,
                        categoryFontWeight = event.categoryFontWeight
                    )
                    allEvents.add(monthViewEvent)
                }
            }
        }

        allEvents = allEvents.asSequence().sortedWith(
            compareBy({ -it.daysCnt }, { !it.isAllDay }, { it.startTS }, { it.endTS }, { it.startDayIndex }, { it.title })
        ).toMutableList() as ArrayList<MonthViewEvent>
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        dayVerticalOffsets.clear()
        measureDaySize(canvas)

        if (config.showGrid && !isMonthDayView) {
            drawGrid(canvas)
        }

        addWeekDayLetters(canvas)
        if (showWeekNumbers && days.isNotEmpty()) {
            addWeekNumbers(canvas)
        }

        var curId = 0
        for (y in 0 until ROW_COUNT) {
            for (x in 0 until COLUMN_COUNT) {
                val day = days.getOrNull(curId)
                if (day != null) {
                    val dayNumber = day.value.toString()
                    val textPaint = getTextPaint(day)
                    textPaint.getTextBounds(dayNumber, 0, dayNumber.length, dayTextRect)
                    dayVerticalOffsets.put(day.indexOnMonthView, dayVerticalOffsets[day.indexOnMonthView] + weekDaysLetterHeight)
                    val verticalOffset = dayVerticalOffsets[day.indexOnMonthView]
                    val xPos = x * dayWidth + horizontalOffset
                    val yPos = y * dayHeight + verticalOffset
                    val textY = yPos + textPaint.textSize
                    val xPosCenter = xPos + dayWidth / 2

                    // A weekend / 休み / framed (holiday) day fills its date header (the day number's
                    // row, down to the first event bar) with its frame colour, as the weekly grid does.
                    val headerFill = getDayHeaderFill(day)
                    if (headerFill != null) {
                        // inside the inner frame, flush with it, so strip and frame read as one shape
                        val inset = getSpecialFrameInset(day)
                        val cellTop = y * dayHeight + weekDaysLetterHeight
                        val stripBottom = cellTop + textPaint.textSize * 2 - eventTitleHeight
                        dayHeaderPaint.color = if (day.isThisMonth) headerFill else headerFill.adjustAlpha(MEDIUM_ALPHA)
                        canvas.drawRect(xPos + inset, cellTop + inset, xPos + dayWidth - inset, stripBottom, dayHeaderPaint)
                    }

                    val isDaySelected = selectedDayCoords.x != -1 && x == selectedDayCoords.x && y == selectedDayCoords.y
                    if (isDaySelected) {
                        canvas.drawCircle(
                            xPosCenter,
                            textY - dayTextRect.height() / 2,
                            textPaint.textSize * 0.8f,
                            circleStrokePaint
                        )
                        if (day.isToday) {
                            textPaint.color = textColor
                        }
                    } else if (day.isToday && !isPrintVersion && headerFill == null) {
                        canvas.drawCircle(
                            xPosCenter,
                            textY - dayTextRect.height() / 2,
                            textPaint.textSize * 0.8f,
                            getCirclePaint(day)
                        )
                    }

                    // mark days with a dot for each event
                    if (isMonthDayView && !isDaySelected && !day.isToday && day.dayEvents.isNotEmpty()) {
                        val height = dayTextRect.height() * 1.25f
                        val eventCount = day.dayEvents.size
                        val dotRadius = textPaint.textSize * 0.2f
                        val stepSize = dotRadius * 2.5f
                        val columnCount = EVENT_DOT_COLUMN_COUNT

                        val dayEventsSorted = day.dayEvents
                            .asSequence()
                            .sortedWith(
                                comparator = compareBy({ it.startTS }, { it.endTS }, { it.title })
                            )
                            .distinctBy { it.color }

                        var xDot: Float
                        var yDot = yPos + height + textPaint.textSize / 2
                        var indexInRow: Int

                        val dotCount = dayEventsSorted.count()
                        for ((index, event) in dayEventsSorted.withIndex()) {
                            indexInRow = index % columnCount
                            xDot = xPosCenter + stepSize * (indexInRow - (min(dotCount, columnCount)) / 2)
                            if (dotCount % 2 == 0) { // center even number of dots
                                xDot += stepSize / 2
                            }

                            if (index > 0 && indexInRow == 0) { // next row of dots
                                yDot += stepSize
                            }

                            // Always show a + sign if the event count exceeds columnCount.
                            if (eventCount - 1 != index && index >= columnCount * EVENT_DOT_ROW_COUNT - 1) {
                                plusTextPaint.textSize = stepSize * 1.5f
                                canvas.drawText("+", xDot, yDot + dotRadius * 1.2f, plusTextPaint)
                                break
                            } else {
                                val paint = eventDotPaint.apply { color = event.color }
                                canvas.drawCircle(xDot, yDot, dotRadius, paint)
                            }
                        }
                    }

                    canvas.drawText(dayNumber, xPosCenter, textY, textPaint)
                    dayVerticalOffsets.put(day.indexOnMonthView, (verticalOffset + textPaint.textSize * 2).toInt())
                }
                curId++
            }
        }

        if (!isMonthDayView) {
            for (event in allEvents) {
                drawEvent(event, canvas)
            }

            // last, so the opaque event backgrounds cannot paint over them
            drawDayBoxBorders(canvas)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        // vertical lines
        for (i in 0 until COLUMN_COUNT) {
            var lineX = i * dayWidth
            if (showWeekNumbers) {
                lineX += horizontalOffset
            }
            canvas.drawLine(lineX, 0f, lineX, canvas.height.toFloat(), gridPaint)
        }

        // horizontal lines
        canvas.drawLine(0f, 0f, canvas.width.toFloat(), 0f, gridPaint)
        for (i in 0 until ROW_COUNT) {
            canvas.drawLine(0f, i * dayHeight + weekDaysLetterHeight, canvas.width.toFloat(), i * dayHeight + weekDaysLetterHeight, gridPaint)
        }
        canvas.drawLine(0f, canvas.height.toFloat(), canvas.width.toFloat(), canvas.height.toFloat(), gridPaint)
    }

    private fun addWeekDayLetters(canvas: Canvas) {
        for (i in 0 until COLUMN_COUNT) {
            val xPos = horizontalOffset + (i + 1) * dayWidth - dayWidth / 2
            var weekDayLetterPaint = textPaint
            if (i == currDayOfWeek && !isPrintVersion) {
                // the today highlight alone can be black on black, which took the letter with it
                weekDayLetterPaint = getColoredPaint(getCurrentWeekNumberColor())
            } else if (highlightWeekends && context.isWeekendIndex(i)) {
                weekDayLetterPaint = getColoredPaint(weekendsTextColor)
            }

            // a weekend column's name sits on a header filled with the weekend frame colour, like the
            // weekend days' date headers below it
            if (highlightWeekends && context.isWeekendIndex(i) && !isPrintVersion && !isMonthDayView) {
                val left = horizontalOffset + i * dayWidth
                dayHeaderPaint.color = weekendBoxBorderColor
                canvas.drawRect(left, 0f, left + dayWidth, weekDaysLetterHeight.toFloat(), dayHeaderPaint)
                weekDayLetterPaint = getColoredPaint(weekDayLetterPaint.color.readableOn(weekendBoxBorderColor))
            }
            canvas.drawText(dayLetters[i], xPos, weekDaysLetterHeight * 0.7f, weekDayLetterPaint)
        }
    }

    private fun addWeekNumbers(canvas: Canvas) {
        val weekNumberPaint = Paint(textPaint)

        for (i in 0 until ROW_COUNT) {
            val weekDays = days.subList(i * 7, i * 7 + 7)
            val isCurrentWeek = weekDays.any { it.isToday && !isPrintVersion }
            weekNumberPaint.color = if (isCurrentWeek) getCurrentWeekNumberColor() else textColor

            // fourth day of the week determines the week of the year number
            val weekOfYear = days.getOrNull(i * 7 + 3)?.weekOfYear ?: 1
            val id = "$weekOfYear:"
            val horizontalMarginFactor = 0.5f
            val xPos = horizontalOffset * horizontalMarginFactor
            val yPos = i * dayHeight + weekDaysLetterHeight
            canvas.drawText(id, xPos, yPos + textPaint.textSize, weekNumberPaint)
        }
    }

    // Variant C (see helpers/DayMarks), in three layers:
    //  1. one thin yellow grid round every day and weekday header, each line drawn once, so two
    //     neighbours never fight over a shared edge;
    //  2. a weekend / 休み / framed (holiday) day's thin coloured frame, a little INSIDE its grid box,
    //     so the yellow line and the coloured frame both stay visible (its date strip is filled in
    //     onDraw);
    //  3. today's thick border (TODAY_BOX_BORDER + the today thickness, shared with 週) on top.
    private fun drawDayBoxBorders(canvas: Canvas) {
        if (isPrintVersion) {
            return
        }

        // 1. the shared grid
        if (dayBoxBorderWidth > 0f) {
            dayBoxBorderPaint.strokeWidth = dayBoxBorderWidth
            dayBoxBorderPaint.color = dayBoxBorderColor
            val half = dayBoxBorderWidth / 2
            val gridLeft = horizontalOffset + half
            val gridRight = canvas.width - half
            val gridBottom = weekDaysLetterHeight + ROW_COUNT * dayHeight
            for (column in 0..COLUMN_COUNT) {
                val x = (horizontalOffset + column * dayWidth).coerceIn(gridLeft, gridRight)
                canvas.drawLine(x, 0f, x, gridBottom, dayBoxBorderPaint)
            }
            canvas.drawLine(horizontalOffset.toFloat(), half, canvas.width.toFloat(), half, dayBoxBorderPaint)
            for (row in 0..ROW_COUNT) {
                val y = (weekDaysLetterHeight + row * dayHeight).coerceAtMost(canvas.height - half)
                canvas.drawLine(horizontalOffset.toFloat(), y, canvas.width.toFloat(), y, dayBoxBorderPaint)
            }
        }

        // 2. the special days' inner frames
        for (index in days.indices) {
            val day = days[index]
            val color = getSpecialDayColor(day) ?: continue
            val shown = if (day.isThisMonth) color else color.adjustAlpha(MEDIUM_ALPHA)
            val inset = getSpecialFrameInset(day)
            val left = index % COLUMN_COUNT * dayWidth + horizontalOffset
            val top = index / COLUMN_COUNT * dayHeight + weekDaysLetterHeight
            val half = specialFrameWidth / 2
            dayBoxRectF.set(left + inset + half, top + inset + half, left + dayWidth - inset - half, top + dayHeight - inset - half)
            dayBoxBorderPaint.strokeWidth = specialFrameWidth
            dayBoxBorderPaint.color = shown
            canvas.drawRect(dayBoxRectF, dayBoxBorderPaint)
        }

        // 3. today, inside its own grid box and over the grid lines round it
        val todayIndex = days.indexOfFirst { it.isToday }
        if (todayIndex >= 0 && todayBoxBorderWidth > 0f) {
            val left = todayIndex % COLUMN_COUNT * dayWidth + horizontalOffset
            val top = todayIndex / COLUMN_COUNT * dayHeight + weekDaysLetterHeight
            val half = todayBoxBorderWidth / 2
            dayBoxRectF.set(left + half, top + half, left + dayWidth - half, top + dayHeight - half)
            dayBoxBorderPaint.strokeWidth = todayBoxBorderWidth
            dayBoxBorderPaint.color = if (days[todayIndex].isThisMonth) todayBoxBorderColor else todayBoxBorderColor.adjustAlpha(MEDIUM_ALPHA)
            canvas.drawRect(dayBoxRectF, dayBoxBorderPaint)
        }
    }

    // A weekend / 休み / framed (holiday) day's colour: the category frame colour first, else the
    // weekend box border colour. Null for a regular day.
    private fun getSpecialDayColor(day: DayMonthly): Int? {
        val frameColor = dayFrameColors.getOrElse(day.indexOnMonthView) { THEME_UNSET }
        return when {
            frameColor != THEME_UNSET -> frameColor
            dayOffs.getOrElse(day.indexOnMonthView) { false } -> dayOffFrameColor
            isWeekendStyled(day) -> weekendBoxBorderColor
            else -> null
        }
    }

    // How far a special day's inner frame sits inside its cell: past the cell's own outline (the grid
    // line, or today's thick border) plus a small gap.
    private fun getSpecialFrameInset(day: DayMonthly): Float {
        val outline = if (day.isToday && todayBoxBorderWidth > 0f) todayBoxBorderWidth else dayBoxBorderWidth
        return outline + specialFrameGap
    }

    private fun refreshDayBoxBorders() {
        val density = resources.displayMetrics.density
        // A regular day gets a thin box in the accent (yellow on our palette). The weekly grid's own
        // day box border slot defaults to the dim grid-line colour, which reads as no box at all here.
        dayBoxBorderColor = context.regularDayBoxBorderColor()
        dayBoxBorderWidth = max(REGULAR_DAY_BOX_BORDER_DP * density, 1f)
        specialFrameWidth = SPECIAL_DAY_FRAME_DP * density
        specialFrameGap = SPECIAL_DAY_FRAME_GAP_DP * density
        weekendBoxBorderColor = context.themeColor(ThemeSlot.WEEKEND_BOX_BORDER)
        dayOffFrameColor = context.themeColor(ThemeSlot.DAY_OFF_FRAME)
        dayOffDateTextColor = context.themeColor(ThemeSlot.DAY_OFF_DATE_TEXT)
        val weekendDp = if (config.weekendBoxBorderThickness != DAY_BOX_THICKNESS_INHERIT) {
            config.weekendBoxBorderThickness
        } else {
            config.dayBoxBorderThickness
        }
        weekendBoxBorderWidth = weekendDp * density
    }

    // Same day/event matching as the event bars: an entry ending exactly at a day's midnight does
    // not belong to that day.
    private fun computeDayMarks() {
        dayOffs = BooleanArray(days.size)
        dayFrameColors = IntArray(days.size) { THEME_UNSET }
        days.forEachIndexed { index, day ->
            val dayEvents = day.dayEvents.filter { !isDayValid(it, day.code) }
            dayOffs[index] = dayEvents.hasDayOff()
            dayFrameColors[index] = dayEvents.dayFrameColor()
        }
    }

    // The date header fill of a weekend / 休み / framed day: its colour (today included). Null for a
    // regular day, in print mode and in the compact 月+日 grid.
    private fun getDayHeaderFill(day: DayMonthly): Int? =
        if (isPrintVersion || isMonthDayView) null else getSpecialDayColor(day)

    // Weekend days, when weekends are highlighted. Days off (休み) have their own colours.
    private fun isWeekendStyled(day: DayMonthly) = highlightWeekends && day.isWeekend

    // The date colour on a special day's filled strip: the 休み date colour on a day off (unless a
    // framed category, e.g. a holiday, outranks it), else the weekend text colour.
    private fun getSpecialDayTextColor(day: DayMonthly): Int {
        val isFramed = dayFrameColors.getOrElse(day.indexOnMonthView) { THEME_UNSET } != THEME_UNSET
        val isDayOff = dayOffs.getOrElse(day.indexOnMonthView) { false }
        return if (isDayOff && !isFramed) dayOffDateTextColor else weekendsTextColor
    }

    // The weekend highlight slot is the weekly grid's weekend HEADER BACKGROUND, so on the black
    // palette it is black — as a text colour it made every weekend letter and number vanish. Use it
    // only while it stays visible on the background; otherwise take the weekend text slot.
    private fun resolveWeekendsTextColor(): Int {
        val highlight = config.highlightWeekendsColor
        return if (highlight.isDistinguishableFrom(backgroundColor)) {
            highlight
        } else {
            context.themeColor(ThemeSlot.WEEKEND_TEXT)
        }
    }

    private fun refreshTodayBoxBorder() {
        todayBoxBorderColor = context.themeColor(ThemeSlot.TODAY_BOX_BORDER)
        val thicknessDp = if (config.todayBoxBorderThickness != DAY_BOX_THICKNESS_INHERIT) {
            config.todayBoxBorderThickness
        } else {
            config.dayBoxBorderThickness
        }
        todayBoxBorderWidth = thicknessDp * resources.displayMetrics.density
    }

    // The current week's number used to be painted in the today highlight alone, so the whole label
    // ("39:") vanished whenever that slot sat close to the background - a black today highlight on a
    // black background took the row marker with it. Prefer the today highlight, fall back to the
    // today box border accent, and finally to the regular text color, so the number is never lost.
    private fun getCurrentWeekNumberColor(): Int {
        return listOf(todayColor, todayBoxBorderColor).firstOrNull { it.isDistinguishableFrom(backgroundColor) } ?: textColor
    }

    private fun measureDaySize(canvas: Canvas) {
        dayWidth = (canvas.width - horizontalOffset) / 7f
        dayHeight = (canvas.height - weekDaysLetterHeight) / ROW_COUNT.toFloat()
        val availableHeightForEvents = dayHeight.toInt() - weekDaysLetterHeight
        maxEventsPerDay = availableHeightForEvents / eventTitleHeight
    }

    private fun drawEvent(event: MonthViewEvent, canvas: Canvas) {
        var verticalOffset = 0
        for (i in 0 until min(event.daysCnt, 7 - event.startDayIndex % 7)) {
            verticalOffset = max(verticalOffset, dayVerticalOffsets[event.startDayIndex + i])
        }
        val xPos = event.startDayIndex % 7 * dayWidth + horizontalOffset
        val yPos = (event.startDayIndex / 7) * dayHeight
        val xPosCenter = xPos + dayWidth / 2

        if (verticalOffset - eventTitleHeight * 2 > dayHeight) {
            val paint = getTextPaint(days[event.startDayIndex])
            paint.color = textColor
            canvas.drawText("...", xPosCenter, yPos + verticalOffset - eventTitleHeight / 2, paint)
            return
        }

        // event background rectangle
        val backgroundY = yPos + verticalOffset
        val bgLeft = xPos + smallPadding
        val bgTop = backgroundY + smallPadding - eventTitleHeight
        var bgRight = xPos - smallPadding + dayWidth * event.daysCnt
        val bgBottom = backgroundY + smallPadding * 2
        if (bgRight > canvas.width.toFloat()) {
            bgRight = canvas.width.toFloat() - smallPadding
            val newStartDayIndex = (event.startDayIndex / 7 + 1) * 7
            if (newStartDayIndex < 42) {
                val newEvent = event.copy(startDayIndex = newStartDayIndex, daysCnt = event.daysCnt - (newStartDayIndex - event.startDayIndex))
                drawEvent(newEvent, canvas)
            }
        }

        bgRectF.set(bgLeft, bgTop, bgRight, bgBottom)
        canvas.drawRoundRect(bgRectF, BG_CORNER_RADIUS, BG_CORNER_RADIUS, getEventBackgroundColor(event))

        val specificEventTitlePaint = getEventTitlePaint(event)
        var taskIconWidth = 0
        if (event.isTask) {
            val taskIcon = resources.getColoredDrawableWithColor(R.drawable.ic_task_vector, specificEventTitlePaint.color).mutate()
            val taskIconY = yPos.toInt() + verticalOffset - eventTitleHeight + smallPadding * 2
            taskIcon.setBounds(xPos.toInt() + smallPadding * 2, taskIconY, xPos.toInt() + eventTitleHeight + smallPadding * 2, taskIconY + eventTitleHeight)
            taskIcon.draw(canvas)
            taskIconWidth += eventTitleHeight + smallPadding
        }

        drawEventTitle(event, canvas, xPos + taskIconWidth, yPos + verticalOffset, bgRight - bgLeft - smallPadding - taskIconWidth, specificEventTitlePaint)

        for (i in 0 until min(event.daysCnt, 7 - event.startDayIndex % 7)) {
            dayVerticalOffsets.put(event.startDayIndex + i, verticalOffset + eventTitleHeight + smallPadding * 2)
        }
    }

    private fun drawEventTitle(event: MonthViewEvent, canvas: Canvas, x: Float, y: Float, availableWidth: Float, paint: Paint) {
        // measured with the paint it is drawn with, so a category font cuts at the right place
        val ellipsized = TextUtils.ellipsize(event.title, TextPaint(paint), availableWidth - smallPadding, TextUtils.TruncateAt.END)
        canvas.drawText(event.title, 0, ellipsized.length, x + smallPadding * 2, y, paint)
    }

    private fun getTextPaint(startDay: DayMonthly): Paint {
        val headerFill = getDayHeaderFill(startDay)
        var paintColor = when {
            headerFill != null -> getSpecialDayTextColor(startDay).readableOn(headerFill)
            !isPrintVersion && startDay.isToday -> todayColor.getContrastColor()
            isWeekendStyled(startDay) -> weekendsTextColor
            else -> textColor
        }

        if (!startDay.isThisMonth) {
            paintColor = paintColor.adjustAlpha(MEDIUM_ALPHA)
        }

        return getColoredPaint(paintColor)
    }

    private fun getColoredPaint(color: Int): Paint {
        val curPaint = Paint(textPaint)
        curPaint.color = color
        return curPaint
    }

    private fun getEventBackgroundColor(event: MonthViewEvent): Paint {
        var paintColor = if (event.hasCategoryBackground()) event.categoryBackgroundColor else event.color

        val adjustAlpha = when {
            event.isTask -> dimCompletedTasks && event.isTaskCompleted
            else -> dimPastEvents && event.isPastEvent && !isPrintVersion
        }

        if (adjustAlpha) {
            paintColor = paintColor.adjustAlpha(MEDIUM_ALPHA)
        }

        return getColoredPaint(paintColor)
    }

    // A category with a background draws its title in its own colour, as the weekly grid does; the
    // category font family + weight apply too. Its size does not: the bars have a fixed row height.
    private fun getEventTitlePaint(event: MonthViewEvent): Paint {
        var paintColor = if (event.hasCategoryBackground()) event.color else event.color.getContrastColor()
        val adjustAlpha = when {
            event.isTask -> dimCompletedTasks && event.isTaskCompleted
            else -> dimPastEvents && event.isPastEvent && !isPrintVersion
        }

        if (adjustAlpha) {
            paintColor = paintColor.adjustAlpha(HIGHER_ALPHA)
        }

        val curPaint = Paint(eventTitlePaint)
        curPaint.color = paintColor
        curPaint.isStrikeThruText = event.shouldStrikeThrough()
        if (event.categoryFontFamily.isNotEmpty() || event.categoryFontWeight > 0) {
            curPaint.typeface = context.themeTypeface(event.categoryFontFamily, event.categoryFontWeight)
        }
        return curPaint
    }

    private fun getCirclePaint(day: DayMonthly): Paint {
        val curPaint = Paint(textPaint)
        var paintColor = todayColor
        if (!day.isThisMonth) {
            paintColor = paintColor.adjustAlpha(MEDIUM_ALPHA)
        }
        curPaint.color = paintColor
        return curPaint
    }

    private fun initWeekDayLetters() {
        dayLetters = context.withFirstDayOfWeekToFront(
            context.resources.getStringArray(org.fossify.commons.R.array.week_days_short).toList()
        )
    }

    private fun setupCurrentDayOfWeekIndex() {
        if (days.firstOrNull { it.isToday && it.isThisMonth } == null) {
            currDayOfWeek = -1
            return
        }

        currDayOfWeek = context.getProperDayIndexInWeek(DateTime())
    }

    // take into account cases when an event starts on the previous screen, subtract those days
    private fun getEventLastingDaysCount(event: Event): Int {
        val startDateTime = Formatter.getDateTimeFromTS(event.startTS)
        val endDateTime = Formatter.getDateTimeFromTS(event.endTS)
        val code = days.first().code
        val screenStartDateTime = Formatter.getDateTimeFromCode(code).toLocalDate()
        var eventStartDateTime = Formatter.getDateTimeFromTS(startDateTime.seconds()).toLocalDate()
        val eventEndDateTime = Formatter.getDateTimeFromTS(endDateTime.seconds()).toLocalDate()
        val diff = Days.daysBetween(screenStartDateTime, eventStartDateTime).days
        if (diff < 0) {
            eventStartDateTime = screenStartDateTime
        }

        val isMidnight = Formatter.getDateTimeFromTS(endDateTime.seconds()) == Formatter.getDateTimeFromTS(endDateTime.seconds()).withTimeAtStartOfDay()
        val numDays = Days.daysBetween(eventStartDateTime, eventEndDateTime).days
        val daysCnt = if (numDays == 1 && isMidnight) 0 else numDays
        return daysCnt + 1
    }

    private fun isDayValid(event: Event, code: String): Boolean {
        val date = Formatter.getDateTimeFromCode(code)
        return event.startTS != event.endTS && Formatter.getDateTimeFromTS(event.endTS) == Formatter.getDateTimeFromTS(date.seconds()).withTimeAtStartOfDay()
    }

    fun togglePrintMode() {
        isPrintVersion = !isPrintVersion
        textColor = if (isPrintVersion) {
            resources.getColor(org.fossify.commons.R.color.theme_light_text_color, null)
        } else {
            context.getProperTextColor()
        }

        textPaint.color = textColor
        gridPaint.color = if (isPrintVersion) textColor.adjustAlpha(LOWER_ALPHA) else context.themeColor(ThemeSlot.GRID_LINES)
        invalidate()
        initWeekDayLetters()
    }

    fun updateCurrentlySelectedDay(x: Int, y: Int) {
        selectedDayCoords = Point(x, y)
        invalidate()
    }
}
