package org.gnucash.android.ui.passcode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.gnucash.android.BuildConfig
import org.gnucash.android.ui.passcode.PasscodeHelper.getPasscode
import timber.log.Timber

class PasscodeFragment : KeyboardFragment() {

    private var passcodeOriginal: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passcodeOriginal = getPasscode(requireContext())
    }

    override fun onPasscodeEntered(code: String) {
        val codeOld = passcodeOriginal!!
        Timber.d("Passcode: %s ~ %s", codeOld, code)
        val context: Context = requireContext()

        if (code == codeOld) {
            PasscodeHelper.PASSCODE_SESSION_INIT_TIME = System.currentTimeMillis()

            val args = requireArguments()
            val action = args.getString(EXTRA_ACTION)
            args.remove(EXTRA_ACTION)
            val callerClassName = args.getString(PASSCODE_CLASS_CALLER, "")
            val intent = Intent(action)
                .setClassName(context, callerClassName)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtras(args)
            startActivity(intent)

            val activity = requireActivity()
            activity.setResult(Activity.RESULT_OK)
            activity.finish()
        } else {
            showWrongPassword()
        }
    }

    companion object {
        const val EXTRA_ACTION = BuildConfig.APPLICATION_ID + ".extra.ACTION"

        /**
         * Class caller, which will be launched after the unlocking
         */
        const val PASSCODE_CLASS_CALLER = "passcode_class_caller"
    }
}