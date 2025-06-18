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

package org.gnucash.android.ui.export;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;
import static org.gnucash.android.app.IntentExtKt.takePersistableUriPermission;
import static org.gnucash.android.export.ExportParams.CSV_COLON;
import static org.gnucash.android.export.ExportParams.CSV_COMMA;
import static org.gnucash.android.export.ExportParams.CSV_SEMICOLON;
import static org.gnucash.android.util.ContentExtKt.getDocumentName;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence;
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter;
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.FragmentExportFormBinding;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.export.DropboxHelper;
import org.gnucash.android.export.ExportAsyncTask;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.passcode.PasscodeHelper;
import org.gnucash.android.ui.settings.dialog.OwnCloudDialogFragment;
import org.gnucash.android.ui.transaction.TransactionFormFragment;
import org.gnucash.android.ui.util.RecurrenceParser;
import org.gnucash.android.ui.util.RecurrenceViewClickListener;
import org.gnucash.android.ui.util.dialog.DatePickerDialogFragment;
import org.gnucash.android.ui.util.dialog.TimePickerDialogFragment;
import org.gnucash.android.util.PreferencesHelper;
import org.gnucash.android.util.TimestampHelper;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import timber.log.Timber;

/**
 * Dialog fragment for exporting accounts and transactions in various formats
 * <p>The dialog is used for collecting information on the export options and then passing them
 * to the {@link org.gnucash.android.export.Exporter} responsible for exporting</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ExportFormFragment extends MenuFragment implements
    RecurrencePickerDialogFragment.OnRecurrenceSetListener,
    DatePickerDialog.OnDateSetListener,
    TimePickerDialog.OnTimeSetListener {

    /**
     * Request code for intent to pick export file destination
     */
    private static final int REQUEST_EXPORT_FILE = 0x14;

    //Save As..
    private static final int TARGET_URI = 0;
    //DROPBOX
    private static final int TARGET_DROPBOX = 1;
    //OwnCloud
    private static final int TARGET_OWNCLOUD = 2;
    //Share File
    private static final int TARGET_SHARE = 3;

    /**
     * Event recurrence options
     */
    private final EventRecurrence mEventRecurrence = new EventRecurrence();

    /**
     * Recurrence rule
     */
    private String mRecurrenceRule;

    private final Calendar mExportStartCalendar = Calendar.getInstance();

    private final ExportParams mExportParams = new ExportParams();
    private ScheduledAction mScheduledAction;

    /**
     * Flag to determine if export has been started.
     * Used to continue export after user has picked a destination file
     */
    private boolean mExportStarted = false;

    private FragmentExportFormBinding mBinding;
    private boolean isDoubleEntry = true;
    private final List<ExportFormatItem> formatItems = new ArrayList<>();

    private void onFormatSelected(@NonNull FragmentExportFormBinding binding, @NonNull ExportFormat exportFormat) {
        mExportParams.setExportFormat(exportFormat);
        mBinding.exportWarning.setVisibility(View.GONE);

        switch (exportFormat) {
            case OFX:
                binding.exportWarning.setText(R.string.export_warning_ofx);
                binding.exportWarning.setVisibility(View.VISIBLE);
                OptionsViewAnimationUtils.expand(mBinding.exportDateLayout);
                OptionsViewAnimationUtils.collapse(mBinding.layoutCsvOptions);
                break;

            case QIF:
                //TODO: Also check that there exist transactions with multiple currencies before displaying warning
                binding.exportWarning.setText(R.string.export_warning_qif);
                binding.exportWarning.setVisibility(View.VISIBLE);
                OptionsViewAnimationUtils.expand(mBinding.exportDateLayout);
                OptionsViewAnimationUtils.collapse(mBinding.layoutCsvOptions);
                break;

            case XML:
                binding.exportWarning.setText(R.string.export_warning_xml);
                binding.exportWarning.setVisibility(View.VISIBLE);
                OptionsViewAnimationUtils.collapse(mBinding.exportDateLayout);
                OptionsViewAnimationUtils.collapse(mBinding.layoutCsvOptions);
                break;

            case CSVA:
            case CSVT:
                binding.exportWarning.setText(R.string.export_notice_csv);
                binding.exportWarning.setVisibility(View.VISIBLE);
                OptionsViewAnimationUtils.expand(mBinding.exportDateLayout);
                OptionsViewAnimationUtils.expand(mBinding.layoutCsvOptions);
                break;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isDoubleEntry = GnuCashApplication.isDoubleEntryEnabled(requireContext());
    }

    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @NonNull ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        mBinding = FragmentExportFormBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        ActionBar actionBar = activity.getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(R.string.title_export_dialog);

        Bundle args = getArguments();
        if ((args == null) || args.isEmpty()) {
            return;
        }
        final FragmentExportFormBinding binding = mBinding;
        bindViewListeners(binding);

        String scheduledUID = args.getString(UxArgument.SCHEDULED_ACTION_UID);
        if (TextUtils.isEmpty(scheduledUID)) {
            return;
        }
        ScheduledActionDbAdapter scheduledActionDbAdapter = ScheduledActionDbAdapter.getInstance();
        ScheduledAction scheduledAction = scheduledActionDbAdapter.getRecord(scheduledUID);
        if (scheduledAction != null) {
            bindForm(binding, scheduledAction);
        }
    }

    private void bindForm(@NonNull FragmentExportFormBinding binding, @NonNull ScheduledAction scheduledAction) {
        mScheduledAction = scheduledAction;
        String tag = scheduledAction.getTag();
        if (TextUtils.isEmpty(tag)) {
            return;
        }
        ExportParams exportParams = ExportParams.parseTag(tag);
        Uri uri = exportParams.getExportLocation();
        ExportParams.ExportTarget exportTarget = exportParams.getExportTarget();
        char csvSeparator = exportParams.getCsvSeparator();
        Timestamp startTime = exportParams.getExportStartTime();

        switch (exportTarget) {
            case DROPBOX:
                binding.spinnerExportDestination.setSelection(TARGET_DROPBOX);
                break;
            case OWNCLOUD:
                binding.spinnerExportDestination.setSelection(TARGET_OWNCLOUD);
                break;
            case SD_CARD, URI:
                binding.spinnerExportDestination.setSelection(TARGET_URI);
                break;
            case SHARING:
                binding.spinnerExportDestination.setSelection(TARGET_SHARE);
                break;
        }

        setExportUri(uri);

        // select relevant format
        ExportFormat exportFormat = exportParams.getExportFormat();
        int formatIndex = NO_POSITION;
        for (int i = 0; i < formatItems.size(); i++) {
            ExportFormatItem item = formatItems.get(i);
            if (item.value == exportFormat) {
                formatIndex = i;
                break;
            }
        }
        binding.valueExportFormat.setSelection(formatIndex);

        switch (csvSeparator) {
            case CSV_COMMA:
                binding.radioSeparatorCommaFormat.setChecked(true);
                break;
            case CSV_COLON:
                binding.radioSeparatorColonFormat.setChecked(true);
                break;
            case CSV_SEMICOLON:
                binding.radioSeparatorSemicolonFormat.setChecked(true);
                break;
        }

        long startTimeMills = startTime.getTime();
        if (startTimeMills > 0L) {
            mExportStartCalendar.setTimeInMillis(startTimeMills);
            binding.exportStartDate.setText(TransactionFormFragment.DATE_FORMATTER.print(startTimeMills));
            binding.exportStartTime.setText(TransactionFormFragment.TIME_FORMATTER.print(startTimeMills));
            binding.switchExportAll.setChecked(false);
        } else {
            binding.switchExportAll.setChecked(true);
        }

        binding.checkboxPostExportDelete.setChecked(exportParams.shouldDeleteTransactionsAfterExport());
        binding.compression.setChecked(exportParams.isCompressed);

        String rrule = scheduledAction.getRuleString();
        onRecurrenceSet(rrule);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.export_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_save:
                startExport();
                return true;

            case android.R.id.home: {
                Activity activity = getActivity();
                if (activity == null) {
                    Timber.w("Activity expected");
                    return false;
                }
                activity.finish();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        DropboxHelper.retrieveAndSaveToken(requireContext());
    }

    @Override
    public void onPause() {
        super.onPause();
        // When the user try to export sharing to 3rd party service like DropBox
        // then pausing all activities. That cause passcode screen appearing happened.
        // We use a disposable flag to skip this unnecessary passcode screen.
        PasscodeHelper.skipPasscodeScreen(requireContext());
    }

    /**
     * Starts the export of transactions with the specified parameters
     */
    private void startExport() {
        final Activity activity = requireActivity();
        final ExportParams exportParameters = mExportParams;

        if (exportParameters.getExportTarget() == ExportParams.ExportTarget.URI && exportParameters.getExportLocation() == null) {
            mExportStarted = true;
            selectExportFile();
            return;
        }

        if (mBinding.switchExportAll.isChecked()) {
            exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        } else {
            exportParameters.setExportStartTime(new Timestamp(mExportStartCalendar.getTimeInMillis()));
        }

        Timber.i("Commencing async export of transactions");
        new ExportAsyncTask(activity, GnuCashApplication.getActiveBookUID()).execute(exportParameters);

        if (mRecurrenceRule != null) {
            DatabaseAdapter.UpdateMethod updateMethod = DatabaseAdapter.UpdateMethod.replace;
            ScheduledAction scheduledAction = mScheduledAction;
            if (scheduledAction == null) {
                scheduledAction = new ScheduledAction(ScheduledAction.ActionType.BACKUP);
                scheduledAction.setActionUID(BaseModel.generateUID());
                updateMethod = DatabaseAdapter.UpdateMethod.insert;
            }
            scheduledAction.setRecurrence(RecurrenceParser.parse(mEventRecurrence));
            scheduledAction.setTag(exportParameters.toTag());
            ScheduledActionDbAdapter.getInstance().addRecord(scheduledAction, updateMethod);
            mScheduledAction = scheduledAction;
        }

        int position = mBinding.spinnerExportDestination.getSelectedItemPosition();
        PreferenceManager.getDefaultSharedPreferences(activity)
            .edit()
            .putInt(getString(R.string.key_last_export_destination), position)
            .apply();

        // finish the activity will cause the progress dialog to be leaked
        // which would throw an exception
        //getActivity().finish();
    }

    /**
     * Bind views to actions when initializing the export form
     */
    private void bindViewListeners(@NonNull final FragmentExportFormBinding binding) {
        final Context context = binding.getRoot().getContext();
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        // export destination bindings
        ArrayAdapter<CharSequence> destinationAdapter = ArrayAdapter.createFromResource(context,
            R.array.export_destinations, android.R.layout.simple_spinner_item);
        destinationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerExportDestination.setAdapter(destinationAdapter);
        binding.spinnerExportDestination.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view == null) //the item selection is fired twice by the Android framework. Ignore the first one
                    return;
                switch (position) {
                    case TARGET_URI:
                        mExportParams.setExportTarget(ExportParams.ExportTarget.URI);
                        binding.recurrenceOptions.setVisibility(View.VISIBLE);
                        Uri exportUri = mExportParams.getExportLocation();
                        setExportUri(exportUri);
                        break;
                    case TARGET_DROPBOX:
                        setExportUriText(getString(R.string.label_dropbox_export_destination));
                        binding.recurrenceOptions.setVisibility(View.VISIBLE);
                        mExportParams.setExportTarget(ExportParams.ExportTarget.DROPBOX);

                        if (!DropboxHelper.hasToken(context)) {
                            DropboxHelper.authenticate(context);
                        }
                        break;
                    case TARGET_OWNCLOUD:
                        setExportUri(null);
                        binding.recurrenceOptions.setVisibility(View.VISIBLE);
                        mExportParams.setExportTarget(ExportParams.ExportTarget.OWNCLOUD);
                        if (!preferences.getBoolean(getString(R.string.key_owncloud_sync), false)) {
                            OwnCloudDialogFragment ocDialog = OwnCloudDialogFragment.newInstance(null);
                            ocDialog.show(getParentFragmentManager(), "ownCloud dialog");
                        }
                        break;
                    case TARGET_SHARE:
                        setExportUriText(getString(R.string.label_select_destination_after_export));
                        mExportParams.setExportTarget(ExportParams.ExportTarget.SHARING);
                        binding.recurrenceOptions.setVisibility(View.GONE);
                        break;

                    default:
                        mExportParams.setExportTarget(ExportParams.ExportTarget.SD_CARD);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //nothing to see here, move along
            }
        });

        int position = preferences.getInt(getString(R.string.key_last_export_destination), 0);
        binding.spinnerExportDestination.setSelection(position);

        //**************** export start time bindings ******************
        Timestamp timestamp = PreferencesHelper.getLastExportTime(context);
        final long date = timestamp.getTime() - DateUtils.WEEK_IN_MILLIS;
        mExportStartCalendar.setTimeInMillis(date);

        binding.exportStartDate.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                long dateMillis = mExportStartCalendar.getTimeInMillis();
                DatePickerDialogFragment.newInstance(ExportFormFragment.this, dateMillis)
                    .show(getParentFragmentManager(), "date_picker_fragment");
            }
        });
        binding.exportStartDate.setText(TransactionFormFragment.DATE_FORMATTER.print(date));

        binding.exportStartTime.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                long timeMillis = mExportStartCalendar.getTimeInMillis();
                TimePickerDialogFragment.newInstance(ExportFormFragment.this, timeMillis)
                    .show(getParentFragmentManager(), "time_picker_dialog_fragment");
            }
        });
        binding.exportStartTime.setText(TransactionFormFragment.TIME_FORMATTER.print(date));

        binding.switchExportAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                binding.exportStartDate.setEnabled(!isChecked);
                binding.exportStartTime.setEnabled(!isChecked);
            }
        });
        binding.switchExportAll.setChecked(preferences.getBoolean(getString(R.string.key_export_all_transactions), false));

        binding.checkboxPostExportDelete.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mExportParams.setDeleteTransactionsAfterExport(isChecked);
            }
        });
        binding.checkboxPostExportDelete.setChecked(preferences.getBoolean(getString(R.string.key_delete_transactions_after_export), false));

        binding.compression.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mExportParams.isCompressed = isChecked;
            }
        });
        binding.compression.setChecked(preferences.getBoolean(getString(R.string.key_compress_export), true));

        binding.inputRecurrence.setOnClickListener(new RecurrenceViewClickListener(requireActivity(), mRecurrenceRule, this));

        //this part (setting the export format) must come after the recurrence view bindings above
        String keyDefaultExportFormat = getString(R.string.key_default_export_format);
        String defaultExportFormat = preferences.getString(keyDefaultExportFormat, ExportFormat.XML.value);
        ExportFormat defaultFormat = ExportFormat.of(defaultExportFormat);

        List<ExportFormatItem> formatItems = this.formatItems;
        formatItems.clear();
        formatItems.add(new ExportFormatItem(ExportFormat.CSVT, context.getString(ExportFormat.CSVT.labelId)));
        formatItems.add(new ExportFormatItem(ExportFormat.QIF, context.getString(ExportFormat.QIF.labelId)));
        if (isDoubleEntry) {
            formatItems.add(new ExportFormatItem(ExportFormat.XML, context.getString(ExportFormat.XML.labelId)));
            if (defaultFormat == ExportFormat.OFX) {
                defaultFormat = ExportFormat.XML;
            }
        } else {
            formatItems.add(new ExportFormatItem(ExportFormat.OFX, context.getString(ExportFormat.OFX.labelId)));
            if (defaultFormat == ExportFormat.XML) {
                defaultFormat = ExportFormat.OFX;
            }
        }
        final ArrayAdapter<ExportFormatItem> formatAdapter = new ArrayAdapter(context, android.R.layout.simple_spinner_item, formatItems);
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.valueExportFormat.setAdapter(formatAdapter);
        binding.valueExportFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view == null) //the item selection is fired twice by the Android framework. Ignore the first one
                    return;
                ExportFormatItem item = formatAdapter.getItem(position);
                onFormatSelected(binding, item.value);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //nothing to see here, move along
            }
        });
        int formatIndex = NO_POSITION;
        for (int i = 0; i < formatItems.size(); i++) {
            ExportFormatItem item = formatItems.get(i);
            if (item.value == defaultFormat) {
                formatIndex = i;
                break;
            }
        }
        binding.valueExportFormat.setSelection(formatIndex);

        binding.radioSeparatorCommaFormat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mExportParams.setCsvSeparator(CSV_COMMA);
                }
            }
        });
        binding.radioSeparatorColonFormat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mExportParams.setCsvSeparator(CSV_COLON);
                }
            }
        });
        binding.radioSeparatorSemicolonFormat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mExportParams.setCsvSeparator(CSV_SEMICOLON);
                }
            }
        });
    }

    /**
     * Display the file path of the file where the export will be saved
     *
     * @param filepath Path to export file. If {@code null}, the view will be hidden and nothing displayed
     */
    private void setExportUriText(String filepath) {
        if (TextUtils.isEmpty(filepath)) {
            mBinding.targetUri.setVisibility(View.GONE);
            mBinding.targetUri.setText("");
        } else {
            mBinding.targetUri.setText(filepath);
            mBinding.targetUri.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Display the file path of the file where the export will be saved
     *
     * @param uri URI to export file. If {@code null}, the view will be hidden and nothing displayed
     */
    private void setExportUri(@Nullable Uri uri) {
        mExportParams.setExportLocation(uri);
        if (uri == null) {
            setExportUriText("");
        } else {
            setExportUriText(getDocumentName(uri, getContext()));
        }
    }

    /**
     * Open a chooser for user to pick a file to export to
     */
    private void selectExportFile() {
        String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();
        String filename = Exporter.buildExportFilename(mExportParams.getExportFormat(), mExportParams.isCompressed, bookName);

        Intent createIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, filename);
        try {
            startActivityForResult(createIntent, REQUEST_EXPORT_FILE);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "Cannot create document for export");
            if (isVisible()) {
                View view = getView();
                assert view != null;
                Snackbar.make(view, R.string.toast_install_file_manager, Snackbar.LENGTH_LONG).show();
            } else {
                Toast.makeText(requireContext(), R.string.toast_install_file_manager, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRecurrenceSet(String rrule) {
        Timber.i("Export reoccurs: %s", rrule);
        Context context = mBinding.inputRecurrence.getContext();
        String repeatString = null;
        if (!TextUtils.isEmpty(rrule)) {
            try {
                mEventRecurrence.parse(rrule);
                mRecurrenceRule = rrule;
                repeatString = EventRecurrenceFormatter.getRepeatString(context, context.getResources(), mEventRecurrence, true);
            } catch (Exception e) {
                Timber.e(e, "Bad recurrence for [%s]", rrule);
            }
        }
        if (TextUtils.isEmpty(repeatString)) {
            repeatString = context.getString(R.string.label_tap_to_create_schedule);
        }
        mBinding.inputRecurrence.setText(repeatString);
    }

    /**
     * Callback for when the activity chooser dialog is completed
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_EXPORT_FILE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        takePersistableUriPermission(requireContext(), data);
                        Uri location = data.getData();
                        setExportUri(location);
                    } else {
                        setExportUri(null);
                    }

                    if (mExportStarted) {
                        startExport();
                    }
                }
                break;
        }
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        mExportStartCalendar.set(Calendar.YEAR, year);
        mExportStartCalendar.set(Calendar.MONTH, month);
        mExportStartCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        mBinding.exportStartDate.setText(TransactionFormFragment.DATE_FORMATTER.print(mExportStartCalendar.getTimeInMillis()));
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        mExportStartCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        mExportStartCalendar.set(Calendar.MINUTE, minute);
        mBinding.exportStartTime.setText(TransactionFormFragment.TIME_FORMATTER.print(mExportStartCalendar.getTimeInMillis()));
    }

    private static class ExportFormatItem {
        public final ExportFormat value;
        public final String label;

        private ExportFormatItem(ExportFormat value, String label) {
            this.value = value;
            this.label = label;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }
}

// Gotten from: https://stackoverflow.com/a/31720191
class OptionsViewAnimationUtils {

    public static void expand(final View v) {
        v.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int targetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = interpolatedTime == 1
                    ? ViewGroup.LayoutParams.WRAP_CONTENT
                    : (int) (targetHeight * interpolatedTime);
                v.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        a.setDuration((int) (3 * targetHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    public static void collapse(final View v) {
        final int initialHeight = v.getMeasuredHeight();

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        a.setDuration((int) (3 * initialHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }
}
