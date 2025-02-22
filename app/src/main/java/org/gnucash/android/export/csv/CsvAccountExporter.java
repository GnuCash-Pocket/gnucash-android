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

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

import org.gnucash.android.R;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.Account;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

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
     * @param params  Parameters for the export
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
        try (Writer writer = new FileWriter(outputFile)) {
            ICSVWriter csvWriter = new CSVWriterBuilder(writer).withSeparator(mCsvSeparator).build();
            generateExport(csvWriter);
            csvWriter.close();
            return Collections.singletonList(outputFile);
        } catch (Exception ex) {
            throw new ExporterException(mExportParams, ex);
        } finally {
            try {
                close();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Writes out all the accounts in the system as CSV to the provided writer
     *
     * @param csvWriter Destination for the CSV export
     * @throws IOException if an error occurred while writing to the stream
     */
    public void generateExport(final ICSVWriter csvWriter) throws IOException {
        String[] names = mContext.getResources().getStringArray(R.array.csv_account_headers);
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccountList();

        csvWriter.writeNext(names);

        final String[] fields = new String[names.length];
        for (Account account : accounts) {
            fields[0] = account.getAccountType().toString();
            fields[1] = account.getFullName();
            fields[2] = account.getName();

            fields[3] = null; //Account code
            fields[4] = account.getDescription();
            fields[5] = account.getColorHexString();
            fields[6] = null; //Account notes

            fields[7] = account.getCommodity().getCurrencyCode();
            fields[8] = account.getCommodity().getNamespace();
            fields[9] = format(account.isHidden());

            fields[10] = format(false); //Tax
            fields[11] = format(account.isPlaceholder());

            csvWriter.writeNext(fields);
        }
    }

    private String format(boolean value) {
        return value ? "T" : "F";
    }
}
