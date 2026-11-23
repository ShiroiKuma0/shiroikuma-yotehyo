package org.fossify.calendar.extensions

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.calendar.R
import org.fossify.calendar.helpers.DAY_BOX_THICKNESS_INHERIT
import org.fossify.calendar.helpers.DIALOG_BORDER_WIDTH_DEFAULT
import androidx.core.graphics.ColorUtils
import org.fossify.calendar.helpers.PALETTE_BLACK
import org.fossify.calendar.helpers.PALETTE_BLUE
import org.fossify.calendar.helpers.PALETTE_WARNING
import org.fossify.calendar.helpers.PALETTE_WEEKEND
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.fossify.commons.helpers.resetAllUiColors
import org.fossify.calendar.helpers.PALETTE_ORANGE
import org.fossify.calendar.helpers.PALETTE_DEEP_BLUE
import org.fossify.calendar.helpers.PALETTE_DEEP_GREEN
import org.fossify.calendar.helpers.PALETTE_RED
import org.fossify.calendar.helpers.PALETTE_ROSE
import org.fossify.calendar.helpers.PALETTE_SKY
import org.fossify.calendar.helpers.PALETTE_YELLOW
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.calendar.helpers.TODAY_BOX_BORDER_THICKNESS_DEFAULT
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.isUiColorSet
import org.fossify.commons.helpers.resetUiColor
import org.fossify.commons.helpers.setUiColor
import org.fossify.commons.helpers.uiColor
import org.fossify.commons.helpers.uiDefaultColor

// Granular, per-element theming for 白い熊 予定表.
//
// Each [ThemeSlot] is one customizable color. Foundation slots reuse the stock commons colors
// (background / primary / text); every other slot inherits from a foundation slot by default
// (two-tier), so the whole app stays coherent and a single foundation change cascades. A slot
// only diverges once the user gives it an explicit override (stored as an Int; THEME_UNSET means
// "follow the default"). The default look is seeded to black background + yellow text/accents.

enum class ThemeGroup(@StringRes val labelRes: Int) {
    FOUNDATION(R.string.theme_group_foundation),
    SEARCH(R.string.theme_group_search),
    CHROME(R.string.theme_group_chrome),
    CALENDAR(R.string.theme_group_calendar),
    MENUS(R.string.theme_group_menus),
    SETTINGS_PAGE(R.string.theme_group_settings_page),

    // Items of this app that belong in a section the commons' roles open (shown there, beside them)
    TEXT_STATUS(R.string.theme_group_foundation),
    BUTTONS(R.string.theme_group_foundation),
    DIALOGS(R.string.theme_group_dialogs),
    SWITCHES(R.string.theme_group_foundation),
    VIEWS(R.string.theme_sub_views),
    YEAR(R.string.theme_sub_year_view),
    EVENT_LIST(R.string.theme_sub_event_list),
    EDITOR(R.string.theme_sub_editor),
    LISTS(R.string.theme_sub_calendar_lists),
    EXIM_PANEL(R.string.theme_sub_exim_panel),
    FONT_PICKER(R.string.theme_sub_font_picker),
    WIDGET_SETUP(R.string.theme_sub_widget_setup),
    SECTION_ICONS(R.string.theme_sub_section_icons),
}

