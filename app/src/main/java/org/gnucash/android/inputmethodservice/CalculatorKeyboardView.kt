package org.gnucash.android.inputmethodservice

import android.content.Context
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.widget.Button
import android.widget.ImageButton
import android.widget.TableLayout
import org.gnucash.android.R
import java.text.DecimalFormatSymbols
import java.text.NumberFormat

class CalculatorKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TableLayout(context, attrs) {

    var onKeyboardActionListener: KeyboardView.OnKeyboardActionListener? = null

    private val decimalSeparator: String
    private val minusSign: String
    private val zeroDigit: String
    private val oneDigit: String
    private val twoDigit: String
    private val threeDigit: String
    private val fourDigit: String
    private val fiveDigit: String
    private val sixDigit: String
    private val sevenDigit: String
    private val eightDigit: String
    private val nineDigit: String
    private val keyCodes = IntArray(0)

    init {
        val symbols = DecimalFormatSymbols.getInstance()
        var numbers = NumberFormat.getInstance()
        decimalSeparator = symbols.monetaryDecimalSeparator.toString()
        minusSign = symbols.minusSign.toString()
        zeroDigit = numbers.format(0)
        oneDigit = numbers.format(1)
        twoDigit = numbers.format(2)
        threeDigit = numbers.format(3)
        fourDigit = numbers.format(4)
        fiveDigit = numbers.format(5)
        sixDigit = numbers.format(6)
        sevenDigit = numbers.format(7)
        eightDigit = numbers.format(8)
        nineDigit = numbers.format(9)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        findViewById<Button>(R.id.key_clear).apply {
            setOnClickListener { onKeyboardActionListener?.onKey(KEY_CODE_CLEAR, keyCodes) }
        }
        findViewById<Button>(R.id.key_0).apply {
            text = zeroDigit
            setOnClickListener { onKeyboardActionListener?.onText(zeroDigit) }
        }
        findViewById<Button>(R.id.key_1).apply {
            text = oneDigit
            setOnClickListener { onKeyboardActionListener?.onText(oneDigit) }
        }
        findViewById<Button>(R.id.key_2).apply {
            text = twoDigit
            setOnClickListener { onKeyboardActionListener?.onText(twoDigit) }
        }
        findViewById<Button>(R.id.key_3).apply {
            text = threeDigit
            setOnClickListener { onKeyboardActionListener?.onText(threeDigit) }
        }
        findViewById<Button>(R.id.key_4).apply {
            text = fourDigit
            setOnClickListener { onKeyboardActionListener?.onText(fourDigit) }
        }
        findViewById<Button>(R.id.key_5).apply {
            text = fiveDigit
            setOnClickListener { onKeyboardActionListener?.onText(fiveDigit) }
        }
        findViewById<Button>(R.id.key_6).apply {
            text = sixDigit
            setOnClickListener { onKeyboardActionListener?.onText(sixDigit) }
        }
        findViewById<Button>(R.id.key_7).apply {
            text = sevenDigit
            setOnClickListener { onKeyboardActionListener?.onText(sevenDigit) }
        }
        findViewById<Button>(R.id.key_8).apply {
            text = eightDigit
            setOnClickListener { onKeyboardActionListener?.onText(eightDigit) }
        }
        findViewById<Button>(R.id.key_9).apply {
            text = nineDigit
            setOnClickListener { onKeyboardActionListener?.onText(nineDigit) }
        }
        findViewById<Button>(R.id.key_decimal).apply {
            text = decimalSeparator
            setOnClickListener { onKeyboardActionListener?.onText(decimalSeparator) }
        }
        findViewById<ImageButton>(R.id.key_delete).apply {
            setOnClickListener { onKeyboardActionListener?.onKey(KEY_CODE_DELETE, keyCodes) }
        }
        findViewById<Button>(R.id.key_divide).apply {
            setOnClickListener { onKeyboardActionListener?.onText("/") }
        }
        findViewById<Button>(R.id.key_equals).apply {
            setOnClickListener { onKeyboardActionListener?.onKey(KEY_CODE_EVALUATE, keyCodes) }
        }
        findViewById<Button>(R.id.key_left_parenthesis).apply {
            setOnClickListener { onKeyboardActionListener?.onText("(") }
        }
        findViewById<Button>(R.id.key_right_parenthesis).apply {
            setOnClickListener { onKeyboardActionListener?.onText(")") }
        }
        findViewById<Button>(R.id.key_minus).apply {
            text = minusSign
            setOnClickListener { onKeyboardActionListener?.onText(minusSign) }
        }
        findViewById<Button>(R.id.key_multiply).apply {
            setOnClickListener { onKeyboardActionListener?.onText("*") }
        }
        findViewById<Button>(R.id.key_plus).apply {
            setOnClickListener { onKeyboardActionListener?.onText("+") }
        }
    }

    companion object {
        const val KEY_CODE_CLEAR: Int = -3
        const val KEY_CODE_DELETE: Int = -5
        const val KEY_CODE_EVALUATE: Int = '='.code
    }
}