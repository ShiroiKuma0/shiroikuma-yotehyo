package org.fossify.calendar.extensions

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.circleDrawable
import kotlin.math.roundToInt

// The shapes this app draws, built in code so that every colour comes from a role (no colour in XML).

private const val EVENT_BLOCK_RADIUS_DP = 4f
private const val TIME_CELL_RADIUS_DP = 4f
private const val TIME_CELL_FRAME_DP = 1.5f
private const val TIME_CELL_FRAME_FOCUSED_DP = 3f
private const val SHORTCUT_ICON_INSET_DP = 8f

private fun Context.dpToPx(dp: Float) = dp * resources.displayMetrics.density

/** An event in a month cell: a small rounded block in the event's colour. */
fun Context.eventBlockDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
    cornerRadius = dpToPx(EVENT_BLOCK_RADIUS_DP)
    setColor(color)
}

/** The bar down the start edge of a list entry, in the entry's colour; round on its outer corners. */
fun Context.eventColorBarDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
    val radius = resources.getDimension(org.fossify.commons.R.dimen.activity_margin)
    cornerRadii = floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
    setColor(color)
}

/**
 * One digit box of the keyboard time entry. The box keeps its ground; the focused one — the one the
 * next keystroke overwrites — is marked by a thicker frame.
 */
fun Context.timeCellDrawable(): Drawable {
    fun box(frameDp: Float) = GradientDrawable().apply {
        cornerRadius = dpToPx(TIME_CELL_RADIUS_DP)
        setColor(themeColor(ThemeSlot.TIME_CELL_GROUND))
        setStroke(dpToPx(frameDp).roundToInt().coerceAtLeast(1), themeColor(ThemeSlot.TIME_CELL_FRAME))
    }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), box(TIME_CELL_FRAME_FOCUSED_DP))
        addState(intArrayOf(), box(TIME_CELL_FRAME_DP))
    }
}

/** A launcher shortcut's icon: [iconRes] on a disc of [ground]. */
fun Context.shortcutIconDrawable(iconRes: Int, ground: Int): Drawable {
    val inset = dpToPx(SHORTCUT_ICON_INSET_DP).roundToInt()
    val icon = ContextCompat.getDrawable(this, iconRes)!!.mutate()
    return LayerDrawable(arrayOf(circleDrawable(ground), InsetDrawable(icon, inset)))
}

/** Hairlines along the bottom and / or the end edge of a view: the frame round the week view's parts. */
class EdgeLinesDrawable(color: Int, private val bottom: Boolean, private val end: Boolean) : Drawable() {
    private val paint = Paint().apply {
        this.color = color
        strokeWidth = 1f
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (bottom) canvas.drawRect(b.left.toFloat(), b.bottom - 1f, b.right.toFloat(), b.bottom.toFloat(), paint)
        if (end) canvas.drawRect(b.right - 1f, b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun Context.edgeLines(bottom: Boolean = false, end: Boolean = false): Drawable =
    EdgeLinesDrawable(themeColor(ThemeSlot.GRID_LINES), bottom, end)
