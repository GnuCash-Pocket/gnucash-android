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

import static com.opencsv.ICSVWriter.RFC4180_LINE_END;
import static org.gnucash.android.util.ColorExtKt.formatRGB;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

import org.gnucash.android.R;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.gnc.GncProgressListener;
import org.gnucash.android.model.Account;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Creates a GnuCash CSV account representation of the accounts and transactions
 *
 * @author Semyannikov Gleb <nightdevgame@gmail.com>
 */
public class CsvAccountExporter extends Exporter {

    /**
     * Overloaded constructor.
     * Creates an exporter with an already open database instance.
     *
     * @param context The context.
     * @param params  Parameters for the export
     * @param bookUID The book UID.
     */
    public CsvAccountExporter(
        @NonNull Context context,
        @NonNull ExportParams params,
        @NonNull String bookUID,
        @Nullable GncProgressListener listener
    ) {
        super(context, params, bookUID, listener);
    }

    /**
     * Overloaded constructor.
     * Creates an exporter with an already open database instance.
     *
     * @param context The context.
     * @param params  Parameters for the export
     * @param bookUID The book UID.
     */
    public CsvAccountExporter(
        @NonNull Context context,
        @NonNull ExportParams params,
        @NonNull String bookUID
    ) {
        this(context, params, bookUID, null);
    }

    @Override
    protected void writeExport(@NonNull Writer writer, @NonNull ExportParams exportParams) throws ExporterException, IOException {
        ICSVWriter csvWriter = new CSVWriterBuilder(writer)
            .withSeparator(exportParams.getCsvSeparator())
            .withLineEnd(RFC4180_LINE_END)
            .build();
        writeExport(csvWriter);
        csvWriter.close();
    }

    /**
     * Writes out all the accounts in the system as CSV to the provided writer
     *
     * @param writer Destination for the CSV export
     */
    public void writeExport(@NonNull ICSVWriter writer) {
        String[] names = mContext.getResources().getStringArray(R.array.csv_account_headers);
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccounts();
        if (listener != null) {
            listener.onAccountCount(accounts.size());
        }
        writer.writeNext(names);

        final String[] fields = new String[names.length];
        for (Account account : accounts) {
            if (account.isRoot()) continue;
            if (account.isTemplate()) continue;
            cancellationSignal.throwIfCanceled();

            writeAccount(fields, account);
            writer.writeNext(fields);
        }
    }

    private void writeAccount(@NonNull String[] fields, @NonNull Account account) {
        fields[0] = account.getAccountType().name();
        fields[1] = account.getFullName();
        fields[2] = account.getName();

        fields[3] = ""; //Account code
        fields[4] = account.getDescription();
        fields[5] = formatRGB(account.getColor());
        fields[6] = orEmpty(account.getNote());

        fields[7] = account.getCommodity().getCurrencyCode();
        fields[8] = account.getCommodity().getNamespace();
        fields[9] = format(account.isHidden());
        fields[10] = format(false); //Tax
        fields[11] = format(account.isPlaceholder());

        if (listener != null) {
            listener.onAccount(account);
        }
    }

    @NonNull
    private String orEmpty(@Nullable String s) {
        return (s != null) ? s : "";
    }

    private String format(boolean value) {
        return value ? "T" : "F";
    }
}
