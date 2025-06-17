package org.gnucash.android.ui.util.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.ColorInt

class StatusBarContent @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    private val statusBar = View(context)
    private val content = FrameLayout(context)
    private var childrenBlocked = false

    init {
        orientation = VERTICAL
        addView(statusBar, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        childrenBlocked = true
    }

    constructor(view: View) : this(view.context) {
        content.addView(view)
    }

    var statusBarHeight: Int
        get() = statusBar.layoutParams.height
        set(value) {
            val lp = statusBar.layoutParams
            lp.height = value
            statusBar.layoutParams = lp
        }

    @ColorInt
    var statusBarColor: Int = Color.TRANSPARENT
        set(value) {
            statusBar.setBackgroundColor(value)
            field = value
        }

    override fun addView(child: View?) {
        if (childrenBlocked) {
            content.addView(child)
        } else {
            super.addView(child)
        }
    }

    override fun addView(child: View?, index: Int) {
        if (childrenBlocked) {
            content.addView(child, index)
        } else {
            super.addView(child, index)
        }
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (childrenBlocked) {
            content.addView(child, index, params)
        } else {
            super.addView(child, index, params)
        }
    }

    override fun addView(child: View?, params: ViewGroup.LayoutParams?) {
        if (childrenBlocked) {
            content.addView(child, params)
        } else {
            super.addView(child, params)
        }
    }

    override fun addView(child: View?, width: Int, height: Int) {
        if (childrenBlocked) {
            content.addView(child, width, height)
        } else {
            super.addView(child, width, height)
        }
    }

    override fun removeAllViews() {
        if (childrenBlocked) {
            content.removeAllViews()
        } else {
            super.removeAllViews()
        }
    }

    override fun removeAllViewsInLayout() {
        if (childrenBlocked) {
            content.removeAllViewsInLayout()
        } else {
            super.removeAllViewsInLayout()
        }
    }

    override fun removeView(view: View?) {
        if (childrenBlocked) {
            content.removeView(view)
        } else {
            super.removeView(view)
        }
    }

    override fun removeViewAt(index: Int) {
        if (childrenBlocked) {
            content.removeViewAt(index)
        } else {
            super.removeViewAt(index)
        }
    }

    override fun removeViewInLayout(view: View?) {
        if (childrenBlocked) {
            content.removeViewInLayout(view)
        } else {
            super.removeViewInLayout(view)
        }
    }

    override fun removeViews(start: Int, count: Int) {
        if (childrenBlocked) {
            content.removeViews(start, count)
        } else {
            super.removeViews(start, count)
        }
    }

    override fun removeViewsInLayout(start: Int, count: Int) {
        if (childrenBlocked) {
            content.removeViewsInLayout(start, count)
        } else {
            super.removeViewsInLayout(start, count)
        }
    }
}