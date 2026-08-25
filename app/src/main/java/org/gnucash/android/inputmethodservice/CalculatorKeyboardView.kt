package org.gnucash.android.inputmethodservice

import android.content.Context
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import org.gnucash.android.R
import org.gnucash.android.ui.theme.GnucashTheme

class CalculatorKeyboardView(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    var onKeyboardActionListener: KeyboardView.OnKeyboardActionListener? = null

    private val keyCodes = IntArray(0)

    override fun onFinishInflate() {
        super.onFinishInflate()
        findViewById<ComposeView>(R.id.compose).setContent {
            GnucashTheme {
                CalculatorKeyboard(
                    onText = { onKeyboardActionListener?.onText(it) },
                    onClear = { onKeyboardActionListener?.onKey(KEY_CODE_CLEAR, keyCodes) },
                    onDelete = { onKeyboardActionListener?.onKey(KEY_CODE_DELETE, keyCodes) },
                    onEvaluate = { onKeyboardActionListener?.onKey(KEY_CODE_EVALUATE, keyCodes) }
                )
            }
        }
    }

    companion object {
        const val KEY_CODE_CLEAR: Int = -3
        const val KEY_CODE_DELETE: Int = -5
        const val KEY_CODE_EVALUATE: Int = '='.code
    }
}