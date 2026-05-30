package org.fossify.calendar.extensions

import android.icu.text.SimpleDateFormat
import android.icu.text.TimeZoneNames
import android.icu.util.TimeZone
import android.icu.util.ULocale
import org.fossify.calendar.helpers.EVENT_TIME_FORMAT_JAPANESE
import java.time.Instant
import java.time.ZoneId
import java.util.Date

// Event time display for the box-grid week view (start only) and the day view
// (start ー end （duration）). The default is the Sino-Japanese clock from shiroikuma-denwa
// (e.g. 午前八時); any other stored value is a literal ICU time pattern (e.g. "HH:mm", "h:mm a")
// rendered in the device locale. All timestamps here are the calendar's epoch-seconds.

/** A single start/end time token. */
fun formatEventClock(tsSeconds: Long, format: String): String {
    val millis = tsSeconds * 1000L
    return if (format == EVENT_TIME_FORMAT_JAPANESE) {
        millis.toJapaneseClockString()
    } else {
        try {
            SimpleDateFormat(format, ULocale.getDefault()).format(Date(millis))
        } catch (e: Exception) {
            SimpleDateFormat("HH:mm", ULocale.getDefault()).format(Date(millis))
        }
    }
}

/** Separator between the start and end times in the day view. */
fun eventTimeSeparator(format: String) = if (format == EVENT_TIME_FORMAT_JAPANESE) "ー" else " - "

/** Japanese duration suffix （…） for the day view; empty for plain (masked) formats. */
fun eventDurationSuffix(startTSSeconds: Long, endTSSeconds: Long, format: String) =
    if (format == EVENT_TIME_FORMAT_JAPANESE) (endTSSeconds - startTSSeconds).toInt().toJapaneseDurationString() else ""

/** Full start–end (+ duration) range — used for samples and simple cases. */
fun formatEventTimeRange(startTSSeconds: Long, endTSSeconds: Long, format: String) =
    formatEventClock(startTSSeconds, format) + eventTimeSeparator(format) +
        formatEventClock(endTSSeconds, format) + eventDurationSuffix(startTSSeconds, endTSSeconds, format)

// --- Sino-Japanese clock + duration, mirrored from shiroikuma-denwa ---

// After the title in the day view, when the start or end zone differs from the device zone, the
// event's own start/end times in the Sino-Japanese format, each prefixed (no space) with its zone
// code, separated by the Japanese dash and wrapped in full-width parens: （CET午前八時ーHST午前九時）.
// Returns null when both zones match the device zone. All timestamps are the calendar's epoch-seconds.
fun eventTimeZoneAnnotation(startTSSeconds: Long, endTSSeconds: Long, startZoneId: String, endZoneId: String): String? {
    val deviceId = ZoneId.systemDefault().id
    if (startZoneId == deviceId && endZoneId == deviceId) {
        return null
    }
    val startMillis = startTSSeconds * 1000L
    val endMillis = endTSSeconds * 1000L
    val startText = zoneCode(startZoneId, startMillis) + startMillis.toJapaneseClockString(zoneOf(startZoneId))
    val endText = zoneCode(endZoneId, endMillis) + endMillis.toJapaneseClockString(zoneOf(endZoneId))
    return "（$startText" + "ー" + "$endText）"
}

