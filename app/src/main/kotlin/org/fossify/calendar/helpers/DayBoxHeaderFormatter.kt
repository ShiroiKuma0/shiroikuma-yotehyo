package org.fossify.calendar.helpers

import android.icu.text.NumberFormat
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.ULocale
import java.util.Date

// Renders a box-grid week-view header date.
//
// The two presets use ICU with the Japanese numbering system (algorithmic kanji numerals,
// e.g. 30 -> 三十, 15 -> 十五) — and, for the era preset, the Japanese imperial calendar so the
// year renders as 令和八年 rather than 2026. java.time can't do this: its DecimalStyle only
// supports contiguous decimal numbering systems, not algorithmic ones like "jpan".
//
//   japanese      -> 五月三十日（土曜日）
//   japanese_era  -> 令和八年五月三十日（土曜日）
//
// Any other value is treated as a literal ICU date pattern (e.g. "MM-dd", "yyyy/MM/dd EEE")
// rendered in the device's default locale. A malformed pattern falls back to a plain date.
fun formatDayBoxHeader(millis: Long, format: String): String {
    val date = Date(millis)
    return try {
        when (format) {
            DAY_BOX_HEADER_FORMAT_JAPANESE ->
                SimpleDateFormat("M月d日（EEEE）", ULocale("ja_JP@numbers=jpan")).format(date)

            DAY_BOX_HEADER_FORMAT_JAPANESE_ERA -> {
                // ICU's Japanese calendar renders the era *year* in Latin digits even with
                // numbers=jpan, so compose it: era name + kanji-numeral year + the (jpan) rest.
                val eraLocale = ULocale("ja_JP@calendar=japanese")
                val eraName = SimpleDateFormat("G", eraLocale).format(date)
                val eraYear = Calendar.getInstance(eraLocale).apply { timeInMillis = millis }.get(Calendar.YEAR)
                val yearKanji = if (eraYear == 1) {
                    "元" // the first year of an era is conventionally written 元年, not 一年
                } else {
                    NumberFormat.getInstance(ULocale("ja_JP@numbers=jpan"))
                        .apply { isGroupingUsed = false }
                        .format(eraYear.toLong())
                }
                val rest = SimpleDateFormat("M月d日（EEEE）", ULocale("ja_JP@numbers=jpan")).format(date)
                "$eraName${yearKanji}年$rest"
            }

            else -> SimpleDateFormat(format, ULocale.getDefault()).format(date)
        }
    } catch (e: Exception) {
        SimpleDateFormat("EEE, MMM d", ULocale.getDefault()).format(date)
    }
}
