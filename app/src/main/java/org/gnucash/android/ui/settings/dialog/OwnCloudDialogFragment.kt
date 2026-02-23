package org.gnucash.android.ui.settings.dialog

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.preference.TwoStatePreference
import com.google.android.material.textfield.TextInputLayout
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.OwnCloudClientFactory
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory
import com.owncloud.android.lib.common.operations.OnRemoteOperationListener
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.files.CreateFolderRemoteOperation
import com.owncloud.android.lib.resources.files.ExistenceCheckRemoteOperation
import com.owncloud.android.lib.resources.files.FileUtils
import com.owncloud.android.lib.resources.status.GetStatusRemoteOperation
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.databinding.DialogOwncloudAccountBinding
import org.gnucash.android.lang.equals
import org.gnucash.android.lang.trim
import org.gnucash.android.net.configureHttpClientToTrustAllCertificates
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
    private lateinit var serverTextLayout: TextInputLayout
    private lateinit var serverText: EditText
    private lateinit var directoryTextLayout: TextInputLayout
    private lateinit var directoryText: EditText
    private lateinit var usernameTextLayout: TextInputLayout
    private lateinit var usernameText: EditText
    private lateinit var passwordTextLayout: TextInputLayout
    private lateinit var passwordText: EditText
    private lateinit var errorText: TextView

    private lateinit var preferences: OwnCloudPreferences

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = requireContext()
        preferences = OwnCloudPreferences(context)

        serverAddress = preferences.server
        directory = preferences.dir
        username = preferences.username
        password = preferences.password
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogOwncloudAccountBinding.inflate(layoutInflater)
        val context = binding.root.context

        serverTextLayout = binding.serverLayout
        serverText = binding.server
        directoryTextLayout = binding.directoryLayout
        directoryText = binding.directory
        usernameTextLayout = binding.usernameLayout
        usernameText = binding.username
        passwordTextLayout = binding.passwordLayout
        passwordText = binding.password

        serverText.setText(serverAddress)
        directoryText.setText(directory)
        usernameText.setText(username)
        passwordText.setText(password)

        errorText = binding.error
        errorText.text = null

        val dialog = AlertDialog.Builder(context, theme)
            .setTitle(R.string.owncloud_name)
            .setView(binding.root)
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                // Dismisses itself
            }
            .setNeutralButton(R.string.btn_test, null)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                if ((serverText.getText() equals serverAddress) &&
                    (directoryText.getText() equals directory) &&
                    (usernameText.getText() equals username) &&
                    (passwordText.getText() equals password) &&
                    isOK(errorText)
                ) {
                    save()
                }
            }
            .create()

        // Keep the dialog visible to the test.
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                test()
            }
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
        }

        return dialog
    }

    private fun isOK(textView: TextView): Boolean {
        val context = textView.context
        val text = textView.getText()
        return text.isNullOrEmpty() || (text equals context.getText(R.string.owncloud_dir_ok))
    }

    private fun save() {
        preferences.server = serverAddress
        preferences.dir = directory
        preferences.username = username
        preferences.password = password
        preferences.isSync = true

        ocCheckBox?.isChecked = true
    }

    private fun test() {
        val context = serverText.context
        serverTextLayout.error = null
        directoryTextLayout.error = null
        usernameTextLayout.error = null
        passwordTextLayout.error = null
        errorText.text = null
        (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false

        serverAddress = serverText.getText().trim()
        directory = directoryText.getText().trim()
        username = usernameText.getText().trim()
        password = passwordText.getText().trim()

        if (serverAddress.isNullOrEmpty()) {
            serverTextLayout.error = context.getString(R.string.owncloud_server_invalid)
            return
        }
        if (directory.isNullOrEmpty()) {
            directoryTextLayout.error = context.getString(R.string.owncloud_dir_invalid)
            return
        }
        if (username.isNullOrEmpty()) {
            usernameTextLayout.error = context.getString(R.string.owncloud_user_invalid)
            return
        }

        val serverUri = serverAddress?.toUri()
        val client = OwnCloudClientFactory.createOwnCloudClient(serverUri, context, true)
        client.credentials = OwnCloudCredentialsFactory.newBasicCredentials(username, password)
        client.userId = username
        if (BuildConfig.DEBUG) {
            configureHttpClientToTrustAllCertificates()
        }

        fetchStatus(context, client)
    }

    private fun fetchStatus(context: Context, client: OwnCloudClient) {
        Timber.v("Get status")
        val listenerStatus = OnRemoteOperationListener { _, result ->
            if (result.isSuccess) {
                Timber.i("Read status OK")
                checkCredentials(context, client)
            } else {
                val message = result.getLogMessage(context)
                Timber.e(result.exception, message)
                errorText.text = message
            }
        }
        val operation = GetStatusRemoteOperation(context)
        operation.execute(client, listenerStatus, handler)
    }

    /**
     * Checks validity of currently stored credentials for a given OC account
     */
    private fun checkCredentials(context: Context, client: OwnCloudClient) {
        Timber.v("Check credentials")
        val listener = OnRemoteOperationListener { _, result ->
            if (result.isSuccess) {
                Timber.i("Check credentials OK")
                createFolder(context, client)
            } else {
                val message = result.getLogMessage(context)
                Timber.e(result.exception, message)
                errorText.text = message
            }
        }
        val operation = ExistenceCheckRemoteOperation(FileUtils.PATH_SEPARATOR, false)
        operation.execute(client, listener, handler)
    }

    private fun createFolder(context: Context, client: OwnCloudClient) {
        Timber.v("Create folder")
        val listener = OnRemoteOperationListener { _, result ->
            val code = result.code
            if (result.isSuccess || (code == RemoteOperationResult.ResultCode.FOLDER_ALREADY_EXISTS)) {
                Timber.i("Create folder OK")
                // All OK - can save.
                (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
            } else {
                val message = result.getLogMessage(context)
                Timber.e(result.exception, message)
                errorText.text = message
            }
        }
        val operation = CreateFolderRemoteOperation(directory, true)
        operation.execute(client, listener, handler)
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
