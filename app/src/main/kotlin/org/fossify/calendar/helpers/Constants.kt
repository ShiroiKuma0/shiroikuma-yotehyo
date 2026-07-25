package org.fossify.calendar.helpers

import android.provider.CalendarContract.Events
import org.fossify.calendar.activities.EventActivity
import org.fossify.calendar.activities.TaskActivity
import org.fossify.commons.helpers.MONTH_SECONDS
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import java.util.UUID

const val STORED_LOCALLY_ONLY = 0
const val ROW_COUNT = 6
const val COLUMN_COUNT = 7
const val SCHEDULE_CALDAV_REQUEST_CODE = 10000
const val AUTOMATIC_BACKUP_REQUEST_CODE = 10001
const val DUMMY_ALARM_REQUEST_CODE = 10002
const val FETCH_INTERVAL = 3 * MONTH_SECONDS
const val MAX_SEARCH_YEAR =
    2051218800L  // 2035, limit search results for events repeating indefinitely

// endless scrolling updating
const val MIN_EVENTS_TRESHOLD = 30
const val INITIAL_EVENTS = 0
const val UPDATE_TOP = 1
const val UPDATE_BOTTOM = 2

const val DAY_CODE = "day_code"
const val YEAR_LABEL = "year"
const val EVENT_ID = "event_id"
const val IS_DUPLICATE_INTENT = "is_duplicate_intent"
const val EVENT_OCCURRENCE_TS = "event_occurrence_ts"
const val IS_TASK_COMPLETED = "is_task_completed"
const val NEW_EVENT_START_TS = "new_event_start_ts"
const val WEEK_START_TIMESTAMP = "week_start_timestamp"
const val NEW_EVENT_SET_HOUR_DURATION = "new_event_set_hour_duration"
const val WEEK_START_DATE_TIME = "week_start_date_time"
const val YEAR_TO_OPEN = "year_to_open"
const val CALDAV = "Caldav"
const val VIEW_TO_OPEN = "view_to_open"
const val SHORTCUT_NEW_EVENT = "shortcut_new_event"
const val SHORTCUT_NEW_TASK = "shortcut_new_task"
const val LOCAL_CALENDAR_ID = 1L
const val TIME_ZONE = "time_zone"
const val END_TIME_ZONE = "end_time_zone"
const val CURRENT_TIME_ZONE = "current_time_zone"
const val IS_TASK = "is_task"

const val MONTHLY_VIEW = 1
const val YEARLY_VIEW = 2
const val EVENTS_LIST_VIEW = 3
const val WEEKLY_VIEW = 4
const val DAILY_VIEW = 5
const val LAST_VIEW = 6
const val MONTHLY_DAILY_VIEW = 7
const val WEEKLY_GRID_VIEW = 8

// Day-box week view header text alignment
const val DAY_BOX_ALIGN_START = 0
const val DAY_BOX_ALIGN_CENTER = 1
const val DAY_BOX_ALIGN_END = 2

// A today/weekend border-thickness of this value means "inherit the general day-box thickness".
const val DAY_BOX_THICKNESS_INHERIT = -1

const val REMINDER_OFF = -1
const val REMINDER_DEFAULT_VALUE = "$REMINDER_OFF,$REMINDER_OFF,$REMINDER_OFF"

const val OTHER_EVENT = 0
const val BIRTHDAY_EVENT = 1
const val ANNIVERSARY_EVENT = 2
const val HOLIDAY_EVENT = 3

const val ITEM_EVENT = 0
const val ITEM_SECTION_DAY = 1
const val ITEM_SECTION_MONTH = 2

const val DEFAULT_START_TIME_NEXT_FULL_HOUR = -1
const val DEFAULT_START_TIME_CURRENT_TIME = -2

const val TYPE_EVENT = 0
const val TYPE_TASK = 1

const val TWELVE_HOURS = 43200
const val DAY = 86400
const val WEEK = 604800
const val MONTH =
    2592001    // exact value not taken into account, Joda is used for adding months and years
const val YEAR = 31536000

const val EVENT_PERIOD_TODAY = -1
const val EVENT_PERIOD_CUSTOM = -2

const val AUTO_BACKUP_INTERVAL_IN_DAYS = 1

const val EVENT_LIST_PERIOD = "event_list_period"

