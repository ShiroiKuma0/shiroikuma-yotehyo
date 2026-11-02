package org.fossify.calendar.extensions

import android.graphics.Color
import org.fossify.commons.extensions.getContrastColor
import kotlin.math.abs
import kotlin.math.roundToInt

import org.fossify.calendar.helpers.MONTH
import org.fossify.calendar.helpers.WEEK
import org.fossify.calendar.helpers.YEAR

fun Int.isXWeeklyRepetition() = this != 0 && this % WEEK == 0

fun Int.isXMonthlyRepetition() = this != 0 && this % MONTH == 0

fun Int.isXYearlyRepetition() = this != 0 && this % YEAR == 0

// Summed per-channel distance (0..765) below which a color is considered indistinguishable from the
// background it is drawn on, i.e. effectively invisible.
private const val MIN_VISIBLE_COLOR_DISTANCE = 60

/** Whether this color can still be told apart from [background] once drawn over it. */
fun Int.isDistinguishableFrom(background: Int): Boolean {
    val blended = blendOver(background)
    val distance = abs(Color.red(blended) - Color.red(background)) +
        abs(Color.green(blended) - Color.green(background)) +
        abs(Color.blue(blended) - Color.blue(background))
    return distance >= MIN_VISIBLE_COLOR_DISTANCE
}

/** This color as it actually appears once drawn over [background], taking its alpha into account. */
fun Int.blendOver(background: Int): Int {
    val alpha = Color.alpha(this) / 255f
    val red = (Color.red(this) * alpha + Color.red(background) * (1 - alpha)).roundToInt()
    val green = (Color.green(this) * alpha + Color.green(background) * (1 - alpha)).roundToInt()
    val blue = (Color.blue(this) * alpha + Color.blue(background) * (1 - alpha)).roundToInt()
    return Color.rgb(red, green, blue)
}

/** This text color, or [fill]'s contrast color when it would vanish on [fill]. */
fun Int.readableOn(fill: Int): Int = if (isDistinguishableFrom(fill)) this else fill.getContrastColor()