enum class ThemeSlot(
    val key: String,
    val group: ThemeGroup,
    @StringRes val labelRes: Int,
    val isFoundation: Boolean = false,
    // hasFont = true for concrete text elements (family / weight / size are configurable per element)
    val hasFont: Boolean = false,
    // A slot with a role IS that commons role (helpers/UiRoles.kt) under this app's label and section:
    // it is read and stored as the role, so what the commons draws and what this app draws agree.
    val role: UiRole? = null,
) {
    // Foundation — reuse the stock commons colors (editing these repaints the whole app)
    BACKGROUND("theme_background", ThemeGroup.FOUNDATION, R.string.theme_background, isFoundation = true, role = UiRole.BACKGROUND),
    PRIMARY("theme_primary", ThemeGroup.FOUNDATION, R.string.theme_primary, isFoundation = true, role = UiRole.ACCENT),
    TEXT("theme_text", ThemeGroup.FOUNDATION, R.string.theme_text, isFoundation = true, hasFont = true, role = UiRole.TEXT),
    TEXT_SECONDARY("theme_text_secondary", ThemeGroup.FOUNDATION, R.string.theme_text_secondary, hasFont = true),

    // Search bar (the top MySearchMenu)
    SEARCH_FILL("theme_search_fill", ThemeGroup.SEARCH, R.string.theme_search_fill, role = UiRole.SEARCH_GROUND),
    SEARCH_TEXT("theme_search_text", ThemeGroup.SEARCH, R.string.theme_search_text, hasFont = true, role = UiRole.SEARCH_TEXT),
    SEARCH_HINT("theme_search_hint", ThemeGroup.SEARCH, R.string.theme_search_hint, role = UiRole.SEARCH_HINT),
    SEARCH_ICON("theme_search_icon", ThemeGroup.SEARCH, R.string.theme_search_icon, role = UiRole.SEARCH_ICON),
    SEARCH_BORDER("theme_search_border", ThemeGroup.SEARCH, R.string.theme_search_border, role = UiRole.SEARCH_FRAME),

    // Top bar (action / overflow icons), overflow menu text, and the Settings toolbar foreground
    TOOLBAR_ICONS("theme_toolbar_icons", ThemeGroup.CHROME, R.string.theme_toolbar_icons, role = UiRole.TOOLBAR_ICON),
    MENU_TEXT("theme_menu_text", ThemeGroup.CHROME, R.string.theme_menu_text, role = UiRole.MENU_LABEL),
    SETTINGS_TITLE("theme_settings_title", ThemeGroup.CHROME, R.string.theme_settings_title, role = UiRole.TOOLBAR_TITLE),
    VIEW_SWITCHER("theme_view_switcher", ThemeGroup.CHROME, R.string.theme_view_switcher, hasFont = true),

    // Calendar surfaces
    EVENT_TEXT("theme_event_text", ThemeGroup.CALENDAR, R.string.theme_event_text, hasFont = true),
    TODAY_HIGHLIGHT("theme_today_highlight", ThemeGroup.CALENDAR, R.string.theme_today_highlight),
    WEEKEND("theme_weekend", ThemeGroup.CALENDAR, R.string.theme_weekend),
    GRID_LINES("theme_grid_lines", ThemeGroup.CALENDAR, R.string.theme_grid_lines),
    DAY_BOX_HEADER("theme_day_box_header", ThemeGroup.CALENDAR, R.string.theme_day_box_header),
    DAY_BOX_HEADER_TEXT("theme_day_box_header_text", ThemeGroup.CALENDAR, R.string.theme_day_box_header_text, hasFont = true),
    DAY_BOX_HEADER_BORDER("theme_day_box_header_border", ThemeGroup.CALENDAR, R.string.theme_day_box_header_border),
    DAY_BOX_BORDER("theme_day_box_border", ThemeGroup.CALENDAR, R.string.theme_day_box_border),

    // Today / weekend day-box overrides (inherit the general day-box slots by default)
    TODAY_BOX_FILL("theme_today_box_fill", ThemeGroup.CALENDAR, R.string.theme_today_box_fill),
    TODAY_TEXT("theme_today_text", ThemeGroup.CALENDAR, R.string.theme_today_text, hasFont = true),
    TODAY_HEADER_BORDER("theme_today_header_border", ThemeGroup.CALENDAR, R.string.theme_today_header_border),
    TODAY_BOX_BORDER("theme_today_box_border", ThemeGroup.CALENDAR, R.string.theme_today_box_border),
    WEEKEND_TEXT("theme_weekend_text", ThemeGroup.CALENDAR, R.string.theme_weekend_text, hasFont = true),
    WEEKEND_HEADER_BORDER("theme_weekend_header_border", ThemeGroup.CALENDAR, R.string.theme_weekend_header_border),
    WEEKEND_BOX_BORDER("theme_weekend_box_border", ThemeGroup.CALENDAR, R.string.theme_weekend_box_border),

    // 休み (days off): the entry itself, whatever calendar it is filed under, and its day's frame + date
    DAY_OFF_ENTRY_TEXT("theme_day_off_entry_text", ThemeGroup.CALENDAR, R.string.theme_day_off_entry_text, hasFont = true),
    DAY_OFF_ENTRY_BACKGROUND("theme_day_off_entry_background", ThemeGroup.CALENDAR, R.string.theme_day_off_entry_background),
    DAY_OFF_FRAME("theme_day_off_frame", ThemeGroup.CALENDAR, R.string.theme_day_off_frame),
    DAY_OFF_DATE_TEXT("theme_day_off_date_text", ThemeGroup.CALENDAR, R.string.theme_day_off_date_text),

    // Time zones: the 発 / 着 / 現地 lines under an event, the editor summary and the travel banner
    // (dim gold), and the outlined zone tags plus 翌日 (accent)
    TIME_ZONE_TEXT("theme_time_zone_text", ThemeGroup.CALENDAR, R.string.theme_time_zone_text, hasFont = true),
    TIME_ZONE_TAG("theme_time_zone_tag", ThemeGroup.CALENDAR, R.string.theme_time_zone_tag),

    // Menus: every action row is an outlined pill whose frame and label stay in the accent, while its
    // icon says by colour what the row does (helpers/MenuPills, views/PillMenuItemView). A destructive
    // row lends the delete colour to its frame and label as well.
    MENU_ROLE_CONTACT("theme_menu_role_contact", ThemeGroup.MENUS, R.string.theme_menu_role_contact),
    MENU_ROLE_SHARE("theme_menu_role_share", ThemeGroup.MENUS, R.string.theme_menu_role_share),
    MENU_ROLE_VIEW("theme_menu_role_view", ThemeGroup.MENUS, R.string.theme_menu_role_view),
    MENU_ROLE_DELETE("theme_menu_role_delete", ThemeGroup.MENUS, R.string.theme_menu_role_delete),
    MENU_ROLE_CONNECT("theme_menu_role_connect", ThemeGroup.MENUS, R.string.theme_menu_role_connect),
    MENU_ROLE_BLOCK("theme_menu_role_block", ThemeGroup.MENUS, R.string.theme_menu_role_block),

    // Settings pages: the dim gold of every explanation and of the thin rule beside it, and the darker
    // (opaque, never faded) yellow of a switch that is off
    SETTINGS_NOTE("theme_settings_note", ThemeGroup.SETTINGS_PAGE, R.string.theme_settings_note),
    SETTINGS_HEADING("theme_settings_heading", ThemeGroup.SETTINGS_PAGE, R.string.theme_settings_heading),
    SETTINGS_VALUE("theme_settings_value", ThemeGroup.SETTINGS_PAGE, R.string.theme_settings_value),
    SWITCH_OFF("theme_switch_off", ThemeGroup.SWITCHES, R.string.theme_switch_off, role = UiRole.SWITCH_OFF_OUTLINE),

    // A warning that is only read: "no export yet", an end before its start
    STATUS_WARNING("theme_status_warning", ThemeGroup.TEXT_STATUS, R.string.theme_status_warning),

    // The + menu: the two pills above the round button, and the veil over the screen behind them
    FAB_MENU_TEXT("theme_fab_menu_text", ThemeGroup.BUTTONS, R.string.theme_fab_menu_text),
    FAB_MENU_FRAME("theme_fab_menu_frame", ThemeGroup.BUTTONS, R.string.theme_fab_menu_frame),
    FAB_MENU_GROUND("theme_fab_menu_ground", ThemeGroup.BUTTONS, R.string.theme_fab_menu_ground),
    FAB_MENU_SCRIM("theme_fab_menu_scrim", ThemeGroup.BUTTONS, R.string.theme_fab_menu_scrim),
    FAB_MENU_PANEL_FRAME("theme_fab_menu_panel_frame", ThemeGroup.BUTTONS, R.string.theme_fab_menu_panel_frame),
    FAB_MENU_PANEL_GROUND("theme_fab_menu_panel_ground", ThemeGroup.BUTTONS, R.string.theme_fab_menu_panel_ground),

    // The UI page's section icons, on heading and chip: the colour says what kind of thing the section is
    SECTION_BASE("theme_section_base", ThemeGroup.SECTION_ICONS, R.string.theme_section_base),
    SECTION_CONTROLS("theme_section_controls", ThemeGroup.SECTION_ICONS, R.string.theme_section_controls),
    SECTION_WORDS("theme_section_words", ThemeGroup.SECTION_ICONS, R.string.theme_section_words),
    SECTION_FRAME("theme_section_frame", ThemeGroup.SECTION_ICONS, R.string.theme_section_frame),
    SECTION_REACH("theme_section_reach", ThemeGroup.SECTION_ICONS, R.string.theme_section_reach),
    SECTION_WARNING("theme_section_warning", ThemeGroup.SECTION_ICONS, R.string.theme_section_warning),
    SECTION_FLASH("theme_section_flash", ThemeGroup.SECTION_ICONS, R.string.theme_section_flash),

    // The keyboard time entry: one box per digit
    TIME_CELL_FRAME("theme_time_cell_frame", ThemeGroup.DIALOGS, R.string.theme_time_cell_frame),
    TIME_CELL_GROUND("theme_time_cell_ground", ThemeGroup.DIALOGS, R.string.theme_time_cell_ground),

    // What every calendar view shares
    VIEW_GROUND("theme_view_ground", ThemeGroup.VIEWS, R.string.theme_view_ground),
    NAV_TITLE("theme_nav_title", ThemeGroup.VIEWS, R.string.theme_nav_title),
    NAV_ARROW("theme_nav_arrow", ThemeGroup.VIEWS, R.string.theme_nav_arrow),
    DAY_NUMBER("theme_day_number", ThemeGroup.VIEWS, R.string.theme_day_number),
    WEEK_NUMBER("theme_week_number", ThemeGroup.VIEWS, R.string.theme_week_number),
    HOUR_LABEL("theme_hour_label", ThemeGroup.VIEWS, R.string.theme_hour_label),
    NEW_EVENT_MARK("theme_new_event_mark", ThemeGroup.VIEWS, R.string.theme_new_event_mark),
    SELECTED_DAY_RING("theme_selected_day_ring", ThemeGroup.VIEWS, R.string.theme_selected_day_ring),

    // The year view
    YEAR_MONTH_LABEL("theme_year_month_label", ThemeGroup.YEAR, R.string.theme_year_month_label),
    YEAR_CURRENT_MONTH("theme_year_current_month", ThemeGroup.YEAR, R.string.theme_year_current_month),
    YEAR_DAY_NUMBER("theme_year_day_number", ThemeGroup.YEAR, R.string.theme_year_day_number),
    YEAR_TODAY_MARK("theme_year_today_mark", ThemeGroup.YEAR, R.string.theme_year_today_mark),

    // The event list and a day's list
    LIST_ITEM_TEXT("theme_list_item_text", ThemeGroup.EVENT_LIST, R.string.theme_list_item_text),
    LIST_PLACEHOLDER("theme_list_placeholder", ThemeGroup.EVENT_LIST, R.string.theme_list_placeholder),
    LIST_NEW_EVENT_LINK("theme_list_new_event_link", ThemeGroup.EVENT_LIST, R.string.theme_list_new_event_link),

    // The event and task editors
    EDITOR_ICON("theme_editor_icon", ThemeGroup.EDITOR, R.string.theme_editor_icon),
    EDITOR_ICON_PEOPLE("theme_editor_icon_people", ThemeGroup.EDITOR, R.string.theme_editor_icon_people),
    EDITOR_MAP_ICON("theme_editor_map_icon", ThemeGroup.EDITOR, R.string.theme_editor_map_icon),

    // Lists of calendars and time zones, the quick filter, and the ring round every colour drop
    CALENDAR_LIST_TITLE("theme_calendar_list_title", ThemeGroup.LISTS, R.string.theme_calendar_list_title),
    CALENDAR_LIST_ICON("theme_calendar_list_icon", ThemeGroup.LISTS, R.string.theme_calendar_list_icon),
    SWATCH_RING("theme_swatch_ring", ThemeGroup.LISTS, R.string.theme_swatch_ring),
    SWATCH_RING_EMPTY("theme_swatch_ring_empty", ThemeGroup.LISTS, R.string.theme_swatch_ring_empty),
    FILTER_CHIP_TEXT("theme_filter_chip_text", ThemeGroup.LISTS, R.string.theme_filter_chip_text),
    FILTER_CHIP_TEXT_OFF("theme_filter_chip_text_off", ThemeGroup.LISTS, R.string.theme_filter_chip_text_off),
    TIME_ZONE_LIST_TEXT("theme_time_zone_list_text", ThemeGroup.LISTS, R.string.theme_time_zone_list_text),

    // The Export / Import panel
    EXIM_TEXT("theme_exim_text", ThemeGroup.EXIM_PANEL, R.string.theme_exim_text),
    EXIM_CAPTION("theme_exim_caption", ThemeGroup.EXIM_PANEL, R.string.theme_exim_caption),
    EXIM_BOX_FRAME("theme_exim_box_frame", ThemeGroup.EXIM_PANEL, R.string.theme_exim_box_frame),
    EXIM_BOX_GROUND("theme_exim_box_ground", ThemeGroup.EXIM_PANEL, R.string.theme_exim_box_ground),
    EXIM_DIVIDER("theme_exim_divider", ThemeGroup.EXIM_PANEL, R.string.theme_exim_divider),

    // The font picker
    FONT_OPTION_TEXT("theme_font_option_text", ThemeGroup.FONT_PICKER, R.string.theme_font_option_text),
    FONT_ADD_TEXT("theme_font_add_text", ThemeGroup.FONT_PICKER, R.string.theme_font_add_text),

    // The widget setup screens
    WIDGET_SETUP_GROUND("theme_widget_setup_ground", ThemeGroup.WIDGET_SETUP, R.string.theme_widget_setup_ground),
    WIDGET_SAMPLE_EVENT("theme_widget_sample_event", ThemeGroup.WIDGET_SETUP, R.string.theme_widget_sample_event),
    WIDGET_SAMPLE_TODAY("theme_widget_sample_today", ThemeGroup.WIDGET_SETUP, R.string.theme_widget_sample_today),
}