// Shared Preferences
const val WEEK_NUMBERS = "week_numbers"
const val START_WEEKLY_AT = "start_weekly_at"
const val START_WEEK_WITH_CURRENT_DAY = "start_week_with_current_day"
const val SHOW_MIDNIGHT_SPANNING_EVENTS_AT_TOP = "show_midnight_spanning_events_at_top"
const val ALLOW_CUSTOMIZE_DAY_COUNT = "allow_customise_day_count"
const val VIBRATE = "vibrate"
const val REMINDER_SOUND_URI = "reminder_sound_uri"
const val REMINDER_SOUND_TITLE = "reminder_sound_title"
const val VIEW = "view"
const val LAST_EVENT_REMINDER_MINUTES = "reminder_minutes"
const val LAST_EVENT_REMINDER_MINUTES_2 = "reminder_minutes_2"
const val LAST_EVENT_REMINDER_MINUTES_3 = "reminder_minutes_3"
const val DISPLAY_CALENDARS = "display_event_types"
const val QUICK_FILTER_CALENDARS = "quick_filter_event_types"
const val LIST_WIDGET_VIEW_TO_OPEN = "list_widget_view_to_open"
const val CALDAV_SYNC = "caldav_sync"
const val CALDAV_SYNCED_CALENDAR_IDS = "caldav_synced_calendar_ids"
const val LAST_USED_CALDAV_CALENDAR = "last_used_caldav_calendar"
const val LAST_USED_LOCAL_CALENDAR_ID = "last_used_local_event_type_id"
const val LAST_USED_IGNORE_CALENDARS_STATE = "last_used_ignore_event_types_state"
const val DISPLAY_PAST_EVENTS = "display_past_events"
const val DISPLAY_DESCRIPTION = "display_description"
const val REPLACE_DESCRIPTION = "replace_description"
const val SHOW_GRID = "show_grid"
const val LOOP_REMINDERS = "loop_reminders"
const val DIM_PAST_EVENTS = "dim_past_events"
const val DIM_COMPLETED_TASKS = "dim_completed_tasks"
const val LAST_SOUND_URI = "last_sound_uri"
const val LAST_REMINDER_CHANNEL_ID = "last_reminder_channel_ID"
const val REMINDER_AUDIO_STREAM = "reminder_audio_stream"
const val USE_PREVIOUS_EVENT_REMINDERS = "use_previous_event_reminders"
const val DEFAULT_REMINDER_1 = "default_reminder_1"
const val DEFAULT_REMINDER_2 = "default_reminder_2"
const val DEFAULT_REMINDER_3 = "default_reminder_3"
const val PULL_TO_REFRESH = "pull_to_refresh"
const val LAST_VIBRATE_ON_REMINDER = "last_vibrate_on_reminder"
const val DEFAULT_START_TIME = "default_start_time"
const val DEFAULT_DURATION = "default_duration"
const val DEFAULT_CALENDAR_ID = "default_event_type_id"
const val ALLOW_CHANGING_TIME_ZONES = "allow_changing_time_zones"
const val ADD_BIRTHDAYS_AUTOMATICALLY = "add_birthdays_automatically"
const val ADD_ANNIVERSARIES_AUTOMATICALLY = "add_anniversaries_automatically"
const val BIRTHDAY_REMINDERS = "birthday_reminders"
const val ANNIVERSARY_REMINDERS = "anniversary_reminders"
const val LAST_EXPORT_PATH = "last_export_path"
const val EXPORT_EVENTS = "export_events"
const val EXPORT_TASKS = "export_tasks"
const val EXPORT_PAST_EVENTS = "export_past_events"
const val WEEKLY_VIEW_ITEM_HEIGHT_MULTIPLIER = "weekly_view_item_height_multiplier"
const val WEEKLY_VIEW_DAYS = "weekly_view_days"
const val SHOW_TIME_GRID_WEEKLY = "show_time_grid_weekly"
const val SHOW_BOX_GRID_WEEKLY = "show_box_grid_weekly"
const val DAY_BOX_HEADER_DATE_FORMAT = "day_box_header_date_format"

// Day-box header date-format presets. Any other stored value is treated as a literal ICU date pattern.
const val DAY_BOX_HEADER_FORMAT_JAPANESE = "japanese"
const val DAY_BOX_HEADER_FORMAT_JAPANESE_ERA = "japanese_era"

