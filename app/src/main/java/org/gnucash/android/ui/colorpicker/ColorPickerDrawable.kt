package org.gnucash.android.ui.colorpicker

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.Shape
import androidx.annotation.ColorInt
import org.gnucash.android.util.darken

class ColorPickerDrawable(@ColorInt color: Int) : ShapeDrawable(OvalShape()) {

    init {
        paint.color = color
        paint.style = Paint.Style.FILL
        intrinsicWidth = 100
        intrinsicHeight = 100
    }

    private val borderWidth = 2f
    private val border = Paint().apply {
        this.color = darken(color)
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
    }

    override fun onDraw(shape: Shape, canvas: Canvas, paint: Paint) {
        super.onDraw(shape, canvas, paint)
        val rect = bounds
        canvas.drawOval(
            rect.left + borderWidth,
            rect.top + borderWidth,
            rect.right - borderWidth,
            rect.bottom - borderWidth,
            border
        )
    }
}