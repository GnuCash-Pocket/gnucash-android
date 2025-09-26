package org.gnucash.android.ui.settings.dialog

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.preference.TwoStatePreference
import com.owncloud.android.lib.common.OwnCloudClientFactory
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory
import com.owncloud.android.lib.common.operations.OnRemoteOperationListener
import com.owncloud.android.lib.resources.files.FileUtils
import com.owncloud.android.lib.resources.status.GetRemoteStatusOperation
import com.owncloud.android.lib.resources.users.GetRemoteUserInfoOperation
import org.gnucash.android.R
import org.gnucash.android.databinding.DialogOwncloudAccountBinding
import org.gnucash.android.lang.equals
import org.gnucash.android.lang.trim
import org.gnucash.android.ui.settings.OwnCloudPreferences
import org.gnucash.android.ui.util.dialog.VolatileDialogFragment
import timber.log.Timber

/**
 * A fragment for adding an ownCloud account.
 */
class OwnCloudDialogFragment : VolatileDialogFragment() {
    private var serverAddress: String? = null
    private var username: String? = null
    private var password: String? = null
    private var directory: String? = null

    private var ocCheckBox: TwoStatePreference? = null
    private lateinit var serverText: EditText
    private lateinit var usernameText: EditText
    private lateinit var passwordText: EditText
    private lateinit var directoryText: EditText
    private lateinit var serverErrorText: TextView
    private lateinit var usernameErrorText: TextView
    private lateinit var directoryErrorText: TextView

    private lateinit var preferences: OwnCloudPreferences

    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = requireContext()
        preferences = OwnCloudPreferences(context)

        serverAddress = preferences.server
        username = preferences.username
        password = preferences.password
        directory = preferences.dir
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogOwncloudAccountBinding.inflate(layoutInflater)
        val context = binding.root.context

        serverText = binding.owncloudHostname
        usernameText = binding.owncloudUsername
        passwordText = binding.owncloudPassword
        directoryText = binding.owncloudDir

        serverText.setText(serverAddress)
        directoryText.setText(directory)
        passwordText.setText(password) // TODO: Remove - debugging only
        usernameText.setText(username)

        serverErrorText = binding.owncloudHostnameInvalid
        usernameErrorText = binding.owncloudUsernameInvalid
        directoryErrorText = binding.owncloudDirInvalid
        serverErrorText.isVisible = false
        usernameErrorText.isVisible = false
        directoryErrorText.isVisible = false

        return AlertDialog.Builder(context, theme)
            .setTitle("ownCloud")
            .setView(binding.root)
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                // Dismisses itself
            }
            .setNeutralButton(R.string.btn_test) { _, _ ->
                checkData()
            }
            .setPositiveButton(R.string.btn_save) { _, _ ->
                if ((serverText.getText() equals serverAddress) &&
                    (usernameText.getText() equals username) &&
                    (passwordText.getText() equals password) &&
                    (directoryText.getText() equals directory) &&
                    isOK(directoryErrorText) &&
                    isOK(usernameErrorText) &&
                    isOK(serverErrorText)
                ) {
                    save()
                }
            }
            .create()
    }

    private fun isOK(textView: TextView): Boolean {
        val context = textView.context
        val text = textView.getText()
        return text.isNullOrEmpty() || (text equals context.getText(R.string.owncloud_dir_ok))
    }

    private fun save() {
        preferences.server = serverAddress
        preferences.username = username
        preferences.password = password
        preferences.dir = directory
        preferences.isSync = true

        ocCheckBox?.isChecked = true
    }

    private fun checkData() {
        val context = serverText.context
        serverErrorText.isVisible = false
        usernameErrorText.isVisible = false
        directoryErrorText.isVisible = false

        serverAddress = serverText.getText().trim()
        username = usernameText.getText().trim()
        password = passwordText.getText().trim()
        directory = directoryText.getText().trim()

        if (FileUtils.isValidPath(directory, false)) {
            directoryErrorText.setTextColor(
                ContextCompat.getColor(context, R.color.account_green)
            )
            directoryErrorText.setText(R.string.owncloud_dir_ok)
            directoryErrorText.isVisible = true
        } else {
            directoryErrorText.setTextColor(
                ContextCompat.getColor(context, R.color.design_default_color_error)
            )
            directoryErrorText.setText(R.string.owncloud_dir_invalid)
            directoryErrorText.isVisible = true
        }

        val serverUri = serverAddress?.toUri()
        val client = OwnCloudClientFactory.createOwnCloudClient(serverUri, context, true)
        client.credentials = OwnCloudCredentialsFactory.newBasicCredentials(username, password)

        val listener = OnRemoteOperationListener { caller, result ->
            if (result.isSuccess) {
                if (caller is GetRemoteStatusOperation) {
                    serverErrorText.setTextColor(
                        ContextCompat.getColor(context, R.color.account_green)
                    )
                    serverErrorText.setText(R.string.owncloud_server_ok)
                    serverErrorText.isVisible = true
                } else if (caller is GetRemoteUserInfoOperation) {
                    usernameErrorText.setTextColor(
                        ContextCompat.getColor(context, R.color.account_green)
                    )
                    usernameErrorText.setText(R.string.owncloud_user_ok)
                    usernameErrorText.isVisible = true
                }
            } else {
                Timber.e(result.exception, result.logMessage)

                if (caller is GetRemoteStatusOperation) {
                    serverErrorText.setTextColor(
                        ContextCompat.getColor(context, R.color.design_default_color_error)
                    )
                    serverErrorText.setText(R.string.owncloud_server_invalid)
                    serverErrorText.isVisible = true
                } else if (caller is GetRemoteUserInfoOperation) {
                    usernameErrorText.setTextColor(
                        ContextCompat.getColor(context, R.color.design_default_color_error)
                    )
                    usernameErrorText.setText(R.string.owncloud_user_invalid)
                    usernameErrorText.isVisible = true
                }
            }
        }

        val statusOperation = GetRemoteStatusOperation(context)
        statusOperation.execute(client, listener, handler)

        val userInfoOperation = GetRemoteUserInfoOperation()
        userInfoOperation.execute(client, listener, handler)
    }

    companion object {
        const val TAG = "owncloud_dialog"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment OwnCloudDialogFragment.
         */
        fun newInstance(preference: TwoStatePreference? = null): OwnCloudDialogFragment {
            val fragment = OwnCloudDialogFragment()
            fragment.ocCheckBox = preference
            return fragment
        }
    }
}
