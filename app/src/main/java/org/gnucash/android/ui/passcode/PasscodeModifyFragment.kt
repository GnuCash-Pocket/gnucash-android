package org.gnucash.android.ui.passcode

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import org.gnucash.android.R
import org.gnucash.android.ui.passcode.PasscodeHelper.getPasscode
import org.gnucash.android.ui.passcode.PasscodeHelper.isPasscodeEnabled
import org.gnucash.android.ui.passcode.PasscodeHelper.setPasscode
import timber.log.Timber

class PasscodeModifyFragment : KeyboardFragment() {

    private var passcodeOriginal: String? = null
    private var isPasscodeEnabled = false
    private var isRequestDisable = false
    private var state = STATE_NONE
    private lateinit var instructionLabel: TextView
    private var passcode1: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context: Context = requireContext()
        passcodeOriginal = getPasscode(context)

        isPasscodeEnabled = isPasscodeEnabled(context)
        if (isPasscodeEnabled) {
            state = STATE_VERIFY
        } else {
            state = STATE_ADD
        }

        val args = requireArguments()
        isRequestDisable = args.getBoolean(DISABLE_PASSCODE, false)
        if (isRequestDisable) {
            state = STATE_VERIFY
        }

        passcode1 = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        instructionLabel = view.findViewById(R.id.passcode_label)
        setState(state)
    }

    override fun onPasscodeEntered(code: String) {
        val codeOld = passcodeOriginal
        Timber.d("Passcode: %s", code)
        val activity = requireActivity()

        when (state) {
            STATE_VERIFY -> if (code == codeOld || codeOld == null) {
                if (isRequestDisable) {  // Disable existing code?
                    isPasscodeEnabled = false
                    passcodeOriginal = null
                    setPasscode(activity, null)

                    activity.setResult(Activity.RESULT_OK)
                    activity.finish()
                } else if (isPasscodeEnabled) {
                    setState(STATE_MODIFY)
                } else {
                    setState(STATE_ADD)
                }
            } else {
                showWrongPassword()
            }

            STATE_ADD -> if (passcode1 == null) {
                passcode1 = code
                setState(STATE_ADD_CONFIRM)
            }

            STATE_MODIFY -> if (passcode1 == null) {
                passcode1 = code
                setState(STATE_MODIFY_CONFIRM)
            }

            STATE_ADD_CONFIRM, STATE_MODIFY_CONFIRM -> if (passcode1 == code) {
                passcodeOriginal = code
                setPasscode(activity, code)

                activity.setResult(Activity.RESULT_OK)
                activity.finish()
            } else {
                showWrongPasswordConfirmation()
            }
        }
    }

    private fun setState(state: Int) {
        this.state = state
        @StringRes var instructionId = 0
        when (state) {
            STATE_NONE -> instructionId = R.string.label_passcode

            STATE_VERIFY -> instructionId = if (isPasscodeEnabled && !isRequestDisable) {
                R.string.label_old_passcode
            } else {
                R.string.label_passcode
            }

            STATE_ADD -> {
                instructionId = R.string.label_new_passcode
                passcode1 = null
            }

            STATE_ADD_CONFIRM -> instructionId = R.string.label_confirm_passcode

            STATE_MODIFY -> {
                instructionId = R.string.label_new_passcode
                passcode1 = null
            }

            STATE_MODIFY_CONFIRM -> instructionId = R.string.label_confirm_passcode
        }
        instructionLabel.setText(instructionId)
    }

    private fun showWrongPasswordConfirmation() {
        Toast.makeText(
            requireContext(),
            R.string.toast_invalid_passcode_confirmation,
            Toast.LENGTH_LONG
        ).show()
    }

    companion object {

        /**
         * Key for disabling the passcode
         */
        const val DISABLE_PASSCODE = "disable_passcode"

        private const val STATE_NONE = 0
        private const val STATE_VERIFY = 1
        private const val STATE_ADD = 2
        private const val STATE_ADD_CONFIRM = 3
        private const val STATE_MODIFY = 4
        private const val STATE_MODIFY_CONFIRM = 5
    }
}