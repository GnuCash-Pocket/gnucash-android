package org.gnucash.android.app

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.darken
import org.gnucash.android.ui.util.widget.StatusBarContent

open class GnuCashActivity : AppCompatActivity() {

    private val contentView: ViewGroup get() = window.decorView.findViewById(android.R.id.content)
    private var statusBarContent: StatusBarContent? = null

    override fun onStart() {
        super.onStart()
        statusBarContent?.let { view ->
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.statusBarHeight = bars.top
                v.updatePadding(
                    left = bars.left,
                    top = 0,
                    right = bars.right,
                    bottom = bars.bottom,
                )
                WindowInsetsCompat.CONSUMED
            }
        } ?: run {
            ViewCompat.setOnApplyWindowInsetsListener(contentView) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(
                    left = bars.left,
                    top = bars.top,
                    right = bars.right,
                    bottom = bars.bottom,
                )
                WindowInsetsCompat.CONSUMED
            }
        }

        if (statusBarContent?.statusBarColor == Color.TRANSPARENT) {
            setTitlesColor(ContextCompat.getColor(this, R.color.theme_primary))
        }
    }

    /**
     * Sets the color Action Bar and Status bar (where applicable)
     */
    protected open fun setTitlesColor(@ColorInt color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            statusBarContent?.statusBarColor = darken(color)
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = darken(color)
        }
        supportActionBar?.setBackgroundDrawable(color.toDrawable())
    }

    override fun setContentView(view: View?) {
        var v = view ?: return
        v = wrapStatusBar(v)
        super.setContentView(v)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        var v = view ?: return
        v = wrapStatusBar(v)
        super.setContentView(v, params)
    }

    override fun setContentView(layoutResID: Int) {
        val view = layoutInflater.inflate(layoutResID, contentView, false)
        setContentView(view)
    }

    private fun wrapStatusBar(view: View): View {
        val container = StatusBarContent(view)
        statusBarContent = container
        return container
    }
}