private const val NOTE_BLEND = 0.784f
private const val SCRIM_ALPHA = 0.8f
private const val EXIM_DIVIDER_ALPHA = 0.4f

/** The effective color for a slot: the user's override if set, otherwise its inherited default. */
fun Context.themeColor(slot: ThemeSlot): Int {
    slot.role?.let { return uiColor(it) }
    val override = config.getThemeOverride(slot.key)
    return if (override != THEME_UNSET) override else themeDefault(slot)
}

private fun Context.themeDefault(slot: ThemeSlot): Int = when (slot) {
    // A slot that is a commons role has the role's default (themeColor never comes here for one)
    ThemeSlot.BACKGROUND, ThemeSlot.PRIMARY, ThemeSlot.TEXT,
    ThemeSlot.SEARCH_FILL, ThemeSlot.SEARCH_TEXT, ThemeSlot.SEARCH_HINT, ThemeSlot.SEARCH_ICON, ThemeSlot.SEARCH_BORDER,
    ThemeSlot.TOOLBAR_ICONS, ThemeSlot.MENU_TEXT, ThemeSlot.SETTINGS_TITLE, ThemeSlot.SWITCH_OFF -> uiDefaultColor(slot.role!!)
    ThemeSlot.TEXT_SECONDARY -> themeColor(ThemeSlot.TEXT).adjustAlpha(0.6f)


    // Top bar / menu chrome
    ThemeSlot.VIEW_SWITCHER -> themeColor(ThemeSlot.TOOLBAR_ICONS)

    // Calendar: today markers follow the accent; weekend reuses the stock weekend color;
    // grid lines inherit the text color at the same low alpha the views already use.
    ThemeSlot.EVENT_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.TODAY_HIGHLIGHT -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.WEEKEND -> config.highlightWeekendsColor
    ThemeSlot.GRID_LINES -> themeColor(ThemeSlot.TEXT).adjustAlpha(LOWER_ALPHA)
    // Today box fill exposes the formerly hard-coded 12% wash so its colour + alpha are editable.
    ThemeSlot.TODAY_BOX_FILL -> themeColor(ThemeSlot.TODAY_HIGHLIGHT).adjustAlpha(0.12f)
    // Day-box week view header bars default to the accent (today/weekend get their own slots).
    ThemeSlot.DAY_BOX_HEADER -> themeColor(ThemeSlot.PRIMARY)
    // Header text defaults to a readable contrast of the header background; box borders to grid lines.
    ThemeSlot.DAY_BOX_HEADER_TEXT -> themeColor(ThemeSlot.DAY_BOX_HEADER).getContrastColor()
    ThemeSlot.DAY_BOX_BORDER -> themeColor(ThemeSlot.GRID_LINES)
    ThemeSlot.DAY_BOX_HEADER_BORDER -> themeColor(ThemeSlot.DAY_BOX_BORDER)

    // Today / weekend day-box slots inherit the general defaults until overridden. Their text follows
    // the day-box header text (color + font, see fontParent) so they match unless given their own.
    ThemeSlot.TODAY_TEXT -> themeColor(ThemeSlot.DAY_BOX_HEADER_TEXT)
    ThemeSlot.TODAY_HEADER_BORDER -> themeColor(ThemeSlot.DAY_BOX_HEADER_BORDER)
    ThemeSlot.TODAY_BOX_BORDER -> themeColor(ThemeSlot.DAY_BOX_BORDER)
    ThemeSlot.WEEKEND_TEXT -> themeColor(ThemeSlot.DAY_BOX_HEADER_TEXT)
    ThemeSlot.WEEKEND_HEADER_BORDER -> themeColor(ThemeSlot.DAY_BOX_HEADER_BORDER)
    ThemeSlot.WEEKEND_BOX_BORDER -> themeColor(ThemeSlot.DAY_BOX_BORDER)

    // 休み defaults: yellow text on the weekend blue, and a day drawn exactly like a weekend day
    ThemeSlot.DAY_OFF_ENTRY_TEXT -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.DAY_OFF_ENTRY_BACKGROUND -> themeColor(ThemeSlot.WEEKEND_BOX_BORDER)
    ThemeSlot.DAY_OFF_FRAME -> themeColor(ThemeSlot.WEEKEND_BOX_BORDER)
    ThemeSlot.DAY_OFF_DATE_TEXT -> themeColor(ThemeSlot.WEEKEND_TEXT)

    ThemeSlot.TIME_ZONE_TEXT -> dimmedColor(themeColor(ThemeSlot.TEXT))
    ThemeSlot.TIME_ZONE_TAG -> themeColor(ThemeSlot.PRIMARY)

    ThemeSlot.MENU_ROLE_CONTACT -> PALETTE_SKY
    ThemeSlot.MENU_ROLE_SHARE -> PALETTE_ORANGE
    ThemeSlot.MENU_ROLE_VIEW -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.MENU_ROLE_DELETE -> PALETTE_RED
    ThemeSlot.MENU_ROLE_CONNECT -> PALETTE_DEEP_GREEN
    ThemeSlot.MENU_ROLE_BLOCK -> PALETTE_ROSE

    ThemeSlot.STATUS_WARNING -> PALETTE_WARNING

    ThemeSlot.FAB_MENU_TEXT -> uiColor(UiRole.BUTTON_TEXT)
    ThemeSlot.FAB_MENU_FRAME -> uiColor(UiRole.BUTTON_FRAME)
    ThemeSlot.FAB_MENU_GROUND -> uiColor(UiRole.BUTTON_GROUND)
    ThemeSlot.FAB_MENU_SCRIM -> themeColor(ThemeSlot.BACKGROUND).adjustAlpha(SCRIM_ALPHA)
    ThemeSlot.FAB_MENU_PANEL_FRAME -> uiColor(UiRole.MENU_FRAME)
    ThemeSlot.FAB_MENU_PANEL_GROUND -> uiColor(UiRole.MENU_GROUND)
    ThemeSlot.SECTION_BASE -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.SECTION_CONTROLS -> PALETTE_DEEP_GREEN
    ThemeSlot.SECTION_WORDS -> PALETTE_SKY
    ThemeSlot.SECTION_FRAME -> PALETTE_DEEP_BLUE
    ThemeSlot.SECTION_REACH -> PALETTE_ORANGE
    ThemeSlot.SECTION_WARNING -> PALETTE_RED
    ThemeSlot.SECTION_FLASH -> PALETTE_ROSE

    ThemeSlot.TIME_CELL_FRAME -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.TIME_CELL_GROUND -> uiColor(UiRole.DIALOG_GROUND)

    ThemeSlot.VIEW_GROUND -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.NAV_TITLE, ThemeSlot.NAV_ARROW, ThemeSlot.DAY_NUMBER, ThemeSlot.WEEK_NUMBER, ThemeSlot.HOUR_LABEL,
    ThemeSlot.YEAR_MONTH_LABEL, ThemeSlot.LIST_ITEM_TEXT, ThemeSlot.LIST_PLACEHOLDER -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.NEW_EVENT_MARK, ThemeSlot.YEAR_CURRENT_MONTH, ThemeSlot.LIST_NEW_EVENT_LINK,
    ThemeSlot.EDITOR_MAP_ICON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.YEAR_DAY_NUMBER -> themeColor(ThemeSlot.DAY_NUMBER).adjustAlpha(MEDIUM_ALPHA)
    ThemeSlot.YEAR_TODAY_MARK -> themeColor(ThemeSlot.PRIMARY).adjustAlpha(MEDIUM_ALPHA)

    ThemeSlot.EDITOR_ICON -> themeColor(ThemeSlot.MENU_ROLE_VIEW)

    ThemeSlot.CALENDAR_LIST_TITLE, ThemeSlot.CALENDAR_LIST_ICON, ThemeSlot.FILTER_CHIP_TEXT,
    ThemeSlot.TIME_ZONE_LIST_TEXT, ThemeSlot.FONT_OPTION_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.FILTER_CHIP_TEXT_OFF -> themeColor(ThemeSlot.FILTER_CHIP_TEXT).adjustAlpha(LOWER_ALPHA)
    ThemeSlot.SWATCH_RING, ThemeSlot.WIDGET_SETUP_GROUND -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.SETTINGS_HEADING, ThemeSlot.SETTINGS_VALUE, ThemeSlot.SELECTED_DAY_RING,
    ThemeSlot.SWATCH_RING_EMPTY, ThemeSlot.EXIM_CAPTION, ThemeSlot.EXIM_BOX_FRAME, ThemeSlot.FONT_ADD_TEXT,
    ThemeSlot.WIDGET_SAMPLE_EVENT, ThemeSlot.WIDGET_SAMPLE_TODAY -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.EXIM_TEXT -> uiColor(UiRole.DIALOG_TEXT)
    ThemeSlot.EXIM_BOX_GROUND -> uiColor(UiRole.DIALOG_GROUND)
    ThemeSlot.EXIM_DIVIDER -> themeColor(ThemeSlot.PRIMARY).adjustAlpha(EXIM_DIVIDER_ALPHA)
    ThemeSlot.EDITOR_ICON_PEOPLE -> themeColor(ThemeSlot.MENU_ROLE_CONTACT)

    // #C8C800 on the black/yellow palette: 78 % of the way from the background to the text
    ThemeSlot.SETTINGS_NOTE -> ColorUtils.blendARGB(themeColor(ThemeSlot.BACKGROUND), themeColor(ThemeSlot.TEXT), NOTE_BLEND)
}

