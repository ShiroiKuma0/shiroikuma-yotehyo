package org.fossify.calendar.extensions

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.calendar.R
import org.fossify.calendar.helpers.THEME_UNSET

// The box-grid week view is a fixed 3-row x 2-column layout (equal weights), so its dividing lines
// fall at fixed fractions. These seven lines are drawn as an overlay independent of the per-box
// borders, each with its own enabled / color / thickness so they can be mixed freely.
//   verticals:   LEFT (x=0), CENTER (x=½), RIGHT (x=1)
//   horizontals: TOP (y=0), MIDDLE_UPPER (y=⅓), MIDDLE_LOWER (y=⅔), BOTTOM (y=1)
enum class WeekGridLine(val key: String, @StringRes val labelRes: Int) {
    TOP("top", R.string.theme_grid_line_top),
    MIDDLE_UPPER("middle_upper", R.string.theme_grid_line_middle_upper),
    MIDDLE_LOWER("middle_lower", R.string.theme_grid_line_middle_lower),
    BOTTOM("bottom", R.string.theme_grid_line_bottom),
    LEFT("left", R.string.theme_grid_line_left),
    CENTER("center", R.string.theme_grid_line_center),
    RIGHT("right", R.string.theme_grid_line_right),
}

/** A grid line's effective color: its own override, else the shared Grid lines color. */
fun Context.gridLineColor(line: WeekGridLine): Int {
    val own = config.getGridLineColor(line.key)
    return if (own != THEME_UNSET) own else themeColor(ThemeSlot.GRID_LINES)
}
