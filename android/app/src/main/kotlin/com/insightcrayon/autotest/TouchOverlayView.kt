package com.insightcrayon.autotest

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.DecelerateInterpolator

class TouchOverlayView(context: Context) : View(context) {

    private val touchPoints = mutableListOf<TouchPoint>()
    private val paint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    data class TouchPoint(
        var x: Float,
        var y: Float,
        var radius: Float,
        var alpha: Int
    )

    fun showTouch(x: Float, y: Float) {
        val touchPoint = TouchPoint(x, y, 0f, 255)
        touchPoints.add(touchPoint)

        // 애니메이션: 크기 커지면서 투명해짐
        val animator = ValueAnimator.ofFloat(0f, 100f)
        animator.duration = 800
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            touchPoint.radius = value
            touchPoint.alpha = (255 * (1 - value / 100f)).toInt()

            invalidate()

            // 애니메이션 끝나면 제거
            if (value >= 100f) {
                touchPoints.remove(touchPoint)
            }
        }
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (point in touchPoints) {
            paint.color = Color.argb(point.alpha, 0, 150, 255) // 파란색
            canvas.drawCircle(point.x, point.y, point.radius, paint)

            // 테두리
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.argb(point.alpha, 255, 255, 255) // 흰색 테두리
            canvas.drawCircle(point.x, point.y, point.radius, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
