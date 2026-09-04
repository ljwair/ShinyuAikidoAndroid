package org.shinyuembody.aikido.trainer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.View

class GestureTrailView(context: Context) : View(context) {
    private val points = mutableListOf<PointF>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD1E0B3.toInt()
        strokeWidth = 10f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 190
    }

    init {
        isClickable = false
        isFocusable = false
    }

    fun setPoints(newPoints: List<PointF>) {
        points.clear()
        points.addAll(newPoints)
        invalidate()
    }

    fun clearTrail() {
        points.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        canvas.drawPath(path, paint)
    }
}
