package org.fossify.calendar.extensions

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.icu.text.TimeZoneNames
import android.icu.util.ULocale
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.text.style.TabStopSpan
import android.widget.TextView
import org.fossify.calendar.helpers.EVENT_TIME_FORMAT_JAPANESE
import org.fossify.calendar.models.Event
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Multi-zone events (a flight that leaves Prague and lands in Istanbul, a Tokyo meeting seen from
// Prague): the extra lines the day view, the event list and the reminder show under the main
// start–end, the editor's summary line, and the grid marker. The main line always stays on the
// device clock; these lines are added only when an event's own zone(s) read differently from it.
// All timestamps are the calendar's epoch-seconds.

/** One zone line: 発 プラハ ‹time› ⌈CET⌉ 時差… 翌日. [diff] and [dayShift] are null when there is nothing to say. */
class ZoneLine(
    val label: String,
    val city: String,
    val time: String,
    val code: String,
    val diff: String?,
    val dayShift: String?,
)

private val ICU_NAMES_JA: TimeZoneNames by lazy { TimeZoneNames.getInstance(ULocale.JAPANESE) }
private val ICU_NAMES_DEFAULT: TimeZoneNames by lazy { TimeZoneNames.getInstance(ULocale.getDefault()) }
private val ICU_NAMES_EN: TimeZoneNames by lazy { TimeZoneNames.getInstance(ULocale.ENGLISH) }

/** The zone's city in the display language (プラハ / Prague), falling back to the id's last segment. */
fun zoneCityName(zoneId: String, japanese: Boolean): String {
    val names = if (japanese) ICU_NAMES_JA else ICU_NAMES_DEFAULT
    val city = try {
        names.getExemplarLocationName(zoneId)
    } catch (e: Exception) {
        null
    }
    return city ?: zoneId.substringAfterLast('/').replace('_', ' ')
}

/** Every searchable name of the zone: its English, device-language and Japanese city names. */
fun zoneSearchNames(zoneId: String): List<String> = listOf(ICU_NAMES_EN, ICU_NAMES_DEFAULT, ICU_NAMES_JA).mapNotNull {
    try {
        it.getExemplarLocationName(zoneId)
    } catch (e: Exception) {
        null
    }
}.distinct()

/** The zone's UTC offset at the instant, in seconds. */
fun zoneOffsetSeconds(zoneId: String, tsSeconds: Long): Int =
    zoneOf(zoneId).rules.getOffset(Instant.ofEpochSecond(tsSeconds)).totalSeconds

/** UTC+1, UTC+5:30, UTC−3 — the offset at the instant. */
fun utcOffsetLabel(zoneId: String, tsSeconds: Long): String {
    val offset = zoneOffsetSeconds(zoneId, tsSeconds)
    val sign = if (offset < 0) "−" else "+"
    val minutes = abs(offset) / 60
    return if (minutes % 60 == 0) "UTC$sign${minutes / 60}" else "UTC$sign${minutes / 60}:${"%02d".format(minutes % 60)}"
}

/** プラハ · CET · UTC+1 — the editor's and the picker's zone description. */
fun zoneDescription(zoneId: String, tsSeconds: Long, japanese: Boolean) =
    "${zoneCityName(zoneId, japanese)} · ${zoneCode(zoneId, tsSeconds * 1000L)} · ${utcOffsetLabel(zoneId, tsSeconds)}"

/** A zone offset difference: 時差＋二時間 / 時差−五時間半, or +2h / −5h30m; null when zero. */
fun zoneDifferenceText(diffSeconds: Int, japanese: Boolean): String? {
    if (diffSeconds == 0) {
        return null
    }
    val minutes = abs(diffSeconds) / 60
    return if (japanese) {
        val sign = if (diffSeconds > 0) "＋" else "−"
        "時差$sign" + (minutes * 60).toJapaneseDurationString().removePrefix("（").removeSuffix("）")
    } else {
        val sign = if (diffSeconds > 0) "+" else "−"
        val hours = minutes / 60
        val rest = minutes % 60
        sign + when {
            hours == 0 -> "${rest}m"
            rest == 0 -> "${hours}h"
            else -> "${hours}h${rest}m"
        }
    }
}

/** 2h 25m / 45m / 3h — a duration for the plain (non-kanji) formats. */
fun plainDurationText(seconds: Long): String {
    val minutes = max(0L, seconds) / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "${rest}m"
        rest == 0L -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

/** 翌日 / 前日 / ＋2日, or +1 day / −1 day; null when the dates match. */
fun dayShiftText(days: Long, japanese: Boolean): String? = when {
    days == 0L -> null
    japanese && days == 1L -> "翌日"
    japanese && days == -1L -> "前日"
    japanese -> (if (days > 0) "＋" else "−") + "${abs(days)}日"
    abs(days) == 1L -> (if (days > 0) "+" else "−") + "1 day"
    else -> (if (days > 0) "+" else "−") + "${abs(days)} days"
}

private fun localDaysBetween(startTS: Long, startZone: String, endTS: Long, endZone: String): Long {
    val startDate = Instant.ofEpochSecond(startTS).atZone(zoneOf(startZone)).toLocalDate()
    val endDate = Instant.ofEpochSecond(endTS).atZone(zoneOf(endZone)).toLocalDate()
    return ChronoUnit.DAYS.between(startDate, endDate)
}

/** True when the event's own zone(s) read differently from the device clock at its start or end. */
fun eventZonesDiffer(startTS: Long, endTS: Long, startZone: String, endZone: String, isAllDay: Boolean): Boolean {
    if (isAllDay) {
        return false
    }
    val device = ZoneId.systemDefault().id
    return zoneOffsetSeconds(startZone, startTS) != zoneOffsetSeconds(device, startTS) ||
        zoneOffsetSeconds(endZone, endTS) != zoneOffsetSeconds(device, endTS)
}

/** True when start and end sit in zones that read differently — a flight. */
fun isTwoZoneEvent(startTS: Long, endTS: Long, startZone: String, endZone: String, isAllDay: Boolean) =
    !isAllDay && startZone != endZone && zoneOffsetSeconds(startZone, endTS) != zoneOffsetSeconds(endZone, endTS)

/**
 * The zone lines for an event, or an empty list when its zones read like the device clock.
 * Two zones → a 発 line and a 着 line (the 着 line carries the time difference to the departure zone
 * and 翌日 when it lands on another local date). One foreign zone → a single 現地 line with the local
 * start–end and the difference to the device clock.
 */
fun eventZoneLines(
    startTS: Long,
    endTS: Long,
    startZone: String,
    endZone: String,
    isAllDay: Boolean,
    format: String,
): List<ZoneLine> {
    if (!eventZonesDiffer(startTS, endTS, startZone, endZone, isAllDay)) {
        return emptyList()
    }
    val japanese = format == EVENT_TIME_FORMAT_JAPANESE
    return if (isTwoZoneEvent(startTS, endTS, startZone, endZone, isAllDay)) {
        val diff = zoneOffsetSeconds(endZone, endTS) - zoneOffsetSeconds(startZone, endTS)
        listOf(
            ZoneLine(
                label = if (japanese) "発" else "Dep",
                city = zoneCityName(startZone, japanese),
                time = formatEventClock(startTS, format, startZone),
                code = zoneCode(startZone, startTS * 1000L),
                diff = null,
                dayShift = null,
            ),
            ZoneLine(
                label = if (japanese) "着" else "Arr",
                city = zoneCityName(endZone, japanese),
                time = formatEventClock(endTS, format, endZone),
                code = zoneCode(endZone, endTS * 1000L),
                diff = zoneDifferenceText(diff, japanese),
                dayShift = dayShiftText(localDaysBetween(startTS, startZone, endTS, endZone), japanese),
            ),
        )
    } else {
        val device = ZoneId.systemDefault().id
        val diff = zoneOffsetSeconds(startZone, startTS) - zoneOffsetSeconds(device, startTS)
        val time = if (startTS == endTS) {
            formatEventClock(startTS, format, startZone)
        } else {
            formatEventClock(startTS, format, startZone) + eventTimeSeparator(format) + formatEventClock(endTS, format, startZone)
        }
        listOf(
            ZoneLine(
                label = if (japanese) "現地" else "Local",
                city = zoneCityName(startZone, japanese),
                time = time,
                code = zoneCode(startZone, startTS * 1000L),
                diff = zoneDifferenceText(diff, japanese),
                dayShift = dayShiftText(localDaysBetween(startTS, device, startTS, startZone), japanese),
            )
        )
    }
}

fun Event.zoneLines(format: String) =
    eventZoneLines(startTS, endTS, getTimeZoneString(), getEndTimeZoneString(), getIsAllDay(), format)

/** The grid marker in front of a title: ✈ for a two-zone event, 🌐 for one in a foreign zone, else nothing. */
fun gridZoneMarker(startTS: Long, endTS: Long, startZone: String, endZone: String, isAllDay: Boolean): String = when {
    isTwoZoneEvent(startTS, endTS, startZone, endZone, isAllDay) -> "✈︎ "
    eventZonesDiffer(startTS, endTS, startZone, endZone, isAllDay) -> "🌐 "
    else -> ""
}

fun Event.gridZoneMarker() = gridZoneMarker(startTS, endTS, getTimeZoneString(), getEndTimeZoneString(), getIsAllDay())

/** Plain text of the lines, for a notification. */
fun List<ZoneLine>.toPlainText() = joinToString("\n") { line ->
    listOfNotNull("${line.label} ${line.city}", line.time, line.code, line.diff, line.dayShift).joinToString("  ")
}

/**
 * Show the lines in [this] text view, columns aligned with tab stops: label + city, time, then the
 * outlined zone tag and the extras. Measured with the view's own paint, so call it after the font is
 * applied. Hides the view when there are no lines. Falls back to plain spacing when the columns would
 * not fit [availableWidth] (the folded screen), so a long line wraps instead of tabbing off the edge.
 */
fun TextView.showZoneLines(lines: List<ZoneLine>, textColor: Int, tagColor: Int, availableWidth: Int) {
    if (lines.isEmpty()) {
        visibility = android.view.View.GONE
        text = ""
        return
    }
    visibility = android.view.View.VISIBLE
    setTextColor(textColor)

    val density = resources.displayMetrics.density
    val gap = 10 * density
    val tagSpan = { ZoneTagSpan(tagColor, density) }
    val measure = Paint(paint)
    val tagProbe = ZoneTagSpan(tagColor, density)
    val col1 = lines.maxOf { measure.measureText("${it.label} ${it.city}") } + gap
    val col2 = col1 + lines.maxOf { measure.measureText(it.time) } + gap
    val tailWidth = lines.maxOf { line ->
        tagProbe.getSize(measure, line.code, 0, line.code.length, null) +
            listOfNotNull(line.diff, line.dayShift).sumOf { measure.measureText("  $it").toDouble() }.toFloat()
    }
    val useTabs = col2 + tailWidth <= availableWidth

    val builder = SpannableStringBuilder()
    lines.forEachIndexed { index, line ->
        if (index > 0) builder.append("\n")
        val labelStart = builder.length
        builder.append(line.label)
        builder.setSpan(StyleSpan(Typeface.BOLD), labelStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.append(" ").append(line.city)
        builder.append(if (useTabs) "\t" else "  ")
        builder.append(line.time)
        builder.append(if (useTabs) "\t" else "  ")
        val codeStart = builder.length
        builder.append(line.code)
        builder.setSpan(tagSpan(), codeStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        line.diff?.let { builder.append("  ").append(it) }
        line.dayShift?.let {
            builder.append("  ")
            val shiftStart = builder.length
            builder.append(it)
            builder.setSpan(ForegroundColorSpan(tagColor), shiftStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(StyleSpan(Typeface.BOLD), shiftStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    if (useTabs) {
        builder.setSpan(TabStopSpan.Standard(col1.roundToInt()), 0, builder.length, Spanned.SPAN_PARAGRAPH)
        builder.setSpan(TabStopSpan.Standard(col2.roundToInt()), 0, builder.length, Spanned.SPAN_PARAGRAPH)
    }
    text = builder
}

/** A zone code drawn as a small outlined tag (thin accent frame, accent text), centred on the line. */
class ZoneTagSpan(private val color: Int, density: Float) : ReplacementSpan() {
    private val stroke = 1f * density
    private val padH = 4f * density
    private val padV = 1.5f * density
    private val radius = 3f * density
    private val marginH = 2f * density

    private fun tagPaint(base: Paint) = Paint(base).apply {
        textSize = base.textSize * 0.78f
        typeface = Typeface.create(base.typeface, Typeface.BOLD)
        color = this@ZoneTagSpan.color
        isAntiAlias = true
    }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val textWidth = tagPaint(paint).measureText(text ?: "", start, end)
        return (textWidth + 2 * padH + 2 * stroke + 2 * marginH).roundToInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val tp = tagPaint(paint)
        val textWidth = tp.measureText(text ?: "", start, end)
        // centre the tag on the surrounding text's x-height band
        val mainCentre = y + (paint.ascent() + paint.descent()) / 2f
        val baseline = mainCentre - (tp.ascent() + tp.descent()) / 2f
        val rect = RectF(
            x + marginH + stroke / 2,
            max(top.toFloat(), baseline + tp.ascent() - padV),
            x + marginH + stroke * 1.5f + textWidth + 2 * padH,
            baseline + tp.descent() + padV,
        )
        val frame = Paint(tp).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        canvas.drawRoundRect(rect, radius, radius, frame)
        canvas.drawText(text ?: "", start, end, x + marginH + stroke + padH, baseline, tp)
    }
}

/** The dim-gold role's default: the text colour at ~78 % brightness (#FFFF00 → #C8C800). */
fun dimmedColor(color: Int, factor: Float = 0.784f): Int {
    val a = color ushr 24 and 0xFF
    val r = ((color shr 16 and 0xFF) * factor).roundToInt()
    val g = ((color shr 8 and 0xFF) * factor).roundToInt()
    val b = ((color and 0xFF) * factor).roundToInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/** Width a list row's zone lines can use: the screen less the row's margins and colour bar. */
fun Context.zoneLinesAvailableWidth(): Int {
    val density = resources.displayMetrics.density
    return (resources.displayMetrics.widthPixels - 56 * density).roundToInt()
}

/**
 * The day view's travel banner: shown while the device clock reads differently from the home zone
 * ("" = off). 現在地 イスタンブール ⌈TRT⌉ ・ 自宅 プラハ ⌈CET⌉ 午前十一時 時差−二時間 — the home clock
 * right now and how far home is from here — inside a thin rounded frame.
 */
fun TextView.showTravelBanner(homeZone: String, format: String, textColor: Int, tagColor: Int) {
    val now = System.currentTimeMillis() / 1000L
    val device = ZoneId.systemDefault().id
    if (homeZone.isEmpty() || zoneOffsetSeconds(homeZone, now) == zoneOffsetSeconds(device, now)) {
        visibility = android.view.View.GONE
        return
    }
    visibility = android.view.View.VISIBLE
    setTextColor(textColor)
    val japanese = format == EVENT_TIME_FORMAT_JAPANESE
    val density = resources.displayMetrics.density
    val builder = SpannableStringBuilder()
    fun tag(code: String) {
        val start = builder.length
        builder.append(code)
        builder.setSpan(ZoneTagSpan(tagColor, density), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    fun bold(text: String) {
        val start = builder.length
        builder.append(text)
        builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    bold(if (japanese) "現在地" else context.getString(org.fossify.calendar.R.string.travel_banner_here))
    builder.append(" ").append(zoneCityName(device, japanese)).append(" ")
    tag(zoneCode(device, now * 1000L))
    builder.append(if (japanese) "  ・  " else "  ·  ")
    bold(if (japanese) "自宅" else context.getString(org.fossify.calendar.R.string.travel_banner_home))
    builder.append(" ").append(zoneCityName(homeZone, japanese)).append(" ")
    tag(zoneCode(homeZone, now * 1000L))
    builder.append(" ").append(formatEventClock(now, format, homeZone))
    zoneDifferenceText(zoneOffsetSeconds(homeZone, now) - zoneOffsetSeconds(device, now), japanese)?.let {
        builder.append("  ").append(it)
    }
    text = builder
    background = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = 8 * density
        setStroke((1 * density).roundToInt(), textColor)
    }
}

/**
 * An unfilled outlined pill (the black-yellow action look, as commons' Pill.kt from sk12 draws it):
 * a [strokeDp] accent frame, fully round unless [radiusDp] says otherwise, the background showing
 * through, and an accent ripple at 30 %. 1.25 dp is a regular action, 3 dp the main one.
 */
fun Context.outlinedPill(accent: Int, strokeDp: Float = 1.25f, radiusDp: Float = 100f): android.graphics.drawable.Drawable {
    val density = resources.displayMetrics.density
    val shape = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = radiusDp * density
        setStroke((strokeDp * density).roundToInt().coerceAtLeast(1), accent)
        setColor(android.graphics.Color.TRANSPARENT)
    }
    val mask = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = radiusDp * density
        setColor(android.graphics.Color.WHITE)
    }
    val ripple = android.content.res.ColorStateList.valueOf((accent and 0x00FFFFFF) or (0x4D shl 24))
    return android.graphics.drawable.RippleDrawable(ripple, shape, mask)
}

/** A thin rounded frame that groups a block (not an action — no ripple). */
fun Context.groupFrame(color: Int, strokeDp: Float = 1f, radiusDp: Float = 12f): android.graphics.drawable.Drawable {
    val density = resources.displayMetrics.density
    return android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = radiusDp * density
        setStroke((strokeDp * density).roundToInt().coerceAtLeast(1), color)
        setColor(android.graphics.Color.TRANSPARENT)
    }
}
