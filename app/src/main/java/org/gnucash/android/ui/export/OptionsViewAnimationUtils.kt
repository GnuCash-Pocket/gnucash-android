package org.gnucash.android.ui.export

import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation

// Gotten from: https://stackoverflow.com/a/31720191
internal object OptionsViewAnimationUtils {
    @JvmStatic
    fun expand(v: View) {
        v.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val targetHeight = v.measuredHeight

        v.layoutParams.height = 0
        v.visibility = View.VISIBLE
        val a: Animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                v.layoutParams.height = if (interpolatedTime == 1f) {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                } else {
                    (targetHeight * interpolatedTime).toInt()
                }
                v.requestLayout()
            }
        }

        a.duration = ((3L * targetHeight) / v.context.resources.getDisplayMetrics().density).toLong()
        v.startAnimation(a)
    }

    @JvmStatic
    fun collapse(v: View) {
        val initialHeight = v.measuredHeight

        val a: Animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                if (interpolatedTime == 1f) {
                    v.visibility = View.GONE
                } else {
                    v.layoutParams.height = initialHeight - (initialHeight * interpolatedTime).toInt()
                    v.requestLayout()
                }
            }
        }

        a.duration= ((3 * initialHeight) / v.context.resources.displayMetrics.density).toLong()
        v.startAnimation(a)
    }
}
