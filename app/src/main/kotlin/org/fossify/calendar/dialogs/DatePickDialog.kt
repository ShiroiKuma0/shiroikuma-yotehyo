package org.fossify.calendar.dialogs

import android.app.Activity
import android.content.Context
import org.fossify.calendar.extensions.config
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.getJavaDayOfWeekFromISO
import org.fossify.commons.views.FamilyDatePicker

private const val PICKER_TOP_PADDING_DP = 16

/**
 * The commons' own month grid (every part of it a `UiRole.PICKER_*` role), starting its weeks on the
 * day set in 予定表's settings. The platform DatePicker takes its colours from the theme, which names
 * none since commons sk18, so it is used nowhere in this app.
 */
fun Context.familyDatePicker(year: Int, month: Int, day: Int) = FamilyDatePicker(this).apply {
    firstDayOfWeek = getJavaDayOfWeekFromISO(config.firstDayOfWeek)
    setPadding(0, (PICKER_TOP_PADDING_DP * resources.displayMetrics.density).toInt(), 0, 0)
    updateDate(year, month, day)
}

/** Pick a date in an ordinary dialog; [month] is 0-based, as it is in the callback. */
class DatePickDialog(
    activity: Activity,
    year: Int,
    month: Int,
    day: Int,
    callback: (year: Int, month: Int, day: Int) -> Unit,
) {
    init {
        val picker = activity.familyDatePicker(year, month, day)
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                callback(picker.year, picker.month, picker.dayOfMonth)
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { activity.setupDialogStuff(picker, this) }
    }
}
