/*
 * Copyright (c) 2016 Àlex Magaz Graça <alexandre.magaz@gmail.com>
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
package org.gnucash.android.test.unit.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.export.ofx.OfxExporter;
import org.gnucash.android.export.ofx.OfxHelper;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.test.unit.GnuCashTest;
import org.gnucash.android.util.TimestampHelper;
import org.junit.Test;

import java.io.File;
import java.util.Calendar;
import java.util.TimeZone;

public class OfxExporterTest extends GnuCashTest {

    /**
     * When there aren't new or modified transactions, the OFX exporter
     * shouldn't create any file.
     */
    @Test
    public void testWithNoTransactionsToExport_shouldNotCreateAnyFile() {
        Context context = GnuCashApplication.getAppContext();
        ExportParams exportParameters = new ExportParams(ExportFormat.OFX);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);
        OfxExporter exporter = new OfxExporter(context, exportParameters, GnuCashApplication.getActiveBookUID());
        assertThrows(Exporter.ExporterException.class, () -> exporter.generateExport());
    }

    /**
     * Test that OFX files are generated
     */
    @Test
    public void testGenerateOFXExport() {
        Context context = GnuCashApplication.getAppContext();
        AccountsDbAdapter accountsDbAdapter = GnuCashApplication.getAccountsDbAdapter();

        Account account = new Account("Basic Account");
        Transaction transaction = new Transaction("One transaction");
        transaction.addSplit(new Split(Money.createZeroInstance("EUR"), account.getUID()));
        account.addTransaction(transaction);

        accountsDbAdapter.addRecord(account);

        ExportParams exportParameters = new ExportParams(ExportFormat.OFX);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);

        OfxExporter exporter = new OfxExporter(context, exportParameters, GnuCashApplication.getActiveBookUID());
        Uri exportedFile = exporter.generateExport();

        assertThat(exportedFile).isNotNull();
        File file = new File(exportedFile.getPath());
        assertThat(file).exists().hasExtension("ofx");
        assertThat(file.length()).isGreaterThan(0L);
        file.delete();
    }

    @Test
    public void testDateTime() {
        TimeZone tz = TimeZone.getTimeZone("EST");
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(tz);
        cal.set(Calendar.YEAR, 1996);
        cal.set(Calendar.MONTH, Calendar.DECEMBER);
        cal.set(Calendar.DAY_OF_MONTH, 5);
        cal.set(Calendar.HOUR_OF_DAY, 13);
        cal.set(Calendar.MINUTE, 22);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 124);

        String formatted = OfxHelper.getOfxFormattedTime(cal.getTimeInMillis(), tz);
        assertThat(formatted).isEqualTo("19961205132200.124[-5:EST]");

        cal.set(Calendar.MONTH, Calendar.OCTOBER);
        formatted = OfxHelper.getOfxFormattedTime(cal.getTimeInMillis(), tz);
        assertThat(formatted).isEqualTo("19961005142200.124[-4:EDT]");
    }
}