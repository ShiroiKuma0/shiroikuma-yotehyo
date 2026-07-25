package org.fossify.calendar.helpers

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import org.fossify.calendar.R
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.scheduleCalDAVSync
import org.fossify.commons.extensions.getDefaultAlarmTitle
import org.fossify.commons.helpers.BaseConfig
import org.fossify.commons.helpers.DAY_MINUTES
import org.fossify.commons.helpers.YEAR_SECONDS
import java.security.MessageDigest
import java.security.SecureRandom

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)

        // Length of the automation shared secret, hex-encoded to 48 characters.
        private const val AUTOMATION_TOKEN_BYTES = 24
    }

    var showWeekNumbers: Boolean
        get() = prefs.getBoolean(WEEK_NUMBERS, false)
        set(showWeekNumbers) = prefs.edit().putBoolean(WEEK_NUMBERS, showWeekNumbers).apply()

    var startWeeklyAt: Int
        get() = prefs.getInt(START_WEEKLY_AT, 7)
        set(startWeeklyAt) = prefs.edit().putInt(START_WEEKLY_AT, startWeeklyAt).apply()

    var startWeekWithCurrentDay: Boolean
        get() = prefs.getBoolean(START_WEEK_WITH_CURRENT_DAY, false)
        set(startWeekWithCurrentDay) = prefs.edit()
            .putBoolean(START_WEEK_WITH_CURRENT_DAY, startWeekWithCurrentDay).apply()

    var showMidnightSpanningEventsAtTop: Boolean
        get() = prefs.getBoolean(SHOW_MIDNIGHT_SPANNING_EVENTS_AT_TOP, true)
        set(midnightSpanning) = prefs.edit()
            .putBoolean(SHOW_MIDNIGHT_SPANNING_EVENTS_AT_TOP, midnightSpanning).apply()

    var allowCustomizeDayCount: Boolean
        get() = prefs.getBoolean(ALLOW_CUSTOMIZE_DAY_COUNT, true)
        set(allow) = prefs.edit().putBoolean(ALLOW_CUSTOMIZE_DAY_COUNT, allow).apply()

    var vibrateOnReminder: Boolean
        get() = prefs.getBoolean(VIBRATE, false)
        set(vibrate) = prefs.edit().putBoolean(VIBRATE, vibrate).apply()

    @Deprecated("Not used on Oreo+ devices")
    var reminderSoundUri: String
        get() = prefs.getString(
            REMINDER_SOUND_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString()
        )!!
        set(reminderSoundUri) = prefs.edit().putString(REMINDER_SOUND_URI, reminderSoundUri).apply()

    @Deprecated("Not used on Oreo+ devices")
    var reminderSoundTitle: String
        get() = prefs.getString(
            REMINDER_SOUND_TITLE,
            context.getDefaultAlarmTitle(RingtoneManager.TYPE_NOTIFICATION)
        )!!
        set(reminderSoundTitle) = prefs.edit().putString(REMINDER_SOUND_TITLE, reminderSoundTitle)
            .apply()

    @Deprecated("Not used on Oreo+ devices")
    var lastSoundUri: String
        get() = prefs.getString(LAST_SOUND_URI, "")!!
        set(lastSoundUri) = prefs.edit().putString(LAST_SOUND_URI, lastSoundUri).apply()

    var lastReminderChannel: Long
        get() = prefs.getLong(LAST_REMINDER_CHANNEL_ID, 0L)
        set(lastReminderChannel) = prefs.edit()
            .putLong(LAST_REMINDER_CHANNEL_ID, lastReminderChannel).apply()

    var storedView: Int
        get() = prefs.getInt(VIEW, MONTHLY_VIEW)
        set(view) = prefs.edit().putInt(VIEW, view).apply()

    var lastEventReminderMinutes1: Int
        get() = prefs.getInt(LAST_EVENT_REMINDER_MINUTES, 10)
        set(lastEventReminderMinutes) = prefs.edit()
            .putInt(LAST_EVENT_REMINDER_MINUTES, lastEventReminderMinutes).apply()

    var lastEventReminderMinutes2: Int
        get() = prefs.getInt(LAST_EVENT_REMINDER_MINUTES_2, REMINDER_OFF)
        set(lastEventReminderMinutes2) = prefs.edit()
            .putInt(LAST_EVENT_REMINDER_MINUTES_2, lastEventReminderMinutes2).apply()

    var lastEventReminderMinutes3: Int
        get() = prefs.getInt(LAST_EVENT_REMINDER_MINUTES_3, REMINDER_OFF)
        set(lastEventReminderMinutes3) = prefs.edit()
            .putInt(LAST_EVENT_REMINDER_MINUTES_3, lastEventReminderMinutes3).apply()

    var displayPastEvents: Int
        get() = prefs.getInt(DISPLAY_PAST_EVENTS, DAY_MINUTES)
        set(displayPastEvents) = prefs.edit().putInt(DISPLAY_PAST_EVENTS, displayPastEvents).apply()

    var displayCalendars: Set<String>
        get() = prefs.getStringSet(DISPLAY_CALENDARS, HashSet())!!
        set(displayCalendars) = prefs.edit().remove(DISPLAY_CALENDARS)
            .putStringSet(DISPLAY_CALENDARS, displayCalendars).apply()

    var quickFilterCalendars: Set<String>
        get() = prefs.getStringSet(QUICK_FILTER_CALENDARS, HashSet())!!
        set(quickFilterCalendars) = prefs.edit().remove(QUICK_FILTER_CALENDARS)
            .putStringSet(QUICK_FILTER_CALENDARS, quickFilterCalendars).apply()

    fun addQuickFilterCalendar(type: String) {
        val currQuickFilterCalendars = HashSet(quickFilterCalendars)
        currQuickFilterCalendars.add(type)
        quickFilterCalendars = currQuickFilterCalendars
    }

    var listWidgetViewToOpen: Int
        get() = prefs.getInt(LIST_WIDGET_VIEW_TO_OPEN, DAILY_VIEW)
        set(viewToOpenFromListWidget) = prefs.edit()
            .putInt(LIST_WIDGET_VIEW_TO_OPEN, viewToOpenFromListWidget).apply()

    var caldavSync: Boolean
        get() = prefs.getBoolean(CALDAV_SYNC, false)
        set(caldavSync) {
            context.scheduleCalDAVSync(caldavSync)
            prefs.edit().putBoolean(CALDAV_SYNC, caldavSync).apply()
        }

    var caldavSyncedCalendarIds: String
        get() = prefs.getString(CALDAV_SYNCED_CALENDAR_IDS, "")!!
        set(calendarIDs) = prefs.edit().putString(CALDAV_SYNCED_CALENDAR_IDS, calendarIDs).apply()

    var lastUsedCaldavCalendarId: Int
        get() = prefs.getInt(
            LAST_USED_CALDAV_CALENDAR,
            getSyncedCalendarIdsAsList().firstOrNull() ?: STORED_LOCALLY_ONLY
        )
        set(calendarId) = prefs.edit().putInt(LAST_USED_CALDAV_CALENDAR, calendarId).apply()

    var lastUsedLocalCalendarId: Long
        get() = prefs.getLong(LAST_USED_LOCAL_CALENDAR_ID, LOCAL_CALENDAR_ID)
        set(lastUsedLocalCalendarId) = prefs.edit()
            .putLong(LAST_USED_LOCAL_CALENDAR_ID, lastUsedLocalCalendarId).apply()

    var lastUsedIgnoreCalendarsState: Boolean
        get() = prefs.getBoolean(LAST_USED_IGNORE_CALENDARS_STATE, false)
        set(lastUsedIgnoreCalendarsState) = prefs.edit()
            .putBoolean(LAST_USED_IGNORE_CALENDARS_STATE, lastUsedIgnoreCalendarsState).apply()

    var reminderAudioStream: Int
        get() = prefs.getInt(REMINDER_AUDIO_STREAM, AudioManager.STREAM_NOTIFICATION)
        set(reminderAudioStream) = prefs.edit().putInt(REMINDER_AUDIO_STREAM, reminderAudioStream)
            .apply()

    var replaceDescription: Boolean
        get() = prefs.getBoolean(REPLACE_DESCRIPTION, false)
        set(replaceDescription) = prefs.edit().putBoolean(REPLACE_DESCRIPTION, replaceDescription)
            .apply()

    var displayDescription: Boolean
        get() = prefs.getBoolean(DISPLAY_DESCRIPTION, true)
        set(displayDescription) = prefs.edit().putBoolean(DISPLAY_DESCRIPTION, displayDescription)
            .apply()

    var showGrid: Boolean
        get() = prefs.getBoolean(SHOW_GRID, false)
        set(showGrid) = prefs.edit().putBoolean(SHOW_GRID, showGrid).apply()

    var loopReminders: Boolean
        get() = prefs.getBoolean(LOOP_REMINDERS, false)
        set(loopReminders) = prefs.edit().putBoolean(LOOP_REMINDERS, loopReminders).apply()

    var dimPastEvents: Boolean
        get() = prefs.getBoolean(DIM_PAST_EVENTS, true)
        set(dimPastEvents) = prefs.edit().putBoolean(DIM_PAST_EVENTS, dimPastEvents).apply()

    var dimCompletedTasks: Boolean
        get() = prefs.getBoolean(DIM_COMPLETED_TASKS, true)
        set(dimCompletedTasks) = prefs.edit().putBoolean(DIM_COMPLETED_TASKS, dimCompletedTasks)
            .apply()

    fun getSyncedCalendarIdsAsList() =
        caldavSyncedCalendarIds.split(",").filter { it.trim().isNotEmpty() }
            .map { Integer.parseInt(it) }.toMutableList() as ArrayList<Int>

    fun getDisplayCalendarsAsList() =
        displayCalendars.map { it.toLong() }.toMutableList() as ArrayList<Long>

    fun addDisplayCalendar(type: String) {
        addDisplayCalendars(HashSet(listOf(type)))
    }

    private fun addDisplayCalendars(types: Set<String>) {
        val currDisplayCalendars = HashSet(displayCalendars)
        currDisplayCalendars.addAll(types)
        displayCalendars = currDisplayCalendars
    }

    fun removeDisplayCalendars(types: Set<String>) {
        val currDisplayCalendars = HashSet(displayCalendars)
        currDisplayCalendars.removeAll(types)
        displayCalendars = currDisplayCalendars
    }

    var usePreviousEventReminders: Boolean
        get() = prefs.getBoolean(USE_PREVIOUS_EVENT_REMINDERS, true)
        set(usePreviousEventReminders) = prefs.edit()
            .putBoolean(USE_PREVIOUS_EVENT_REMINDERS, usePreviousEventReminders).apply()

    var defaultReminder1: Int
        get() = prefs.getInt(DEFAULT_REMINDER_1, 10)
        set(defaultReminder1) = prefs.edit().putInt(DEFAULT_REMINDER_1, defaultReminder1).apply()

    var defaultReminder2: Int
        get() = prefs.getInt(DEFAULT_REMINDER_2, REMINDER_OFF)
        set(defaultReminder2) = prefs.edit().putInt(DEFAULT_REMINDER_2, defaultReminder2).apply()

    var defaultReminder3: Int
        get() = prefs.getInt(DEFAULT_REMINDER_3, REMINDER_OFF)
        set(defaultReminder3) = prefs.edit().putInt(DEFAULT_REMINDER_3, defaultReminder3).apply()

    var pullToRefresh: Boolean
        get() = prefs.getBoolean(PULL_TO_REFRESH, false)
        set(pullToRefresh) = prefs.edit().putBoolean(PULL_TO_REFRESH, pullToRefresh).apply()

    var lastVibrateOnReminder: Boolean
        get() = prefs.getBoolean(LAST_VIBRATE_ON_REMINDER, context.config.vibrateOnReminder)
        set(lastVibrateOnReminder) = prefs.edit()
            .putBoolean(LAST_VIBRATE_ON_REMINDER, lastVibrateOnReminder).apply()

    var defaultStartTime: Int
        get() = prefs.getInt(DEFAULT_START_TIME, DEFAULT_START_TIME_NEXT_FULL_HOUR)
        set(defaultStartTime) = prefs.edit().putInt(DEFAULT_START_TIME, defaultStartTime).apply()

    var defaultDuration: Int
        get() = prefs.getInt(DEFAULT_DURATION, 0)
        set(defaultDuration) = prefs.edit().putInt(DEFAULT_DURATION, defaultDuration).apply()

    var defaultCalendarId: Long
        get() = prefs.getLong(DEFAULT_CALENDAR_ID, -1L)
        set(defaultCalendarId) = prefs.edit().putLong(DEFAULT_CALENDAR_ID, defaultCalendarId)
            .apply()

    var allowChangingTimeZones: Boolean
        get() = prefs.getBoolean(ALLOW_CHANGING_TIME_ZONES, false)
        set(allowChangingTimeZones) = prefs.edit()
            .putBoolean(ALLOW_CHANGING_TIME_ZONES, allowChangingTimeZones).apply()

    var addBirthdaysAutomatically: Boolean
        get() = prefs.getBoolean(ADD_BIRTHDAYS_AUTOMATICALLY, false)
        set(addBirthdaysAutomatically) = prefs.edit()
            .putBoolean(ADD_BIRTHDAYS_AUTOMATICALLY, addBirthdaysAutomatically).apply()

    var addAnniversariesAutomatically: Boolean
        get() = prefs.getBoolean(ADD_ANNIVERSARIES_AUTOMATICALLY, false)
        set(addAnniversariesAutomatically) = prefs.edit()
            .putBoolean(ADD_ANNIVERSARIES_AUTOMATICALLY, addAnniversariesAutomatically).apply()

    var birthdayReminders: ArrayList<Int>
        get() = prefs.getString(BIRTHDAY_REMINDERS, REMINDER_DEFAULT_VALUE)!!.split(",")
            .map { it.toInt() }.toMutableList() as ArrayList<Int>
        set(birthdayReminders) = prefs.edit()
            .putString(BIRTHDAY_REMINDERS, birthdayReminders.joinToString(",")).apply()

    var anniversaryReminders: ArrayList<Int>
        get() = prefs.getString(ANNIVERSARY_REMINDERS, REMINDER_DEFAULT_VALUE)!!.split(",")
            .map { it.toInt() }.toMutableList() as ArrayList<Int>
        set(anniversaryReminders) = prefs.edit()
            .putString(ANNIVERSARY_REMINDERS, anniversaryReminders.joinToString(",")).apply()

    var exportEvents: Boolean
        get() = prefs.getBoolean(EXPORT_EVENTS, true)
        set(exportEvents) = prefs.edit().putBoolean(EXPORT_EVENTS, exportEvents).apply()

    var exportTasks: Boolean
        get() = prefs.getBoolean(EXPORT_TASKS, true)
        set(exportTasks) = prefs.edit().putBoolean(EXPORT_TASKS, exportTasks).apply()

    var exportPastEntries: Boolean
        get() = prefs.getBoolean(EXPORT_PAST_EVENTS, true)
        set(exportPastEvents) = prefs.edit().putBoolean(EXPORT_PAST_EVENTS, exportPastEvents)
            .apply()

    var weeklyViewItemHeightMultiplier: Float
        get() = prefs.getFloat(WEEKLY_VIEW_ITEM_HEIGHT_MULTIPLIER, 1f)
        set(weeklyViewItemHeightMultiplier) = prefs.edit()
            .putFloat(WEEKLY_VIEW_ITEM_HEIGHT_MULTIPLIER, weeklyViewItemHeightMultiplier).apply()

    var weeklyViewDays: Int
        get() = prefs.getInt(WEEKLY_VIEW_DAYS, 7)
        set(weeklyViewDays) = prefs.edit().putInt(WEEKLY_VIEW_DAYS, weeklyViewDays).apply()

    // Whether the time-grid weekly view (WEEKLY_VIEW) is offered in the change-view picker.
    var showTimeGridWeekly: Boolean
        get() = prefs.getBoolean(SHOW_TIME_GRID_WEEKLY, true)
        set(showTimeGridWeekly) = prefs.edit().putBoolean(SHOW_TIME_GRID_WEEKLY, showTimeGridWeekly).apply()

    // Whether the box-grid weekly view (WEEKLY_GRID_VIEW, Pimlical-style day boxes) is offered in the change-view picker.
    var showBoxGridWeekly: Boolean
        get() = prefs.getBoolean(SHOW_BOX_GRID_WEEKLY, true)
        set(showBoxGridWeekly) = prefs.edit().putBoolean(SHOW_BOX_GRID_WEEKLY, showBoxGridWeekly).apply()

    // Date format for the box-grid week view headers: a preset sentinel (japanese / japanese_era) or a literal ICU pattern.
    var dayBoxHeaderDateFormat: String
        get() = prefs.getString(DAY_BOX_HEADER_DATE_FORMAT, DAY_BOX_HEADER_FORMAT_JAPANESE)!!
        set(dayBoxHeaderDateFormat) {
            prefs.edit().putString(DAY_BOX_HEADER_DATE_FORMAT, dayBoxHeaderDateFormat).apply()
            bumpThemeRevision() // re-render the box-grid headers when returning to the calendar
        }

    // Event time display for the week/day views: a preset sentinel (japanese) or a literal ICU time pattern.
    var eventTimeFormat: String
        get() = prefs.getString(EVENT_TIME_FORMAT, EVENT_TIME_FORMAT_JAPANESE)!!
        set(eventTimeFormat) {
            prefs.edit().putString(EVENT_TIME_FORMAT, eventTimeFormat).apply()
            bumpThemeRevision() // re-render the event times when returning to the calendar
        }

    // Per-line overlay grid lines for the box-grid week view (keyed by WeekGridLine.key).
    // Each line: enabled (default off), color (THEME_UNSET = follow the shared Grid lines color), thickness (dp).
    fun isGridLineEnabled(key: String) = prefs.getBoolean("week_grid_line_${key}_enabled", false)

    fun setGridLineEnabled(key: String, enabled: Boolean) {
        prefs.edit().putBoolean("week_grid_line_${key}_enabled", enabled).apply()
        bumpThemeRevision()
    }

    fun getGridLineColor(key: String) = prefs.getInt("week_grid_line_${key}_color", THEME_UNSET)

    fun setGridLineColor(key: String, color: Int) {
        prefs.edit().putInt("week_grid_line_${key}_color", color).apply()
        bumpThemeRevision()
    }

    fun getGridLineThickness(key: String) = prefs.getInt("week_grid_line_${key}_thickness", 1)

    fun setGridLineThickness(key: String, thickness: Int) {
        prefs.edit().putInt("week_grid_line_${key}_thickness", thickness).apply()
        bumpThemeRevision()
    }

    var highlightWeekends: Boolean
        get() = prefs.getBoolean(HIGHLIGHT_WEEKENDS, false)
        set(highlightWeekends) = prefs.edit().putBoolean(HIGHLIGHT_WEEKENDS, highlightWeekends)
            .apply()

    var highlightWeekendsColor: Int
        get() = prefs.getInt(HIGHLIGHT_WEEKENDS_COLOR, context.resources.getColor(R.color.red_text))
        set(highlightWeekendsColor) = prefs.edit()
            .putInt(HIGHLIGHT_WEEKENDS_COLOR, highlightWeekendsColor).apply()

    // Granular theming: one Int override per color slot, THEME_UNSET means "follow the default".
    var themeV1Seeded: Boolean
        get() = prefs.getBoolean(THEME_V1_SEEDED, false)
        set(value) = prefs.edit().putBoolean(THEME_V1_SEEDED, value).apply()

    var dialogStyleSeeded: Boolean
        get() = prefs.getBoolean(DIALOG_STYLE_SEEDED, false)
        set(value) = prefs.edit().putBoolean(DIALOG_STYLE_SEEDED, value).apply()

    var pureYellowMigrated: Boolean
        get() = prefs.getBoolean(PURE_YELLOW_MIGRATED, false)
        set(value) = prefs.edit().putBoolean(PURE_YELLOW_MIGRATED, value).apply()

    // Bumped whenever any UI override changes, so MainActivity can repaint the views on resume.
    var themeRevision: Int
        get() = prefs.getInt(THEME_REVISION, 0)
        set(value) = prefs.edit().putInt(THEME_REVISION, value).apply()

    private fun bumpThemeRevision() = prefs.edit().putInt(THEME_REVISION, themeRevision + 1).apply()

    fun getThemeOverride(key: String): Int = prefs.getInt(key, THEME_UNSET)

    fun setThemeOverride(key: String, color: Int) {
        prefs.edit().putInt(key, color).apply()
        bumpThemeRevision()
    }

    fun clearThemeOverride(key: String) {
        prefs.edit().remove(key).apply()
        bumpThemeRevision()
    }

    // Per-element fonts: family (filename, "" = default), weight (0 = default), size (sp, 0 = default).
    fun getFontFamily(slotKey: String): String = prefs.getString(FONT_FAMILY_PREFIX + slotKey, "")!!

    fun setFontFamily(slotKey: String, value: String) {
        prefs.edit().putString(FONT_FAMILY_PREFIX + slotKey, value).apply()
        bumpThemeRevision()
    }

    fun getFontWeight(slotKey: String): Int = prefs.getInt(FONT_WEIGHT_PREFIX + slotKey, 0)

    fun setFontWeight(slotKey: String, value: Int) {
        prefs.edit().putInt(FONT_WEIGHT_PREFIX + slotKey, value).apply()
        bumpThemeRevision()
    }

    fun getFontSize(slotKey: String): Int = prefs.getInt(FONT_SIZE_PREFIX + slotKey, 0)

    fun setFontSize(slotKey: String, value: Int) {
        prefs.edit().putInt(FONT_SIZE_PREFIX + slotKey, value).apply()
        bumpThemeRevision()
    }

    // Day-box week view: header text alignment (DAY_BOX_ALIGN_*).
    var dayBoxHeaderAlignment: Int
        get() = prefs.getInt(DAY_BOX_HEADER_ALIGNMENT, DAY_BOX_ALIGN_END)
        set(value) {
            prefs.edit().putInt(DAY_BOX_HEADER_ALIGNMENT, value).apply()
            bumpThemeRevision()
        }

    // Day-box week view: border thickness in dp, 0 = no border.
    var dayBoxBorderThickness: Int
        get() = prefs.getInt(DAY_BOX_BORDER_THICKNESS, 1)
        set(value) {
            prefs.edit().putInt(DAY_BOX_BORDER_THICKNESS, value).apply()
            bumpThemeRevision()
        }

    var dayBoxHeaderBorderThickness: Int
        get() = prefs.getInt(DAY_BOX_HEADER_BORDER_THICKNESS, 0)
        set(value) {
            prefs.edit().putInt(DAY_BOX_HEADER_BORDER_THICKNESS, value).apply()
            bumpThemeRevision()
        }

    // Today / weekend border thickness in dp; DAY_BOX_THICKNESS_INHERIT means follow the general value.
    var todayBoxBorderThickness: Int
        get() = prefs.getInt(TODAY_BOX_BORDER_THICKNESS, DAY_BOX_THICKNESS_INHERIT)
        set(value) {
            prefs.edit().putInt(TODAY_BOX_BORDER_THICKNESS, value).apply()
            bumpThemeRevision()
        }

    var todayHeaderBorderThickness: Int
        get() = prefs.getInt(TODAY_HEADER_BORDER_THICKNESS, DAY_BOX_THICKNESS_INHERIT)
        set(value) {
            prefs.edit().putInt(TODAY_HEADER_BORDER_THICKNESS, value).apply()
            bumpThemeRevision()
        }

    var weekendBoxBorderThickness: Int
        get() = prefs.getInt(WEEKEND_BOX_BORDER_THICKNESS, DAY_BOX_THICKNESS_INHERIT)
        set(value) {
            prefs.edit().putInt(WEEKEND_BOX_BORDER_THICKNESS, value).apply()
            bumpThemeRevision()
        }

    var weekendHeaderBorderThickness: Int
        get() = prefs.getInt(WEEKEND_HEADER_BORDER_THICKNESS, DAY_BOX_THICKNESS_INHERIT)
        set(value) {
            prefs.edit().putInt(WEEKEND_HEADER_BORDER_THICKNESS, value).apply()
            bumpThemeRevision()
        }

    var lastUsedEventSpan: Int
        get() = prefs.getInt(LAST_USED_EVENT_SPAN, YEAR_SECONDS)
        set(lastUsedEventSpan) = prefs.edit().putInt(LAST_USED_EVENT_SPAN, lastUsedEventSpan)
            .apply()

    var allowCreatingTasks: Boolean
        get() = prefs.getBoolean(ALLOW_CREATING_TASKS, true)
        set(allowCreatingTasks) = prefs.edit().putBoolean(ALLOW_CREATING_TASKS, allowCreatingTasks)
            .apply()

    var wasFilteredOutWarningShown: Boolean
        get() = prefs.getBoolean(WAS_FILTERED_OUT_WARNING_SHOWN, false)
        set(wasFilteredOutWarningShown) = prefs.edit()
            .putBoolean(WAS_FILTERED_OUT_WARNING_SHOWN, wasFilteredOutWarningShown).apply()

    var autoBackupCalendars: Set<String>
        get() = prefs.getStringSet(AUTO_BACKUP_CALENDARS, HashSet())!!
        set(autoBackupCalendars) = prefs.edit().remove(AUTO_BACKUP_CALENDARS)
            .putStringSet(AUTO_BACKUP_CALENDARS, autoBackupCalendars).apply()

    var autoBackupEvents: Boolean
        get() = prefs.getBoolean(AUTO_BACKUP_EVENTS, true)
        set(autoBackupEvents) = prefs.edit().putBoolean(AUTO_BACKUP_EVENTS, autoBackupEvents)
            .apply()

    var autoBackupTasks: Boolean
        get() = prefs.getBoolean(AUTO_BACKUP_TASKS, true)
        set(autoBackupTasks) = prefs.edit().putBoolean(AUTO_BACKUP_TASKS, autoBackupTasks).apply()

    var autoBackupPastEntries: Boolean
        get() = prefs.getBoolean(AUTO_BACKUP_PAST_ENTRIES, true)
        set(autoBackupPastEntries) = prefs.edit()
            .putBoolean(AUTO_BACKUP_PAST_ENTRIES, autoBackupPastEntries).apply()

    var lastUsedShowListWidgetHeader: Boolean
        get() = prefs.getBoolean(LAST_USED_SHOW_LIST_WIDGET_HEADER, true)
        set(lastUsedShowListWidgetHeader) = prefs.edit()
            .putBoolean(LAST_USED_SHOW_LIST_WIDGET_HEADER, lastUsedShowListWidgetHeader).apply()

    var widgetShowGrid: Boolean
        get() = prefs.getBoolean(WIDGET_SHOW_GRID, false)
        set(widgetShowGrid) = prefs.edit().putBoolean(WIDGET_SHOW_GRID, widgetShowGrid).apply()

    // External-automation intent surface (receivers/StateExportReceiver): a master switch plus a shared
    // secret that every automation broadcast must carry. Same model as the renrakusaki fork's Config and
    // the 自由作業盤 fork's AutomationAuth. Both keys are device-local — SettingsTransfer excludes them
    // from every export, so the token never travels in a backup ZIP.
    var automationEnabled: Boolean
        get() = prefs.getBoolean(AUTOMATION_ENABLED, false)
        set(automationEnabled) = prefs.edit().putBoolean(AUTOMATION_ENABLED, automationEnabled).apply()

    /** The shared secret; generated on first read so the settings row always shows a value. */
    val automationToken: String
        get() = prefs.getString(AUTOMATION_TOKEN, null)?.takeIf { it.isNotEmpty() }
            ?: regenerateAutomationToken()

    fun regenerateAutomationToken(): String {
        val bytes = ByteArray(AUTOMATION_TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(AUTOMATION_TOKEN, token).apply()
        return token
    }

    /**
     * True when the caller's token matches the stored secret, compared in constant time. The enabled
     * check is deliberately separate so callers can report "disabled" and "bad token" distinctly —
     * they debug differently.
     */
    fun isAutomationTokenValid(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        return MessageDigest.isEqual(token.toByteArray(), automationToken.toByteArray())
    }
}