/** Set an explicit override for a slot. A role slot is stored as its commons role. */
fun Context.setThemeColor(slot: ThemeSlot, color: Int) {
    slot.role?.let {
        setUiColor(it, color)
        config.themeRevision = config.themeRevision + 1
        return
    }
    when (slot) {
        // Weekend reuses the existing single source of truth so the Settings picker stays in sync.
        ThemeSlot.WEEKEND -> config.highlightWeekendsColor = color

        else -> config.setThemeOverride(slot.key, color)
    }
}

/** Revert a slot to its default (the role's own for a role slot, inherited otherwise). */
fun Context.resetThemeColor(slot: ThemeSlot) {
    slot.role?.let {
        resetUiColor(it)
        config.themeRevision = config.themeRevision + 1
        return
    }
    when (slot) {
        ThemeSlot.WEEKEND -> config.highlightWeekendsColor = PALETTE_WEEKEND
        else -> config.clearThemeOverride(slot.key)
    }
}

/** One-time seed of the default black/yellow look across the whole app (via the stock colors). */
fun Context.seedBlackYellowThemeIfNeeded() {
    if (config.themeV1Seeded) {
        return
    }

    config.backgroundColor = PALETTE_BLACK
    config.textColor = PALETTE_YELLOW
    config.primaryColor = PALETTE_YELLOW
    config.accentColor = PALETTE_YELLOW
    config.themeV1Seeded = true
}

