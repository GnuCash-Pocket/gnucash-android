/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.export;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.gnucash.android.ui.export.ExportFormFragment;
import org.gnucash.android.util.TimestampHelper;

import java.sql.Timestamp;

/**
 * Encapsulation of the parameters used for exporting transactions.
 * The parameters are determined by the user in the export dialog and are then transmitted to the asynchronous task which
 * actually performs the export.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see ExportFormFragment
 * @see ExportAsyncTask
 */
public class ExportParams {
    /**
     * Options for the destination of the exported transactions file.
     * It could be stored on the {@link #SD_CARD} or exported through another program via {@link #SHARING}
     */
    public enum ExportTarget {
        SD_CARD("SD Card"),
        SHARING("External Service"),
        DROPBOX("Dropbox"),
        OWNCLOUD("ownCloud"),
        URI("Sync Service");

        private final String mDescription;

        ExportTarget(String description) {
            mDescription = description;
        }

        public String getDescription() {
            return mDescription;
        }
    }

    private static final String TAG_SEPARATOR = ";";

    /**
     * Format to use for the exported transactions
     * By default, the {@link ExportFormat#XML} format is used
     */
    @NonNull
    private ExportFormat mExportFormat = ExportFormat.XML;

    /**
     * All transactions created after this date will be exported
     */
    private Timestamp mExportStartTime = TimestampHelper.getTimestampFromEpochZero();

    /**
     * Flag to determine if all transactions should be deleted after exporting is complete
     * By default no transactions are deleted
     */
    private boolean mDeleteTransactionsAfterExport = false;

    /**
     * Destination for the exported transactions
     */
    private ExportTarget mExportTarget = ExportTarget.SD_CARD;

    /**
     * Location to save the file name being exported.
     * This is typically a Uri and used for {@link ExportTarget#URI} target
     */
    private Uri mExportLocation;

    public static final char CSV_COMMA = ',';
    public static final char CSV_COLON = ':';
    public static final char CSV_SEMICOLON = ';';

    /**
     * CSV-separator char
     */
    private char mCsvSeparator = CSV_COMMA;

    /**
     * Compress the file using gzip?
     */
    public boolean isCompressed = false;

    /**
     * Creates a new set of parameters.
     */
    public ExportParams() {
        super();
    }

    /**
     * Creates a new set of parameters and specifies the export format
     *
     * @param format Format to use when exporting the transactions
     */
    public ExportParams(@NonNull ExportFormat format) {
        this();
        setExportFormat(format);
    }

    /**
     * Return the format used for exporting
     *
     * @return {@link ExportFormat}
     */
    @NonNull
    public ExportFormat getExportFormat() {
        return mExportFormat;
    }

    /**
     * Set the export format
     *
     * @param exportFormat {@link ExportFormat}
     */
    public void setExportFormat(@NonNull ExportFormat exportFormat) {
        this.mExportFormat = exportFormat;
    }

    /**
     * Return date from which to start exporting transactions
     * <p>Transactions created or modified after this timestamp will be exported</p>
     *
     * @return Timestamp from which to export
     */
    public Timestamp getExportStartTime() {
        return mExportStartTime;
    }

    /**
     * Set the timestamp after which all transactions created/modified will be exported
     *
     * @param exportStartTime Timestamp
     */
    public void setExportStartTime(Timestamp exportStartTime) {
        this.mExportStartTime = exportStartTime;
    }

    /**
     * Returns flag whether transactions should be deleted after export
     *
     * @return <code>true</code> if all transactions will be deleted, <code>false</code> otherwise
     */
    public boolean shouldDeleteTransactionsAfterExport() {
        return mDeleteTransactionsAfterExport;
    }

    /**
     * Set flag to delete transactions after exporting is complete
     *
     * @param deleteTransactions Set to <code>true</code> if transactions should be deleted, false if not
     */
    public void setDeleteTransactionsAfterExport(boolean deleteTransactions) {
        this.mDeleteTransactionsAfterExport = deleteTransactions;
    }

    /**
     * Get the target for the exported file
     *
     * @return {@link org.gnucash.android.export.ExportParams.ExportTarget}
     */
    public ExportTarget getExportTarget() {
        return mExportTarget;
    }

    /**
     * Set the target for the exported transactions
     *
     * @param mExportTarget Target for exported transactions
     */
    public void setExportTarget(ExportTarget mExportTarget) {
        this.mExportTarget = mExportTarget;
    }

    /**
     * Return the location where the file should be exported to.
     * When used with {@link ExportTarget#URI}, the returned value will be a URI which can be parsed
     * with {@link Uri#parse(String)}
     *
     * @return String representing export file destination.
     */
    public Uri getExportLocation() {
        return mExportLocation;
    }

    /**
     * Set the location where to export the file
     *
     * @param exportLocation Destination of the export
     */
    public void setExportLocation(Uri exportLocation) {
        mExportLocation = exportLocation;
    }

    /**
     * Get the CSV-separator char
     *
     * @return CSV-separator char
     */
    public char getCsvSeparator() {
        return mCsvSeparator;
    }

    /**
     * Set the CSV-separator char
     *
     * @param separator CSV-separator char
     */
    public void setCsvSeparator(char separator) {
        mCsvSeparator = separator;
    }

    @Override
    public String toString() {
        return "Export all transactions created since " + TimestampHelper.getUtcStringFromTimestamp(mExportStartTime) + " UTC"
            + " as " + mExportFormat.name() + " to " + mExportTarget.name() + (mExportLocation != null ? " (" + mExportLocation + ")" : "");
    }

    /**
     * Returns the export parameters formatted as CSV.
     * <p>The CSV format is: exportformat;exportTarget;shouldExportAllTransactions;shouldDeleteAllTransactions</p>
     *
     * @return String containing CSV format of ExportParams
     */
    public String toTag() {
        return mExportFormat.name() +
            TAG_SEPARATOR + mExportTarget.name() +
            TAG_SEPARATOR + TimestampHelper.getUtcStringFromTimestamp(mExportStartTime) +
            TAG_SEPARATOR + mDeleteTransactionsAfterExport +
            TAG_SEPARATOR + (mExportLocation != null ? mExportLocation : "") +
            TAG_SEPARATOR + isCompressed;
    }

    /**
     * Parses csv generated by {@link #toTag()} to create
     *
     * @param tag String containing list of params
     * @return ExportParams from the tag
     */
    public static ExportParams parseTag(String tag) {
        String[] tokens = tag.split(TAG_SEPARATOR);
        ExportParams params = new ExportParams(ExportFormat.of(tokens[0]));
        params.setExportTarget(ExportTarget.valueOf(tokens[1]));
        params.setExportStartTime(TimestampHelper.getTimestampFromUtcString(tokens[2]));
        params.setDeleteTransactionsAfterExport(Boolean.parseBoolean(tokens[3]));
        if (tokens.length >= 5) {
            params.setExportLocation(Uri.parse(tokens[4]));
            if (tokens.length >= 6) {
                params.isCompressed = Boolean.parseBoolean(tokens[5]);
            }
        }
        return params;
    }
}