// Event time format (week/day views). The default uses the Sino-Japanese clock (午前八時);
// any other stored value is a literal ICU time pattern (e.g. "HH:mm").
const val EVENT_TIME_FORMAT = "event_time_format"
const val EVENT_TIME_FORMAT_JAPANESE = "japanese"
const val DAY_BOX_HEADER_ALIGNMENT = "day_box_header_alignment"
const val DAY_BOX_BORDER_THICKNESS = "day_box_border_thickness"
const val DAY_BOX_HEADER_BORDER_THICKNESS = "day_box_header_border_thickness"
const val TODAY_BOX_BORDER_THICKNESS = "today_box_border_thickness"
const val TODAY_HEADER_BORDER_THICKNESS = "today_header_border_thickness"
const val WEEKEND_BOX_BORDER_THICKNESS = "weekend_box_border_thickness"
const val WEEKEND_HEADER_BORDER_THICKNESS = "weekend_header_border_thickness"
const val HIGHLIGHT_WEEKENDS = "highlight_weekends"
const val HIGHLIGHT_WEEKENDS_COLOR = "highlight_weekends_color"
const val LAST_USED_EVENT_SPAN = "last_used_event_span"

// Granular theming (白い熊 予定表 UI)
const val THEME_V1_SEEDED = "theme_v1_seeded"
const val DIALOG_STYLE_SEEDED = "dialog_style_seeded" // one-time seed of the yellow dialog border/buttons
const val PURE_YELLOW_MIGRATED = "pure_yellow_migrated" // one-time #FFEB3B → #FFFF00 rewrite of persisted colors
const val DIALOG_BORDER_WIDTH_DEFAULT = 2 // dp
const val THEME_REVISION = "theme_revision" // bumped on any UI-override change so views repaint
const val THEME_UNSET = Int.MIN_VALUE // a slot with this stored value follows its inherited default
const val PALETTE_BLACK = 0xFF000000.toInt()
const val PALETTE_YELLOW = 0xFFFFFF00.toInt()

// Per-element fonts: one entry per text slot, keyed by the slot key.
const val FONT_FAMILY_PREFIX = "font_family_" // String, "" = system/global default
const val FONT_WEIGHT_PREFIX = "font_weight_" // Int, 0 = default, else 100..900
const val FONT_SIZE_PREFIX = "font_size_"     // Int sp, 0 = default
const val MAX_FONT_SIZE_SP = 40
const val ALLOW_CREATING_TASKS = "allow_creating_tasks"
const val WAS_FILTERED_OUT_WARNING_SHOWN = "was_filtered_out_warning_shown"
const val AUTO_BACKUP = "auto_backup"
const val AUTO_BACKUP_FOLDER = "auto_backup_folder"
const val AUTO_BACKUP_FILENAME = "auto_backup_filename"
const val AUTO_BACKUP_CALENDARS = "auto_backup_event_types"
const val AUTO_BACKUP_EVENTS = "auto_backup_events"
const val AUTO_BACKUP_TASKS = "auto_backup_tasks"
const val AUTO_BACKUP_PAST_ENTRIES = "auto_backup_past_entries"
const val LAST_AUTO_BACKUP_TIME = "last_auto_backup_time"
const val LAST_USED_SHOW_LIST_WIDGET_HEADER = "last_used_show_widget_header"
const val WIDGET_SHOW_GRID = "widget_show_grid"

// repeat_rule for monthly and yearly repetition
const val REPEAT_SAME_DAY =
    1                           // i.e. 25th every month, or 3rd june (if yearly repetition)
const val REPEAT_ORDER_WEEKDAY_USE_LAST =
    2             // i.e. every last sunday. 4th if a month has 4 sundays, 5th if 5 (or last sunday in june, if yearly)
const val REPEAT_LAST_DAY = 3                           // i.e. every last day of the month
const val REPEAT_ORDER_WEEKDAY =
    4                      // i.e. every 4th sunday, even if a month has 4 sundays only (will stay 4th even at months with 5)

// special event and task flags
const val FLAG_ALL_DAY = 1
const val FLAG_IS_IN_PAST = 2
const val FLAG_MISSING_YEAR = 4
const val FLAG_TASK_COMPLETED = 8

// constants related to ICS file exporting / importing
const val BEGIN_CALENDAR = "BEGIN:VCALENDAR"
const val END_CALENDAR = "END:VCALENDAR"
const val CALENDAR_PRODID = "PRODID:-//Fossify//NONSGML Event Calendar//EN"
const val CALENDAR_VERSION = "VERSION:2.0"
const val BEGIN_EVENT = "BEGIN:VEVENT"
const val END_EVENT = "END:VEVENT"
const val BEGIN_TASK = "BEGIN:VTODO"
const val END_TASK = "END:VTODO"
const val BEGIN_ALARM = "BEGIN:VALARM"
const val END_ALARM = "END:VALARM"
const val DUE = "DUE"
const val DTSTART = "DTSTART"
const val DTEND = "DTEND"
const val LAST_MODIFIED = "LAST-MODIFIED"
const val DTSTAMP = "DTSTAMP:"
const val DURATION = "DURATION:"
const val SUMMARY = "SUMMARY"
const val DESCRIPTION = "DESCRIPTION"
const val DESCRIPTION_EXPORT = "DESCRIPTION:"
val DESCRIPTION_REGEX =
    Regex("""DESCRIPTION(?:(?:;[^:;]*="[^"]*")*;?(?:;LANGUAGE=[^:;]*)?(?:;[^:;]*="[^"]*")*)*:(.*(?:\r?\n\s+.*)*)""")