/**
 * One-time seed of the dialog frame's width and the boxed button style. These live in Commons'
 * BaseConfig so setupDialogStuff can read them. The frame's colour is the commons role
 * UiRole.DIALOG_FRAME and is not seeded: it follows the accent until it is set on the UI page.
 */
fun Context.seedDialogStyleIfNeeded() {
    if (config.dialogStyleSeeded) {
        return
    }

    config.dialogBorderWidth = DIALOG_BORDER_WIDTH_DEFAULT
    config.styledDialogButtons = true
    config.dialogStyleSeeded = true
}

/**
 * One-time switch to commons' pill dialog buttons (sk12): the positive button becomes the main
 * outlined pill (3 dp, bold), every other button a thin one — never filled. It takes precedence over
 * the older boxed buttons and can be turned off again under Dialogs in 白い熊 予定表 UI.
 */
fun Context.seedPillDialogButtonsIfNeeded() {
    if (config.pillDialogSeeded) {
        return
    }

    config.pillDialogButtons = true
    config.pillDialogSeeded = true
}

/**
 * One-time seed of the today box border: a blue stroke around today's day box, thick enough to find
 * at a glance against the black/yellow palette. The monthly and the weekly view share these two
 * settings, so today is marked the same way in both. Seeded only while the slot and its thickness
 * are still untouched, so an existing customization is never overwritten; adjustable afterwards
 * under 今日のボックス in 白い熊 予定表 UI.
 */
