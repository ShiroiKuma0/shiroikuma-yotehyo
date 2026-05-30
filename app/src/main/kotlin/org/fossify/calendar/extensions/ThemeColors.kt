package org.fossify.calendar.extensions

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.calendar.R
import org.fossify.calendar.helpers.PALETTE_BLACK
import org.fossify.calendar.helpers.PALETTE_YELLOW
import org.fossify.calendar.helpers.THEME_UNSET
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.LOWER_ALPHA

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
}

enum class ThemeSlot(
    val key: String,
    val group: ThemeGroup,
    @StringRes val labelRes: Int,
    val isFoundation: Boolean = false,
    // hasFont = true for concrete text elements (family / weight / size are configurable per element)
    val hasFont: Boolean = false,
) {
    // Foundation — reuse the stock commons colors (editing these repaints the whole app)
    BACKGROUND("theme_background", ThemeGroup.FOUNDATION, R.string.theme_background, isFoundation = true),
    PRIMARY("theme_primary", ThemeGroup.FOUNDATION, R.string.theme_primary, isFoundation = true),
    TEXT("theme_text", ThemeGroup.FOUNDATION, R.string.theme_text, isFoundation = true, hasFont = true),
    TEXT_SECONDARY("theme_text_secondary", ThemeGroup.FOUNDATION, R.string.theme_text_secondary, hasFont = true),

    // Search bar (the top MySearchMenu)
    SEARCH_FILL("theme_search_fill", ThemeGroup.SEARCH, R.string.theme_search_fill),
    SEARCH_TEXT("theme_search_text", ThemeGroup.SEARCH, R.string.theme_search_text, hasFont = true),
    SEARCH_HINT("theme_search_hint", ThemeGroup.SEARCH, R.string.theme_search_hint),
    SEARCH_ICON("theme_search_icon", ThemeGroup.SEARCH, R.string.theme_search_icon),
    SEARCH_BORDER("theme_search_border", ThemeGroup.SEARCH, R.string.theme_search_border),

    // Top bar (action / overflow icons), overflow menu text, and the Settings toolbar foreground
    TOOLBAR_ICONS("theme_toolbar_icons", ThemeGroup.CHROME, R.string.theme_toolbar_icons),
    MENU_TEXT("theme_menu_text", ThemeGroup.CHROME, R.string.theme_menu_text),
    SETTINGS_TITLE("theme_settings_title", ThemeGroup.CHROME, R.string.theme_settings_title),
    VIEW_SWITCHER("theme_view_switcher", ThemeGroup.CHROME, R.string.theme_view_switcher, hasFont = true),
    SETTINGS_SWITCHER("theme_settings_switcher", ThemeGroup.CHROME, R.string.theme_settings_switcher, hasFont = true),

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
}

/** The effective color for a slot: the user's override if set, otherwise its inherited default. */
fun Context.themeColor(slot: ThemeSlot): Int {
    val override = config.getThemeOverride(slot.key)
    return if (override != THEME_UNSET) override else themeDefault(slot)
}

private fun Context.themeDefault(slot: ThemeSlot): Int = when (slot) {
    // Foundation reads the stock commons colors (seeded to black/yellow on first run)
    ThemeSlot.BACKGROUND -> getProperBackgroundColor()
    ThemeSlot.PRIMARY -> getProperPrimaryColor()
    ThemeSlot.TEXT -> getProperTextColor()
    ThemeSlot.TEXT_SECONDARY -> themeColor(ThemeSlot.TEXT).adjustAlpha(0.6f)

    // Search bar: black fill, yellow text/icon/border by inheriting foundation
    ThemeSlot.SEARCH_FILL -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.SEARCH_TEXT -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.SEARCH_HINT -> themeColor(ThemeSlot.PRIMARY).adjustAlpha(0.5f)
    ThemeSlot.SEARCH_ICON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.SEARCH_BORDER -> themeColor(ThemeSlot.PRIMARY)

    // Top bar / menu chrome
    ThemeSlot.TOOLBAR_ICONS -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.MENU_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.SETTINGS_TITLE -> themeColor(ThemeSlot.PRIMARY).getContrastColor()
    ThemeSlot.VIEW_SWITCHER -> themeColor(ThemeSlot.TOOLBAR_ICONS)
    ThemeSlot.SETTINGS_SWITCHER -> themeColor(ThemeSlot.TOOLBAR_ICONS)

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
}

/** Set an explicit override for a slot. Write-through slots persist to the stock commons/config colors. */
fun Context.setThemeColor(slot: ThemeSlot, color: Int) {
    when (slot) {
        ThemeSlot.PRIMARY -> {
            config.isSystemThemeEnabled = false
            config.primaryColor = color
        }

        ThemeSlot.BACKGROUND -> {
            config.isSystemThemeEnabled = false
            config.backgroundColor = color
        }

        ThemeSlot.TEXT -> {
            config.isSystemThemeEnabled = false
            config.textColor = color
        }

        // Weekend reuses the existing single source of truth so the Settings picker stays in sync.
        ThemeSlot.WEEKEND -> config.highlightWeekendsColor = color

        else -> config.setThemeOverride(slot.key, color)
    }
}

/** Revert a slot to its default (palette for the editable foundation colors, inherited otherwise). */
fun Context.resetThemeColor(slot: ThemeSlot) {
    when (slot) {
        ThemeSlot.BACKGROUND -> setThemeColor(slot, PALETTE_BLACK)
        ThemeSlot.PRIMARY, ThemeSlot.TEXT -> setThemeColor(slot, PALETTE_YELLOW)
        ThemeSlot.WEEKEND -> config.highlightWeekendsColor = resources.getColor(R.color.red_text, theme)
        else -> config.clearThemeOverride(slot.key)
    }
}

/** One-time seed of the default black/yellow look across the whole app (via the stock colors). */
fun Context.seedBlackYellowThemeIfNeeded() {
    if (config.themeV1Seeded) {
        return
    }

    config.isSystemThemeEnabled = false
    config.backgroundColor = PALETTE_BLACK
    config.textColor = PALETTE_YELLOW
    config.primaryColor = PALETTE_YELLOW
    config.accentColor = PALETTE_YELLOW
    config.themeV1Seeded = true
}