private fun zoneOf(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (e: Exception) {
    ZoneId.systemDefault()
}

// IANA-style zone abbreviations keyed by ICU metazone id -> (standard, daylight). ICU's English short
// names return "GMT+1" for most non-US zones, so we resolve the metazone and map it ourselves.
private val METAZONE_CODES = mapOf(
    "Europe_Central" to Pair("CET", "CEST"),
    "Europe_Western" to Pair("WET", "WEST"),
    "Europe_Eastern" to Pair("EET", "EEST"),
    "GMT" to Pair("GMT", "BST"),
    "Moscow" to Pair("MSK", "MSD"),
    "America_Pacific" to Pair("PST", "PDT"),
    "America_Mountain" to Pair("MST", "MDT"),
    "America_Central" to Pair("CST", "CDT"),
    "America_Eastern" to Pair("EST", "EDT"),
    "Alaska" to Pair("AKST", "AKDT"),
    "Atlantic" to Pair("AST", "ADT"),
    "Hawaii_Aleutian" to Pair("HST", "HDT"),
    "Japan" to Pair("JST", "JST"),
    "China" to Pair("CST", "CST"),
    "Korea" to Pair("KST", "KST"),
    "Gulf" to Pair("GST", "GST"),
    "Indochina" to Pair("ICT", "ICT"),
    "Singapore" to Pair("SGT", "SGT"),
    "Australia_Eastern" to Pair("AEST", "AEDT"),
    "Australia_Western" to Pair("AWST", "AWST"),
    "Australia_Central" to Pair("ACST", "ACDT"),
    "New_Zealand" to Pair("NZST", "NZDT"),
    "Brasilia" to Pair("BRT", "BRST"),
    "Africa_Southern" to Pair("SAST", "SAST"),
    "Africa_Eastern" to Pair("EAT", "EAT"),
    "Africa_Western" to Pair("WAT", "WAST"),
)

// Zones ICU has no metazone for; map their id directly (none of these observe DST).
private val DIRECT_ZONE_CODES = mapOf(
    "UTC" to "UTC",
    "Etc/UTC" to "UTC",
    "Etc/GMT" to "GMT",
    "Asia/Kolkata" to "IST",
    "Asia/Calcutta" to "IST",
)

private val ICU_TZ_NAMES: TimeZoneNames by lazy { TimeZoneNames.getInstance(ULocale.ENGLISH) }

// DST-aware short zone code for the instant: CET/CEST, HST, PST/PDT, JST, … with a compact GMT
// offset (e.g. GMT+5:30) for zones not in the table.
private fun zoneCode(id: String, millis: Long): String {
    DIRECT_ZONE_CODES[id]?.let { return it }
    val tz = TimeZone.getTimeZone(id)
    val daylight = tz.inDaylightTime(Date(millis))
    val metazone = try {
        ICU_TZ_NAMES.getMetaZoneID(id, millis)
    } catch (e: Exception) {
        null
    }
    METAZONE_CODES[metazone]?.let { return if (daylight) it.second else it.first }
    return gmtOffsetCode(tz.getOffset(millis))
}

private fun gmtOffsetCode(offsetMillis: Int): String {
    val totalMinutes = offsetMillis / 60000
    val sign = if (totalMinutes < 0) "-" else "+"
    val abs = if (totalMinutes < 0) -totalMinutes else totalMinutes
    val hours = abs / 60
    val minutes = abs % 60
    return if (minutes == 0) "GMT$sign$hours" else "GMT$sign$hours:${"%02d".format(minutes)}"
}

// Sino-Japanese clock reading: 14:53 -> 午後二時五十三分, 9:30 -> 午前九時半. :00 drops the minute
// part, :30 becomes 半; noon/midnight get the special words 正午 / 正子.
fun Long.toJapaneseClockString(): String = toJapaneseClockString(ZoneId.systemDefault())

fun Long.toJapaneseClockString(zone: ZoneId): String {
    val time = Instant.ofEpochMilli(this).atZone(zone)
    val hour = time.hour
    val minute = time.minute
    when {
        hour == 12 && minute == 0 -> return "正午"
        hour == 12 && minute == 30 -> return "正午半"
        hour == 0 && minute == 0 -> return "正子"
        hour == 0 && minute == 30 -> return "正子半"
    }
    val period = if (hour < 12) "午前" else "午後"
    val hour12 = when {
        hour == 0 -> 12
        hour <= 12 -> hour
        else -> hour - 12
    }
    val minutePart = when (minute) {
        0 -> ""
        30 -> "半"
        else -> "${minute.toKanjiNumeral()}分"
    }
    return "$period${hour12.toKanjiNumeral()}時$minutePart"
}

// A duration in seconds as kanji wrapped in full-width parentheses: 6 -> （六秒）,
// 210 -> （三分半）, 403 -> （六分四十三秒）. A trailing 30s/30m becomes 半 of the unit above it.
fun Int.toJapaneseDurationString(): String {
    val core = if (this <= 0) {
        "零秒"
    } else {
        val hours = this / 3600
        val minutes = this % 3600 / 60
        val seconds = this % 60
        buildString {
            if (hours > 0) append("${hours.toKanjiNumeral()}時間")
            if (hours > 0 && minutes == 30 && seconds == 0) {
                append("半")
            } else {
                if (minutes > 0) append("${minutes.toKanjiNumeral()}分")
                if (minutes > 0 && seconds == 30) {
                    append("半")
                } else if (seconds > 0) {
                    append("${seconds.toKanjiNumeral()}秒")
                }
            }
        }
    }
    return "（$core）"
}

// Everyday kanji numerals for 1..9999 (e.g. 29 -> 二十九, 120 -> 百二十). Matches the
// shiroikuma-denwa output for the ≤99 range it covers, and extends safely to larger event
// durations; ≥10000 falls back to digits.
internal fun Int.toKanjiNumeral(): String {
    if (this <= 0) return "〇"
    if (this >= 10000) return toString()
    val digits = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    val units = arrayOf("", "十", "百", "千")
    val places = intArrayOf(this / 1000 % 10, this / 100 % 10, this / 10 % 10, this % 10)
    return buildString {
        for (i in places.indices) {
            val digit = places[i]
            val unitIdx = places.size - 1 - i
            if (digit == 0) continue
            if (digit == 1 && unitIdx in 1..3) {
                append(units[unitIdx]) // 十 / 百 / 千 without a leading 一
            } else {
                append(digits[digit]).append(units[unitIdx])
            }
        }
    }
}