const val UID = "UID:"
const val ACTION = "ACTION:"
const val TRANSP = "TRANSP:"
const val ATTENDEE = "ATTENDEE:"
const val MAILTO = "mailto:"
const val TRIGGER = "TRIGGER"
const val RRULE = "RRULE:"
const val CATEGORIES = "CATEGORIES:"
const val STATUS = "STATUS:"
const val EXDATE = "EXDATE"
const val BYDAY = "BYDAY"
const val BYMONTHDAY = "BYMONTHDAY"
const val BYMONTH = "BYMONTH"
const val LOCATION = "LOCATION"
const val RECURRENCE_ID = "RECURRENCE-ID"
const val SEQUENCE = "SEQUENCE"
const val TZID = "TZID"
const val COLOR = "COLOR:"
const val FOSSIFY_COLOR = "X-FOSSIFY-EVENT-COLOR:"

// this tag isn't a standard ICS tag, but there's no official way of adding a category color in an ics file
const val SMT_CATEGORY_COLOR = "X-SMT-CATEGORY-COLOR:"
const val CATEGORY_COLOR = "X-FOSSIFY-CATEGORY-COLOR:"
const val CATEGORY_COLOR_LEGACY = "CATEGORY_COLOR:"
const val SMT_MISSING_YEAR = "X-SMT-MISSING-YEAR:"
const val MISSING_YEAR = "X-FOSSIFY-MISSING-YEAR:"

const val DISPLAY = "DISPLAY"
const val EMAIL = "EMAIL"
const val FREQ = "FREQ"
const val UNTIL = "UNTIL"
const val COUNT = "COUNT"
const val INTERVAL = "INTERVAL"
const val CONFIRMED = "CONFIRMED"
const val COMPLETED = "COMPLETED"
const val VALUE = "VALUE"
const val DATE = "DATE"

const val DAILY = "DAILY"
const val WEEKLY = "WEEKLY"
const val MONTHLY = "MONTHLY"
const val YEARLY = "YEARLY"

const val MO = "MO"
const val TU = "TU"
const val WE = "WE"
const val TH = "TH"
const val FR = "FR"
const val SA = "SA"
const val SU = "SU"

const val OPAQUE = "OPAQUE"
const val TRANSPARENT = "TRANSPARENT"

const val SOURCE_SIMPLE_CALENDAR = "simple-calendar"
const val SOURCE_IMPORTED_ICS = "imported-ics"
const val SOURCE_CONTACT_BIRTHDAY = "contact-birthday"
const val SOURCE_CONTACT_ANNIVERSARY = "contact-anniversary"

const val DELETE_SELECTED_OCCURRENCE = 0
const val DELETE_FUTURE_OCCURRENCES = 1
const val DELETE_ALL_OCCURRENCES = 2

const val EDIT_SELECTED_OCCURRENCE = 0
const val EDIT_FUTURE_OCCURRENCES = 1
const val EDIT_ALL_OCCURRENCES = 2

const val REMINDER_NOTIFICATION = 0
const val REMINDER_EMAIL = 1

const val EVENT = "EVENT"
const val TASK = "TASK"
const val START_TS = "START_TS"
const val END_TS = "END_TS"
const val ORIGINAL_START_TS = "ORIGINAL_START_TS"
const val ORIGINAL_END_TS = "ORIGINAL_END_TS"
const val REMINDER_1_MINUTES = "REMINDER_1_MINUTES"
const val REMINDER_2_MINUTES = "REMINDER_2_MINUTES"
const val REMINDER_3_MINUTES = "REMINDER_3_MINUTES"
const val REMINDER_1_TYPE = "REMINDER_1_TYPE"
const val REMINDER_2_TYPE = "REMINDER_2_TYPE"
const val REMINDER_3_TYPE = "REMINDER_3_TYPE"
const val REPEAT_INTERVAL = "REPEAT_INTERVAL"
const val REPEAT_LIMIT = "REPEAT_LIMIT"
const val REPEAT_RULE = "REPEAT_RULE"
const val ATTENDEES = "ATTENDEES"
const val ORIGINAL_ATTENDEES = "ORIGINAL_ATTENDEES"
const val AVAILABILITY = "AVAILABILITY"
const val CLASS = "CLASS"
const val CALENDAR_ID = "CALENDAR_ID"
const val EVENT_CALENDAR_ID = "EVENT_CALENDAR_ID"
const val IS_NEW_EVENT = "IS_NEW_EVENT"
const val EVENT_COLOR = "EVENT_COLOR"

