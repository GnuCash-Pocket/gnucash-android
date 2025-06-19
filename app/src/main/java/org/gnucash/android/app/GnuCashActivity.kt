package org.gnucash.android.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
import org.gnucash.android.ui.SystemBarsDrawable

open class GnuCashActivity : AppCompatActivity() {

    private val contentView: ViewGroup get() = window.decorView.findViewById(android.R.id.content)
    private val systemBarsDrawable = SystemBarsDrawable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentView.background = systemBarsDrawable
    }

    override fun onStart() {
        super.onStart()
            ViewCompat.setOnApplyWindowInsetsListener(contentView) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(
                    left = bars.left,
                    top = bars.top,
                    right = bars.right,
                    bottom = bars.bottom,
                )
                systemBarsDrawable.statusBarHeight = bars.top
                systemBarsDrawable.navigationBarHeight = bars.bottom
                WindowInsetsCompat.CONSUMED
            }

        if (systemBarsDrawable.statusBarColor == Color.TRANSPARENT) {
            setTitlesColor(ContextCompat.getColor(this, R.color.theme_primary))
        }
    }

    /**
     * Sets the color Action Bar and Status bar (where applicable)
     */
    protected open fun setTitlesColor(@ColorInt color: Int) {
        val colorDark = darken(color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            systemBarsDrawable.statusBarColor = colorDark
            systemBarsDrawable.navigationBarColor = colorDark
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = colorDark
        }
        supportActionBar?.setBackgroundDrawable(color.toDrawable())
    }
}