fun Context.seedTodayBoxBorderIfNeeded() {
    if (config.todayBoxBorderSeeded) {
        return
    }

    config.todayBoxBorderSeeded = true
    val isUntouched = config.getThemeOverride(ThemeSlot.TODAY_BOX_BORDER.key) == THEME_UNSET &&
        config.todayBoxBorderThickness == DAY_BOX_THICKNESS_INHERIT
    if (!isUntouched) {
        return
    }

    setThemeColor(ThemeSlot.TODAY_BOX_BORDER, PALETTE_BLUE)
    config.todayBoxBorderThickness = TODAY_BOX_BORDER_THICKNESS_DEFAULT
}

/**
 * One-time migration of every persisted color whose RGB is the old material yellow (#FFEB3B) to the
 * pure yellow PALETTE_YELLOW (#FFFF00), preserving the alpha byte. Covers the seeded stock commons
 * colors, the dialog border, the widget colors, the weekend highlight, every ThemeSlot override and
 * every week grid line override. Bumps the theme revision so already-themed views repaint.
 */
fun Context.migrateToPureYellowIfNeeded() {
    if (config.pureYellowMigrated) {
        return
    }

    val oldYellowRgb = 0xFFEB3B // the pre-migration PALETTE_YELLOW, without its alpha byte
    fun Int.migrated() = if (this and 0xFFFFFF == oldYellowRgb) {
        (this and 0xFF000000.toInt()) or (PALETTE_YELLOW and 0xFFFFFF)
    } else {
        this
    }

    // Stock commons colors (seeded by seedBlackYellowThemeIfNeeded).
    config.backgroundColor = config.backgroundColor.migrated()
    config.textColor = config.textColor.migrated()
    config.primaryColor = config.primaryColor.migrated()
    config.accentColor = config.accentColor.migrated()

    // Widget colors and the weekend highlight (single source of truth for the WEEKEND slot).
    config.widgetBgColor = config.widgetBgColor.migrated()
    config.widgetTextColor = config.widgetTextColor.migrated()
    config.highlightWeekendsColor = config.highlightWeekendsColor.migrated()

    // Explicit per-slot and per-grid-line overrides; THEME_UNSET slots keep following their defaults.
    ThemeSlot.entries.forEach { slot ->
        val override = config.getThemeOverride(slot.key)
        if (override != THEME_UNSET && override.migrated() != override) {
            config.setThemeOverride(slot.key, override.migrated())
        }
    }
    WeekGridLine.entries.forEach { line ->
        val color = config.getGridLineColor(line.key)
        if (color != THEME_UNSET && color.migrated() != color) {
            config.setGridLineColor(line.key, color.migrated())
        }
    }

    // Repaint everything with the new yellow.
    config.themeRevision = config.themeRevision + 1
    config.pureYellowMigrated = true
}

