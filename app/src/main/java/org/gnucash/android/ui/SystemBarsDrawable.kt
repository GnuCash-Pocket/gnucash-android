package org.gnucash.android.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

class SystemBarsDrawable : Drawable() {

    private val statusBarBounds = RectF()
    private val statusBarPaint = Paint().apply {
        color = Color.TRANSPARENT
    }
    var statusBarHeight: Int = 0
        set(value) {
            field = value
            statusBarBounds.bottom = statusBarBounds.top + value
            invalidateSelf()
        }
    @get:ColorInt
    var statusBarColor: Int
        get() = statusBarPaint.color
        set(value) {
            statusBarPaint.color = value
        }

    private val navigationBarBounds = RectF()
    private val navigationBarPaint = Paint().apply {
        color = Color.TRANSPARENT
    }
    var navigationBarHeight: Int = 0
        set(value) {
            field = value
            navigationBarBounds.top = navigationBarBounds.bottom - value
            invalidateSelf()
        }
    @get:ColorInt
    var navigationBarColor: Int
        get() = navigationBarPaint.color
        set(value) {
            navigationBarPaint.color = value
        }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(statusBarBounds, statusBarPaint)
        canvas.drawRect(navigationBarBounds, navigationBarPaint)
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun setAlpha(alpha: Int) {
        statusBarPaint.alpha = alpha
        navigationBarPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        statusBarPaint.setColorFilter(colorFilter)
        navigationBarPaint.setColorFilter(colorFilter)
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()

        statusBarBounds.set(left, top, right, top + statusBarHeight)
        navigationBarBounds.set(left, bottom - navigationBarHeight, right, bottom)
    }
}