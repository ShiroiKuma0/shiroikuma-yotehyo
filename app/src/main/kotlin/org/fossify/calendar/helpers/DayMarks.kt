package org.fossify.calendar.helpers

import org.fossify.calendar.models.Event

/**
 * Day-level marks derived from a day's entries, shared by the weekly box grid and the monthly view so
 * both frame a day the same way.
 *
 *  - A day holding a 休み entry (title = the configurable Config.dayOffTitle, resolved onto
 *    Event.isDayOffEntry at fetch time) is a day off, framed in the 休み frame colour.
 *  - A day holding an entry whose category carries a whole-day frame colour (e.g. red for national
 *    holidays) is framed in that colour.
 *
 * Both views draw them the same way ("variant C", 白い熊 2026-11-01): every day sits in the same thin
 * yellow box; a weekend, day-off or framed day additionally gets a thin frame in its colour drawn
 * [SPECIAL_DAY_FRAME_GAP_DP] inside that box, and its date header filled in the same colour with the
 * date in the weekend text colour. The yellow box and the coloured frame never share an edge, so
 * neither ever hides the other.
 */
/** Thickness of the thin box round every day (dp). */
const val REGULAR_DAY_BOX_BORDER_DP = 1

/** Thickness of a special day's inner coloured frame, and its gap inside the yellow box (dp). */
const val SPECIAL_DAY_FRAME_DP = 2
const val SPECIAL_DAY_FRAME_GAP_DP = 2

fun Event.isDayOff() = isDayOffEntry

fun Iterable<Event>.hasDayOff() = any { it.isDayOff() }

/**
 * The first whole-day frame colour among these entries' categories, or THEME_UNSET when none frames the
 * day. A 休み entry never contributes its category's frame: a day off is always drawn as a weekend day,
 * even when 休み is filed under a framed category (白い熊 keeps 休み in 国民祝日, whose frame is red).
 */
fun Iterable<Event>.dayFrameColor(): Int =
    firstOrNull { it.categoryDayFrameColor != THEME_UNSET && !it.isDayOff() }?.categoryDayFrameColor ?: THEME_UNSET

