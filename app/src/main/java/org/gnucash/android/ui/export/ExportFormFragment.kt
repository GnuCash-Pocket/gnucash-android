/*
 * Copyright (c) 2012-2013 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.export

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment.OnRecurrenceSetListener
import com.google.android.material.snackbar.Snackbar
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.app.GnuCashApplication.Companion.isDoubleEntryEnabled
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.app.finish
import org.gnucash.android.app.isNullOrEmpty
import org.gnucash.android.app.takePersistableUriPermission
import org.gnucash.android.databinding.FragmentExportFormBinding
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.DropboxHelper.authenticate
import org.gnucash.android.export.DropboxHelper.hasToken
import org.gnucash.android.export.DropboxHelper.retrieveAndSaveToken
import org.gnucash.android.export.ExportAsyncTask
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.ExportParams.ExportTarget
import org.gnucash.android.export.Exporter
import org.gnucash.android.export.Exporter.Companion.buildExportFilename
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.export.OptionsViewAnimationUtils.collapse
import org.gnucash.android.ui.export.OptionsViewAnimationUtils.expand
import org.gnucash.android.ui.get
import org.gnucash.android.ui.passcode.PasscodeHelper.skipPasscodeScreen
import org.gnucash.android.ui.settings.dialog.OwnCloudDialogFragment
import org.gnucash.android.ui.transaction.TransactionFormFragment
import org.gnucash.android.ui.util.RecurrenceParser
import org.gnucash.android.ui.util.RecurrenceViewClickListener
import org.gnucash.android.ui.util.dialog.DatePickerDialogFragment
import org.gnucash.android.ui.util.dialog.TimePickerDialogFragment
import org.gnucash.android.util.PreferencesHelper.getLastExportTime
import org.gnucash.android.util.TimestampHelper
import org.gnucash.android.util.getDocumentName
import timber.log.Timber
import java.sql.Timestamp
import java.util.Calendar

/**
 * Dialog fragment for exporting accounts and transactions in various formats
 *
 * The dialog is used for collecting information on the export options and then passing them
 * to the [Exporter] responsible for exporting
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ExportFormFragment : MenuFragment(),
    OnRecurrenceSetListener,
    DatePickerDialog.OnDateSetListener,
    TimePickerDialog.OnTimeSetListener {
    /**
     * Event recurrence options
     */
    private val eventRecurrence = EventRecurrence()

    /**
     * Recurrence rule
     */
    private var recurrenceRule: String? = null

    private val exportStartCalendar: Calendar = Calendar.getInstance()

    private val exportParams = ExportParams()
    private var scheduledAction: ScheduledAction? = null

    /**
     * Flag to determine if export has been started.
     * Used to continue export after user has picked a destination file
     */
    private var exportStarted = false

    private var binding: FragmentExportFormBinding? = null
    private var isDoubleEntry = true
    private val formatItems = mutableListOf<ExportFormatItem>()

    private fun onFormatSelected(binding: FragmentExportFormBinding, exportFormat: ExportFormat) {
        exportParams.exportFormat = exportFormat
        binding.exportWarning.isVisible = false

        when (exportFormat) {
            ExportFormat.OFX -> {
                binding.exportWarning.setText(R.string.export_warning_ofx)
                binding.exportWarning.isVisible = true
                expand(binding.exportDateLayout)
                collapse(binding.layoutCsvOptions)
            }

            ExportFormat.QIF -> {
                //TODO: Also check that there exist transactions with multiple currencies before displaying warning
                binding.exportWarning.setText(R.string.export_warning_qif)
                binding.exportWarning.isVisible = true
                expand(binding.exportDateLayout)
                collapse(binding.layoutCsvOptions)
            }

            ExportFormat.XML -> {
                binding.exportWarning.setText(R.string.export_warning_xml)
                binding.exportWarning.isVisible = true
                collapse(binding.exportDateLayout)
                collapse(binding.layoutCsvOptions)
            }

            ExportFormat.CSVA, ExportFormat.CSVT -> {
                binding.exportWarning.setText(R.string.export_notice_csv)
                binding.exportWarning.isVisible = true
                expand(binding.exportDateLayout)
                expand(binding.layoutCsvOptions)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDoubleEntry = isDoubleEntryEnabled(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentExportFormBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.title_export_dialog)

        val args = arguments
        if (args.isNullOrEmpty()) {
            return
        }
        val binding = this.binding!!
        bindViewListeners(binding)

        val scheduledUID = args.getString(UxArgument.SCHEDULED_ACTION_UID)
        if (scheduledUID.isNullOrEmpty()) {
            return
        }
        val scheduledActionDbAdapter = ScheduledActionDbAdapter.instance
        val scheduledAction = scheduledActionDbAdapter.getRecordOrNull(scheduledUID)
        if (scheduledAction != null) {
            bindForm(binding, scheduledAction)
        }
    }

    private fun bindForm(binding: FragmentExportFormBinding, scheduledAction: ScheduledAction) {
        this.scheduledAction = scheduledAction
        val tag = scheduledAction.tag ?: return
        val exportParams = ExportParams.parseTag(tag)
        val uri = exportParams.exportLocation
        val exportTarget = exportParams.exportTarget
        val csvSeparator = exportParams.csvSeparator
        val startTime = exportParams.exportStartTime

        when (exportTarget) {
            ExportTarget.DROPBOX -> binding.spinnerExportDestination.setSelection(TARGET_DROPBOX)

            ExportTarget.OWNCLOUD -> binding.spinnerExportDestination.setSelection(TARGET_OWNCLOUD)

            ExportTarget.SD_CARD,
            ExportTarget.URI -> binding.spinnerExportDestination.setSelection(TARGET_URI)

            ExportTarget.SHARING -> binding.spinnerExportDestination.setSelection(TARGET_SHARE)
        }

        setExportUri(uri)

        // select relevant format
        val exportFormat = exportParams.exportFormat
        var formatIndex = RecyclerView.NO_POSITION
        for (i in formatItems.indices) {
            val item = formatItems[i]
            if (item.value == exportFormat) {
                formatIndex = i
                break
            }
        }
        binding.valueExportFormat.setSelection(formatIndex)

        when (csvSeparator) {
            ExportParams.CSV_COMMA -> binding.radioSeparatorCommaFormat.isChecked = true
            ExportParams.CSV_COLON -> binding.radioSeparatorColonFormat.isChecked = true
            ExportParams.CSV_SEMICOLON -> binding.radioSeparatorSemicolonFormat.isChecked = true
        }

        val startTimeMills = startTime.time
        if (startTimeMills > 0L) {
            exportStartCalendar.timeInMillis = startTimeMills
            binding.exportStartDate.text =
                TransactionFormFragment.DATE_FORMATTER.print(startTimeMills)
            binding.exportStartTime.text =
                TransactionFormFragment.TIME_FORMATTER.print(startTimeMills)
            binding.switchExportAll.isChecked = false
        } else {
            binding.switchExportAll.isChecked = true
        }

        binding.checkboxPostExportDelete.isChecked = exportParams.deleteTransactionsAfterExport
        binding.compression.isChecked = exportParams.isCompressed

        val rrule = scheduledAction.ruleString
        onRecurrenceSet(rrule)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.export_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                startExport()
                return true
            }

            android.R.id.home -> {
                finish()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        retrieveAndSaveToken(requireContext())
    }

    override fun onPause() {
        super.onPause()
        // When the user try to export sharing to 3rd party service like DropBox
        // then pausing all activities. That cause passcode screen appearing happened.
        // We use a disposable flag to skip this unnecessary passcode screen.
        skipPasscodeScreen(requireContext())
    }

    /**
     * Starts the export of transactions with the specified parameters
     */
    private fun startExport() {
        val exportParameters = exportParams

        if (exportParameters.exportTarget == ExportTarget.URI && exportParameters.exportLocation == null) {
            exportStarted = true
            selectExportFile()
            return
        }

        val binding = binding ?: return
        if (binding.switchExportAll.isChecked) {
            exportParameters.exportStartTime = TimestampHelper.timestampFromEpochZero
        } else {
            exportParameters.exportStartTime = Timestamp(exportStartCalendar.timeInMillis)
        }

        Timber.i("Commencing async export of transactions")
        val bookUID = activeBookUID ?: return
        val position = binding.spinnerExportDestination.selectedItemPosition

        ExportAsyncTask(requireActivity(), bookUID) { bookUri ->
            if (bookUri != null) {
                PreferenceManager.getDefaultSharedPreferences(activity).edit {
                    putInt(getString(R.string.key_last_export_destination), position)
                }
                bookExported(bookUID, exportParameters)
            }
        }.execute(exportParameters)
    }

    private fun bookExported(bookUID: String, exportParameters: ExportParams) {
        if (recurrenceRule != null) {
            var updateMethod = DatabaseAdapter.UpdateMethod.Replace
            var scheduledAction = this.scheduledAction
            if (scheduledAction == null) {
                scheduledAction = ScheduledAction(ScheduledAction.ActionType.BACKUP)
                scheduledAction.actionUID = bookUID
                updateMethod = DatabaseAdapter.UpdateMethod.Insert
            }
            scheduledAction.setRecurrence(RecurrenceParser.parse(eventRecurrence))
            scheduledAction.tag = exportParameters.toTag()
            ScheduledActionDbAdapter.instance.addRecord(scheduledAction, updateMethod)
            this.scheduledAction = scheduledAction
        }

        finish()
    }

    /**
     * Bind views to actions when initializing the export form
     */
    private fun bindViewListeners(binding: FragmentExportFormBinding) {
        val context = binding.root.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        // export destination bindings
        val destinationAdapter = ArrayAdapter.createFromResource(
            context,
            R.array.export_destinations, android.R.layout.simple_spinner_item
        )
        destinationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerExportDestination.adapter = destinationAdapter
        binding.spinnerExportDestination.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                //the item selection is fired twice by the Android framework. Ignore the first one
                if (view == null) return
                when (position) {
                    TARGET_URI -> {
                        exportParams.exportTarget = ExportTarget.URI
                        binding.recurrenceOptions.isVisible = true
                        val exportUri = exportParams.exportLocation
                        setExportUri(exportUri)
                    }

                    TARGET_DROPBOX -> {
                        setExportUriText(getString(R.string.label_dropbox_export_destination))
                        binding.recurrenceOptions.isVisible = true
                        exportParams.exportTarget = ExportTarget.DROPBOX

                        if (!hasToken(context)) {
                            authenticate(context)
                        }
                    }

                    TARGET_OWNCLOUD -> {
                        setExportUri(null)
                        binding.recurrenceOptions.isVisible = true
                        exportParams.exportTarget = ExportTarget.OWNCLOUD
                        if (!preferences.getBoolean(getString(R.string.key_owncloud_sync), false)) {
                            val ocDialog = OwnCloudDialogFragment.newInstance(null)
                            ocDialog.show(parentFragmentManager, "ownCloud dialog")
                        }
                    }

                    TARGET_SHARE -> {
                        setExportUriText(getString(R.string.label_select_destination_after_export))
                        exportParams.exportTarget = ExportTarget.SHARING
                        binding.recurrenceOptions.isVisible = false
                    }

                    else -> exportParams.exportTarget = ExportTarget.SD_CARD
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        val position = preferences.getInt(getString(R.string.key_last_export_destination), 0)
        binding.spinnerExportDestination.setSelection(position)

        //**************** export start time bindings ******************
        var timestamp = getLastExportTime(context, activeBookUID!!)
        if (timestamp.time <= 0L) {
            timestamp = TransactionsDbAdapter.instance.timestampOfFirstModification
        }
        val time = timestamp.time
        exportStartCalendar.time = timestamp

        binding.exportStartDate.setOnClickListener {
            val dateMillis = exportStartCalendar.timeInMillis
            DatePickerDialogFragment.newInstance(this@ExportFormFragment, dateMillis)
                .show(parentFragmentManager, "date_picker_fragment")
        }
        binding.exportStartDate.text = TransactionFormFragment.DATE_FORMATTER.print(time)

        binding.exportStartTime.setOnClickListener {
            val timeMillis = exportStartCalendar.timeInMillis
            TimePickerDialogFragment.newInstance(this@ExportFormFragment, timeMillis)
                .show(parentFragmentManager, "time_picker_dialog_fragment")
        }
        binding.exportStartTime.text = TransactionFormFragment.TIME_FORMATTER.print(time)

        binding.switchExportAll.setOnCheckedChangeListener { _, isChecked ->
            binding.exportStartDate.isEnabled = !isChecked
            binding.exportStartTime.isEnabled = !isChecked
        }
        binding.switchExportAll.isChecked =
            preferences.getBoolean(getString(R.string.key_export_all_transactions), false)

        binding.checkboxPostExportDelete.setOnCheckedChangeListener { _, isChecked ->
            exportParams.deleteTransactionsAfterExport = isChecked
        }
        binding.checkboxPostExportDelete.isChecked =
            preferences.getBoolean(getString(R.string.key_delete_transactions_after_export), false)
        binding.compression.setOnCheckedChangeListener { _, isChecked ->
            exportParams.isCompressed = isChecked
        }

        binding.compression.isChecked =
            preferences.getBoolean(getString(R.string.key_compress_export), true)

        binding.inputRecurrence.setOnClickListener(
            RecurrenceViewClickListener(parentFragmentManager, recurrenceRule, this)
        )

        //this part (setting the export format) must come after the recurrence view bindings above
        val keyDefaultExportFormat = getString(R.string.key_default_export_format)
        val defaultExportFormat =
            preferences.getString(keyDefaultExportFormat, ExportFormat.XML.value)
        var defaultFormat = ExportFormat.of(defaultExportFormat)

        val formatItems = this.formatItems
        formatItems.clear()
        formatItems.add(
            ExportFormatItem(ExportFormat.CSVT, context.getString(ExportFormat.CSVT.labelId))
        )
        formatItems.add(
            ExportFormatItem(ExportFormat.QIF, context.getString(ExportFormat.QIF.labelId))
        )
        if (isDoubleEntry) {
            formatItems.add(
                ExportFormatItem(ExportFormat.XML, context.getString(ExportFormat.XML.labelId))
            )
            if (defaultFormat == ExportFormat.OFX) {
                defaultFormat = ExportFormat.XML
            }
        } else {
            formatItems.add(
                ExportFormatItem(ExportFormat.OFX, context.getString(ExportFormat.OFX.labelId))
            )
            if (defaultFormat == ExportFormat.XML) {
                defaultFormat = ExportFormat.OFX
            }
        }
        val formatAdapter = ArrayAdapter<ExportFormatItem>(
            context,
            android.R.layout.simple_spinner_item,
            formatItems
        )
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.valueExportFormat.adapter = formatAdapter
        binding.valueExportFormat.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (view == null)  //the item selection is fired twice by the Android framework. Ignore the first one
                    return
                val item: ExportFormatItem = formatAdapter[position]
                onFormatSelected(binding, item.value)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
        var formatIndex = RecyclerView.NO_POSITION
        for (i in formatItems.indices) {
            val item = formatItems[i]
            if (item.value == defaultFormat) {
                formatIndex = i
                break
            }
        }
        binding.valueExportFormat.setSelection(formatIndex)

        binding.radioSeparatorCommaFormat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                exportParams.csvSeparator = ExportParams.CSV_COMMA
            }
        }
        binding.radioSeparatorColonFormat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                exportParams.csvSeparator = ExportParams.CSV_COLON
            }
        }
        binding.radioSeparatorSemicolonFormat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                exportParams.csvSeparator = ExportParams.CSV_SEMICOLON
            }
        }
    }

    /**
     * Display the file path of the file where the export will be saved
     *
     * @param filepath Path to export file. If `null`, the view will be hidden and nothing displayed
     */
    private fun setExportUriText(filepath: String?) {
        val binding = binding ?: return
        if (filepath.isNullOrEmpty()) {
            binding.targetUri.isVisible = false
            binding.targetUri.text = ""
        } else {
            binding.targetUri.text = filepath
            binding.targetUri.isVisible = true
        }
    }

    /**
     * Display the file path of the file where the export will be saved
     *
     * @param uri URI to export file. If `null`, the view will be hidden and nothing displayed
     */
    private fun setExportUri(uri: Uri?) {
        exportParams.exportLocation = uri
        if (uri == null) {
            setExportUriText("")
        } else {
            setExportUriText(uri.getDocumentName(context))
        }
    }

    /**
     * Open a chooser for user to pick a file to export to
     */
    private fun selectExportFile() {
        val bookName = BooksDbAdapter.instance.activeBookDisplayName
        val filename =
            buildExportFilename(exportParams.exportFormat, exportParams.isCompressed, bookName)

        val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, filename)
        try {
            startActivityForResult(createIntent, REQUEST_EXPORT_FILE)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Cannot create document for export")
            if (isVisible) {
                val view = requireView()
                Snackbar.make(view, R.string.toast_install_file_manager, Snackbar.LENGTH_LONG)
                    .show()
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.toast_install_file_manager,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onRecurrenceSet(rrule: String?) {
        Timber.i("Export reoccurs: %s", rrule)
        val binding = binding ?: return
        val context = binding.inputRecurrence.context
        var repeatString: String? = null
        if (!rrule.isNullOrEmpty()) {
            try {
                eventRecurrence.parse(rrule)
                recurrenceRule = rrule
                repeatString = EventRecurrenceFormatter.getRepeatString(
                    context,
                    context.resources,
                    eventRecurrence,
                    true
                )
            } catch (e: Exception) {
                Timber.e(e, "Bad recurrence for [%s]", rrule)
            }
        }
        if (repeatString.isNullOrEmpty()) {
            repeatString = context.getString(R.string.label_tap_to_create_schedule)
        }
        binding.inputRecurrence.text = repeatString
    }

    /**
     * Callback for when the activity chooser dialog is completed
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_EXPORT_FILE -> if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    requireContext().takePersistableUriPermission(data)
                    val location = data.data
                    setExportUri(location)
                } else {
                    setExportUri(null)
                }

                if (exportStarted) {
                    startExport()
                }
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, dayOfMonth: Int) {
        val binding = binding ?: return
        exportStartCalendar.set(year, month, dayOfMonth)
        binding.exportStartDate.text = TransactionFormFragment.DATE_FORMATTER
            .print(exportStartCalendar.timeInMillis)
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        val binding = binding ?: return
        exportStartCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        exportStartCalendar.set(Calendar.MINUTE, minute)
        binding.exportStartTime.text = TransactionFormFragment.TIME_FORMATTER
            .print(exportStartCalendar.timeInMillis)
    }

    private data class ExportFormatItem(val value: ExportFormat, val label: String) {
        override fun toString(): String {
            return label
        }
    }

    companion object {
        /**
         * Request code for intent to pick export file destination
         */
        private const val REQUEST_EXPORT_FILE = 0x14

        //Save As..
        private const val TARGET_URI = 0

        //DROPBOX
        private const val TARGET_DROPBOX = 1

        //OwnCloud
        private const val TARGET_OWNCLOUD = 2

        //Share File
        private const val TARGET_SHARE = 3
    }
}
