/*
 * Copyright (c) 2014 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.passcode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.gnucash.android.R
import org.gnucash.android.lang.plus
import org.gnucash.android.ui.snackLong
import java.text.NumberFormat

/**
 * Soft numeric keyboard for lock screen and passcode preference.
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
open class KeyboardFragment : Fragment() {
    private lateinit var pass1: TextView
    private lateinit var pass2: TextView
    private lateinit var pass3: TextView
    private lateinit var pass4: TextView

    private var length = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_numeric_keyboard, container, false)

        pass1 = rootView.findViewById(R.id.passcode1)
        pass2 = rootView.findViewById(R.id.passcode2)
        pass3 = rootView.findViewById(R.id.passcode3)
        pass4 = rootView.findViewById(R.id.passcode4)

        val formatter = NumberFormat.getIntegerInstance()

        rootView.findViewById<TextView>(R.id.one_btn).apply {
            text = formatter.format(1L)
            setOnClickListener { add("1") }
        }
        rootView.findViewById<TextView>(R.id.two_btn).apply {
            text = formatter.format(2L)
            setOnClickListener { add("2") }
        }
        rootView.findViewById<TextView>(R.id.three_btn).apply {
            text = formatter.format(3L)
            setOnClickListener { add("3") }
        }
        rootView.findViewById<TextView>(R.id.four_btn).apply {
            text = formatter.format(4L)
            setOnClickListener { add("4") }
        }
        rootView.findViewById<TextView>(R.id.five_btn).apply {
            text = formatter.format(5L)
            setOnClickListener { add("5") }
        }
        rootView.findViewById<TextView>(R.id.six_btn).apply {
            text = formatter.format(6L)
            setOnClickListener { add("6") }
        }
        rootView.findViewById<TextView>(R.id.seven_btn).apply {
            text = formatter.format(7L)
            setOnClickListener { add("7") }
        }
        rootView.findViewById<TextView>(R.id.eight_btn).apply {
            text = formatter.format(8L)
            setOnClickListener { add("8") }
        }
        rootView.findViewById<TextView>(R.id.nine_btn).apply {
            text = formatter.format(9L)
            setOnClickListener { add("9") }
        }
        rootView.findViewById<TextView>(R.id.zero_btn).apply {
            text = formatter.format(0L)
            setOnClickListener { add("0") }
        }
        rootView.findViewById<View>(R.id.delete_btn).setOnClickListener {
            when (length) {
                1 -> {
                    pass1.text = null
                    length--
                }

                2 -> {
                    pass2.text = null
                    length--
                }

                3 -> {
                    pass3.text = null
                    length--
                }

                4 -> {
                    pass4.text = null
                    length--
                }
            }
        }

        return rootView
    }

    private fun add(num: String) {
        if (length < 0) return
        length++
        when (length) {
            1 -> pass1.text = num
            2 -> pass2.text = num
            3 -> pass3.text = num
            4 -> {
                pass4.text = num
                val code = pass1.text + pass2.text + pass3.text + num
                length = Int.MIN_VALUE

                pass4.postDelayed({
                    onPasscodeEntered(code)
                    length = 0
                    pass1.text = null
                    pass2.text = null
                    pass3.text = null
                    pass4.text = null
                }, DELAY)
            }
        }
    }

    protected open fun onPasscodeEntered(code: String) = Unit

    protected fun showWrongPassword() {
        snackLong(R.string.toast_wrong_passcode)
    }

    companion object {
        private const val DELAY = 500L
    }
}
