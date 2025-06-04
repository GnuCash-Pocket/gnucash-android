/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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

import android.content.Context;
import android.net.Uri;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.export.xml.GncXmlExporter;
import org.gnucash.android.importer.GncXmlImporter;
import org.gnucash.android.test.unit.GnuCashTest;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import timber.log.Timber;

/**
 * Test backup and restore functionality
 */
public class BackupTest extends GnuCashTest {

    @Before
    public void setUp() {
        loadDefaultAccounts();
    }

    @Test
    public void shouldCreateBackupFileName() throws Exporter.ExporterException {
        Context context = GnuCashApplication.getAppContext();
        String bookUID = GnuCashApplication.getActiveBookUID();
        Exporter exporter = new GncXmlExporter(context, new ExportParams(ExportFormat.XML), bookUID);
        Uri uriExported = exporter.generateExport();

        assertThat(uriExported).isNotNull();
        assertThat(uriExported.getScheme()).isEqualTo("file");
        File file = new File(uriExported.getPath());
        assertThat(file)
            .exists()
            .hasExtension(ExportFormat.XML.extension.substring(1));
    }

    /**
     * Loads the default accounts from file resource
     */
    private void loadDefaultAccounts() {
        try {
            Context context = GnuCashApplication.getAppContext();
            String bookUID = GncXmlImporter.parse(context, context.getResources().openRawResource(R.raw.default_accounts));
            BooksDbAdapter.getInstance().setActive(bookUID);
            assertThat(BooksDbAdapter.getInstance().getActiveBookUID()).isEqualTo(bookUID);
            assertThat(GnuCashApplication.getActiveBookUID()).isEqualTo(bookUID);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Timber.e(e);
            throw new RuntimeException("Could not create default accounts");
        }
    }
}
