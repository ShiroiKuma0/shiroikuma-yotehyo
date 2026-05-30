package org.fossify.calendar.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.fossify.calendar.extensions.WeekGridLine
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.gridLineColor

// Draws the box-grid week view's seven overlay grid lines (see WeekGridLine) on top of the boxes,
// independent of the per-box borders. Each enabled line uses its own color + thickness. The view is
// non-clickable so touches fall through to the day boxes below it in the FrameLayout.
class WeekGridLinesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val density = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        WeekGridLine.entries.forEach { line ->
            if (!context.config.isGridLineEnabled(line.key)) {
                return@forEach
            }
            val thickness = context.config.getGridLineThickness(line.key) * density
            if (thickness <= 0f) {
                return@forEach
            }
            paint.color = context.gridLineColor(line)
            paint.strokeWidth = thickness
            val half = thickness / 2f
            when (line) {
                WeekGridLine.TOP -> canvas.drawLine(0f, half, w, half, paint)
                WeekGridLine.MIDDLE_UPPER -> canvas.drawLine(0f, h / 3f, w, h / 3f, paint)
                WeekGridLine.MIDDLE_LOWER -> canvas.drawLine(0f, h * 2f / 3f, w, h * 2f / 3f, paint)
                WeekGridLine.BOTTOM -> canvas.drawLine(0f, h - half, w, h - half, paint)
                WeekGridLine.LEFT -> canvas.drawLine(half, 0f, half, h, paint)
                WeekGridLine.CENTER -> canvas.drawLine(w / 2f, 0f, w / 2f, h, paint)
                WeekGridLine.RIGHT -> canvas.drawLine(w - half, 0f, w - half, h, paint)
            }
        }
    }
}
