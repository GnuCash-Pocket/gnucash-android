package org.gnucash.android.app

import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity

open class GnuCashActivity : AppCompatActivity() {

    /**
     * Sets the color Action Bar and Status bar (where applicable)
     */
    protected open fun setTitlesColor(@ColorInt color: Int) {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
        window.statusBarColor = GnuCashApplication.darken(color)
    }
}