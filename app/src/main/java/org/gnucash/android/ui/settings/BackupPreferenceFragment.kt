/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import androidx.preference.TwoStatePreference
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.app.GnuCashApplication.Companion.getBookPreferences
import org.gnucash.android.app.takePersistableUriPermission
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.export.DropboxHelper.authenticateDropbox
import org.gnucash.android.export.DropboxHelper.deleteDropboxToken
import org.gnucash.android.export.DropboxHelper.hasDropboxToken
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.Exporter.Companion.getExportTime
import org.gnucash.android.export.Exporter.Companion.sanitizeFilename
import org.gnucash.android.importer.ImportAsyncTask
import org.gnucash.android.ui.settings.dialog.OwnCloudDialogFragment
import org.gnucash.android.ui.snackLong
import org.gnucash.android.util.BackupManager
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import org.gnucash.android.util.getDocumentName
import org.joda.time.format.DateTimeFormat
import timber.log.Timber

/**
 * Fragment for displaying general preferences
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class BackupPreferenceFragment : GnuPreferenceFragment() {
    override val titleId: Int = R.string.title_backup_prefs

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.fragment_backup_preferences)

        val context = requireContext()

        if (BuildConfig.DEBUG) {
            val deleteTransactionBackup =
                findPreference<SwitchPreference>(getString(R.string.key_delete_transaction_backup))!!
            deleteTransactionBackup.isChecked = false

            val importBookBackup =
                findPreference<SwitchPreference>(getString(R.string.key_import_book_backup))!!
            importBookBackup.isChecked = false
        }

        //if we are returning from DropBox authentication, save the key which was generated
        var preference = findPreference<Preference>(getString(R.string.key_default_export_email))!!
        if (preference.summaryProvider == null) {
            preference.setSummaryProvider { preference ->
                val textPreference = preference as EditTextPreference
                val email = textPreference.text
                if (email.isNullOrEmpty() || email.trim().isEmpty()) {
                    return@setSummaryProvider getString(R.string.summary_default_export_email)
                }
                email
            }
        }

        val keyDefaultExportFormat = getString(R.string.key_default_export_format)
        preference = findPreference(keyDefaultExportFormat)!!
        if (preference.summaryProvider == null) {
            preference.setSummaryProvider { preference ->
                val listPreference = preference as ListPreference
                val value = listPreference.value
                if (value.isNullOrEmpty()) {
                    return@setSummaryProvider getString(R.string.summary_default_export_format)
                }
                val format = ExportFormat.of(value)
                getString(format.labelId)
            }
        }

        preference = findPreference(getString(R.string.key_restore_backup))!!
        preference.setOnPreferenceClickListener { _ ->
            restoreBackup()
            true
        }

        preference = findPreference(getString(R.string.key_create_backup))!!
        preference.setOnPreferenceClickListener { _ ->
            val activity = activity ?: return@setOnPreferenceClickListener false
            backupActiveBookAsync(activity) { result: Boolean ->
                val msg =
                    if (result) R.string.toast_backup_successful else R.string.toast_backup_failed
                snackLong(msg)
            }
            true
        }

        preference = findPreference(getString(R.string.key_backup_location))!!
        preference.setOnPreferenceClickListener { preference ->
            val bookName = BooksDbAdapter.instance.activeBookDisplayName
            val fileName =
                sanitizeFilename(bookName) + "_" + getString(R.string.label_backup_filename)

            val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType(BackupManager.MIME_TYPE)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, fileName)
            try {
                startActivityForResult(createIntent, REQUEST_BACKUP_FILE)
            } catch (e: ActivityNotFoundException) {
                Timber.e(e, "Cannot create document for backup")
                snackLong(R.string.toast_install_file_manager)
            }
            true
        }

        val defaultBackupLocation = BackupManager.getBookBackupFileUri(context, activeBookUID!!)
        if (defaultBackupLocation != null) {
            preference.summary = defaultBackupLocation.getDocumentName(context)
        }

        var switch = findPreference<TwoStatePreference>(getString(R.string.key_dropbox_sync))!!
        switch.setOnPreferenceClickListener { _ ->
            toggleDropboxSync(switch)
            toggleDropboxPreference(switch)
            false
        }
        toggleDropboxPreference(switch)

        switch = findPreference(getString(R.string.key_owncloud_sync))!!
        switch.setOnPreferenceClickListener { _ ->
            toggleOwnCloudSync(switch)
            toggleOwnCloudPreference(switch)
            false
        }
        toggleOwnCloudPreference(switch)
    }

    /**
     * Toggles the checkbox of the DropBox Sync preference if a DropBox account is linked
     *
     * @param preference DropBox Sync preference
     */
    fun toggleDropboxPreference(preference: TwoStatePreference) {
        val context = preference.context
        preference.isChecked = hasDropboxToken(context)
    }

    /**
     * Toggles the checkbox of the ownCloud Sync preference if an ownCloud account is linked
     *
     * @param preference ownCloud Sync preference
     */
    fun toggleOwnCloudPreference(preference: TwoStatePreference) {
        val context = preference.context
        val preferences = OwnCloudPreferences(context)
        preference.isChecked = preferences.isSync
    }

    /**
     * Toggles the authorization state of a DropBox account.
     * If a link exists, it is removed else DropBox authorization is started
     */
    private fun toggleDropboxSync(preference: TwoStatePreference) {
        val context = preference.context
        if (!hasDropboxToken(context)) {
            authenticateDropbox(context)
        } else {
            deleteDropboxToken(context)
        }
    }

    /**
     * Toggles synchronization with ownCloud on or off
     */
    private fun toggleOwnCloudSync(preference: TwoStatePreference) {
        val context = preference.context
        val preferences = OwnCloudPreferences(context)

        if (preferences.isSync) {
            preferences.isSync = false
        } else {
            OwnCloudDialogFragment.newInstance(preference)
                .show(parentFragmentManager, OwnCloudDialogFragment.TAG)
        }
    }

    /**
     * Opens a dialog for a user to select a backup to restore and then restores the backup
     */
    private fun restoreBackup() {
        Timber.i("Opening GnuCash XML backups for restore")
        val activity = activity ?: return
        val bookUID = activeBookUID

        val defaultBackupFile = BackupManager.getBookBackupFileUri(activity, bookUID!!)
        if (defaultBackupFile != null) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.title_confirm_restore_backup)
                .setMessage(R.string.msg_confirm_restore_backup_into_new_book)
                .setNegativeButton(R.string.btn_cancel) { _, _ ->
                    // Dismisses itself
                }
                .setPositiveButton(R.string.btn_restore) { _, _ ->
                    ImportAsyncTask(activity).execute(defaultBackupFile)
                }
                .show()
            return  //stop here if the default backup file exists
        }

        //If no default location was set, look in the internal SD card location
        if (BackupManager.getBackupList(activity, bookUID).isEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.title_no_backups_found)
                .setMessage(R.string.msg_no_backups_to_restore_from)
                .setNegativeButton(R.string.btn_cancel) { _, _ ->
                    // Dismisses itself
                }
                .show()
            return
        }

        val adapter = ArrayAdapter<String>(activity, android.R.layout.select_dialog_singlechoice)
        val dateFormatter = DateTimeFormat.longDateTime()
        for (backupFile in BackupManager.getBackupList(activity, bookUID)) {
            val time = getExportTime(backupFile.name)
            if (time > 0) adapter.add(dateFormatter.print(time))
            else  //if no timestamp was found in the filename, just use the name
                adapter.add(backupFile.name)
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.title_select_backup_to_restore)
            .setNegativeButton(R.string.alert_dialog_cancel) { _, _ ->
                // Dismisses itself
            }
            .setAdapter(adapter) { _, which ->
                val backupFile = BackupManager.getBackupList(activity, bookUID)[which]
                ImportAsyncTask(activity).execute(Uri.fromFile(backupFile))
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_LINK_TO_DBX -> {
                // if we are in a preference header fragment, this may return null
                val preference =
                    findPreference<TwoStatePreference>(getString(R.string.key_dropbox_sync))
                        ?: return
                toggleDropboxPreference(preference)
            }

            REQUEST_BACKUP_FILE -> if (resultCode == Activity.RESULT_OK) {
                val backupFileUri: Uri = data?.data ?: return
                val context = requireContext()
                context.takePersistableUriPermission(data)

                getBookPreferences(context).edit {
                    putString(BackupManager.KEY_BACKUP_FILE, backupFileUri.toString())
                }

                val preference =
                    findPreference<Preference>(getString(R.string.key_backup_location))!!
                preference.summary = backupFileUri.getDocumentName(preference.context)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        /**
         * Collects references to the UI elements and binds click listeners
         */
        private const val REQUEST_LINK_TO_DBX = 0x11

        /**
         * Request code for the backup file where to save backups
         */
        private const val REQUEST_BACKUP_FILE = 0x13
    }
}
