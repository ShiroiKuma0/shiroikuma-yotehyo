package org.fossify.calendar.models

import org.fossify.calendar.helpers.THEME_UNSET

data class MonthViewEvent(
    val id: Long,
    val title: String,
    val startTS: Long,
    val endTS: Long,
    val color: Int,
    val startDayIndex: Int,
    val daysCnt: Int,
    val originalStartDayIndex: Int,
    val isAllDay: Boolean,
    val isPastEvent: Boolean,
    val isTask: Boolean,
    val isTaskCompleted: Boolean,
    val isAttendeeInviteDeclined: Boolean,
    val isEventCanceled: Boolean,
    // The category styling, as the weekly grid draws it: with a background set, the bar is filled
    // with it and the title takes `color`; without one, the bar is `color` with a contrasting title.
    val categoryBackgroundColor: Int = THEME_UNSET,
    val categoryFontFamily: String = "",
    val categoryFontWeight: Int = 0,
) {
    fun hasCategoryBackground() = categoryBackgroundColor != THEME_UNSET
}
