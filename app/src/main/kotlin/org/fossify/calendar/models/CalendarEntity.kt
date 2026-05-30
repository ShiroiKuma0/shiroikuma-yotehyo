package org.fossify.calendar.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.fossify.calendar.helpers.OTHER_EVENT
import org.fossify.calendar.helpers.THEME_UNSET

@Entity(tableName = "event_types", indices = [(Index(value = ["id"], unique = true))])
data class CalendarEntity(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "color") var color: Int,
    @ColumnInfo(name = "caldav_calendar_id") var caldavCalendarId: Int = 0,
    @ColumnInfo(name = "caldav_display_name") var caldavDisplayName: String = "",
    @ColumnInfo(name = "caldav_email") var caldavEmail: String = "",
    @ColumnInfo(name = "type") var type: Int = OTHER_EVENT,
    // Pimlical-style category styling. The category's `color` doubles as its text colour; these add
    // an optional background colour and per-element font. THEME_UNSET / "" / 0 mean "not set".
    @ColumnInfo(name = "background_color") var backgroundColor: Int = THEME_UNSET,
    @ColumnInfo(name = "font_family") var fontFamily: String = "",
    @ColumnInfo(name = "font_weight") var fontWeight: Int = 0,
    @ColumnInfo(name = "font_size") var fontSize: Int = 0,
) {
    fun getDisplayTitle() =
        if (caldavCalendarId == 0) title else "$caldavDisplayName ($caldavEmail)"

    fun isSyncedCalendar() = caldavCalendarId != 0

    // True once the category carries styling beyond its colour (so views can opt into restyling).
    fun hasCustomStyle() =
        backgroundColor != THEME_UNSET || fontFamily.isNotEmpty() || fontWeight > 0 || fontSize > 0
}
