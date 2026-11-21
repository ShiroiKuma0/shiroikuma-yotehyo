package org.fossify.calendar.fragments

import androidx.fragment.app.Fragment
import org.fossify.calendar.dialogs.familyDatePicker
import org.fossify.commons.views.FamilyDatePicker
import org.joda.time.DateTime

abstract class MyFragmentHolder : Fragment() {
    abstract val viewType: Int

    abstract fun goToToday()

    abstract fun showGoToDateDialog()

    abstract fun refreshEvents()

    abstract fun shouldGoToTodayBeVisible(): Boolean

    abstract fun getNewEventDayCode(): String

    abstract fun printView()

    abstract fun getCurrentDate(): DateTime?

    /**
     * The picker of a "go to date" dialog: a view that jumps by month drops the day grid, one that
     * jumps by year the month row as well.
     */
    fun getDatePickerView(dateTime: DateTime, showDays: Boolean = true, showMonth: Boolean = true): FamilyDatePicker {
        val day = if (showDays) dateTime.dayOfMonth else 1
        return requireActivity().familyDatePicker(dateTime.year, dateTime.monthOfYear - 1, day).also {
            it.showDays = showDays
            it.showMonth = showMonth
        }
    }
}