// From Status attribute (RFC 5545 3.8.1.11)
const val CANCELLED = "CANCELLED"
const val TENTATIVE = "TENTATIVE"

//From Classification attribute (RFC 2445 4.8.1.3)
const val PUBLIC = "PUBLIC"
const val PRIVATE = "PRIVATE"
const val CONFIDENTIAL = "CONFIDENTIAL"

// actions
const val ACTION_MARK_COMPLETED = "ACTION_MARK_COMPLETED"

// The 保存復元 state-export automation contract — the wire shape every 白い熊 app exposes so a single
// 自由作業盤 run can back them all up headlessly. See receivers/StateExportReceiver. Both actions are
// exported and carry no android:permission (the caller cannot hold one); the token is the gate.
const val ACTION_EXPORT_STATE = "shiroikuma.yotehyo.action.EXPORT_STATE"
const val ACTION_LIST_CATEGORIES = "shiroikuma.yotehyo.action.LIST_CATEGORIES"

// Contract extras — deliberately bare names, shared verbatim by every sister app.
const val EXTRA_AUTOMATION_TOKEN = "token"
const val EXTRA_EXPORT_PATH = "path"
const val EXTRA_EXPORT_ITEMS = "items"
const val EXTRA_PROGRESS_ACTION = "progress_action"
const val EXTRA_REPLY_ACTION = "reply_action"
const val EXTRA_REPLY_PACKAGE = "reply_package"
const val EXTRA_REPLY_ID = "reply_id"
const val EXTRA_REPLY_RESULT = "result"
const val EXTRA_PROGRESS_APP = "app"
const val EXTRA_PROGRESS_TEXT = "text"
const val EXTRA_PROGRESS_CURRENT = "current"
const val EXTRA_PROGRESS_TOTAL = "total"
const val EXTRA_PROGRESS_UNIT = "unit"

// At most one progress broadcast per this many ms (the final one at completion is unthrottled).
const val PROGRESS_THROTTLE_MS = 500L

// The automation gate, in the app's shared prefs but never exported — see SettingsTransfer's
// DEVICE_LOCAL_KEYS: each device owns its own security state, so a restore must not flip the switch
// or overwrite the secret, and the token must never travel in a backup ZIP.
const val AUTOMATION_ENABLED = "automation_enabled"
const val AUTOMATION_TOKEN = "automation_token"

fun getNowSeconds() = System.currentTimeMillis() / 1000L

fun isWeekend(dayOfWeek: Int): Boolean {
    val weekendDays = listOf(DateTimeConstants.SATURDAY, DateTimeConstants.SUNDAY)
    return dayOfWeek in weekendDays
}

fun getActivityToOpen(isTask: Boolean) = if (isTask) {
    TaskActivity::class.java
} else {
    EventActivity::class.java
}

fun generateImportId(): String {
    return UUID.randomUUID().toString().replace("-", "") + System.currentTimeMillis().toString()
}

// 6 am is the hardcoded automatic backup time, intervals shorter than 1 day are not yet supported.
fun getNextAutoBackupTime(): DateTime {
    val now = DateTime.now()
    val sixHour = now.withHourOfDay(6)
    return if (now.millis < sixHour.millis) {
        sixHour
    } else {
        sixHour.plusDays(AUTO_BACKUP_INTERVAL_IN_DAYS)
    }
}

fun getPreviousAutoBackupTime(): DateTime {
    val nextBackupTime = getNextAutoBackupTime()
    return nextBackupTime.minusDays(AUTO_BACKUP_INTERVAL_IN_DAYS)
}

fun getStatusStringFromEventStatus(statusCode: Int): String {
    return when (statusCode) {
        Events.STATUS_CONFIRMED -> CONFIRMED
        Events.STATUS_CANCELED -> CANCELLED
        else -> TENTATIVE
    }
}

fun getAccessLevelStringFromEventAccessLevel(accessLevel: Int): String {
    return when (accessLevel) {
        Events.ACCESS_PRIVATE -> PRIVATE
        Events.ACCESS_CONFIDENTIAL -> CONFIDENTIAL
        else -> PUBLIC
    }
}