/**
 * The thin box round every day in 週 and 月: the day box border slot when it carries an explicit
 * override, else the accent. Its inherited default is the dim grid-line colour, which reads as no box.
 */
fun Context.regularDayBoxBorderColor(): Int =
    if (config.getThemeOverride(ThemeSlot.DAY_BOX_BORDER.key) != THEME_UNSET) {
        themeColor(ThemeSlot.DAY_BOX_BORDER)
    } else {
        themeColor(ThemeSlot.PRIMARY)
    }

/**
 * The move to the commons roles (sk18). An override stored under a slot's own key moves to the key of
 * the role the slot now is — at every start and after every import, so a settings export written by
 * an older build still lands. Once only, the dialog frame that earlier builds seeded as plain yellow
 * goes back to following the accent.
 */
fun Context.migrateSlotsToRoles() {
    var moved = false
    ThemeSlot.entries.filter { it.role != null && !it.isFoundation }.forEach { slot ->
        val override = config.getThemeOverride(slot.key)
        if (override != THEME_UNSET) {
            setUiColor(slot.role!!, override)
            config.clearThemeOverride(slot.key)
            moved = true
        }
    }
    if (!config.slotsToRolesMigrated) {
        if (isUiColorSet(UiRole.DIALOG_FRAME) && uiColor(UiRole.DIALOG_FRAME) == PALETTE_YELLOW) {
            resetUiColor(UiRole.DIALOG_FRAME)
        }
        config.slotsToRolesMigrated = true
        moved = true
    }
    if (moved) config.themeRevision = config.themeRevision + 1
}

/**
 * Every colour of the page back to its default: the commons roles, this app's own slots, the weekend
 * colour and the grid lines. Fonts, widths and switches are left as they are.
 */
fun Context.resetAllThemeColors() {
    resetAllUiColors()
    config.backgroundColor = PALETTE_BLACK
    config.textColor = PALETTE_YELLOW
    config.primaryColor = PALETTE_YELLOW
    config.accentColor = PALETTE_YELLOW
    ThemeSlot.entries.filter { it.role == null }.forEach { resetThemeColor(it) }
    WeekGridLine.entries.forEach { config.setGridLineColor(it.key, THEME_UNSET) }
    config.themeRevision = config.themeRevision + 1
}
