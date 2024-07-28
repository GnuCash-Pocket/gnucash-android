/*
 * Copyright (c) 2018 Semyannikov Gleb <nightdevgame@gmail.com>
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

package org.gnucash.android.export.csv;

import android.content.Context;

import androidx.annotation.NonNull;

import org.gnucash.android.R;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.Account;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

/**
 * Creates a GnuCash CSV account representation of the accounts and transactions
 *
 * @author Semyannikov Gleb <nightdevgame@gmail.com>
 */
public class CsvAccountExporter extends Exporter {
    private final char mCsvSeparator;

    /**
     * Overloaded constructor.
     * Creates an exporter with an already open database instance.
     *
     * @param context The context.
     * @param params Parameters for the export
     * @param bookUID The book UID.
     */
    public CsvAccountExporter(@NonNull Context context,
                              @NonNull ExportParams params,
                              @NonNull String bookUID) {
        super(context, params, bookUID);
        mCsvSeparator = params.getCsvSeparator();
    }

    @Override
    public List<String> generateExport() throws ExporterException {
        String outputFile = getExportCacheFilePath();
        try (CsvWriter writer = new CsvWriter(new FileWriter(outputFile), String.valueOf(mCsvSeparator))) {
            generateExport(writer);
            close();
        } catch (IOException ex) {
            Timber.e(ex, "Error exporting CSV");
            throw new ExporterException(mExportParams, ex);
        }

        return Arrays.asList(outputFile);
    }

    /**
     * Writes out all the accounts in the system as CSV to the provided writer
     *
     * @param csvWriter Destination for the CSV export
     * @throws ExporterException if an error occurred while writing to the stream
     */
    public void generateExport(final CsvWriter csvWriter) throws ExporterException {
        try {
            List<String> names = Arrays.asList(mContext.getResources().getStringArray(R.array.csv_account_headers));
            List<Account> accounts = mAccountsDbAdapter.getSimpleAccountList();

            for (int i = 0; i < names.size(); i++) {
                csvWriter.writeToken(names.get(i));
            }

            csvWriter.newLine();
            for (Account account : accounts) {
                csvWriter.writeToken(account.getAccountType().toString());
                csvWriter.writeToken(account.getFullName());
                csvWriter.writeToken(account.getName());

                csvWriter.writeToken(null); //Account code
                csvWriter.writeToken(account.getDescription());
                csvWriter.writeToken(account.getColorHexString());
                csvWriter.writeToken(null); //Account notes

                csvWriter.writeToken(account.getCommodity().getCurrencyCode());
                csvWriter.writeToken(account.getCommodity().getNamespace());
                csvWriter.writeToken(account.isHidden() ? "T" : "F");

                csvWriter.writeToken("F"); //Tax
                csvWriter.writeEndToken(account.isPlaceholderAccount() ? "T" : "F");
            }
        } catch (IOException e) {
            Timber.e(e);
            throw new ExporterException(mExportParams, e);
        }
    }
}
