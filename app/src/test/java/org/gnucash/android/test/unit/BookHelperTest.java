package org.gnucash.android.test.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import android.database.sqlite.SQLiteDatabase;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.importer.GncXmlHandler;
import org.gnucash.android.util.ConsoleTree;
import org.junit.After;
import org.junit.Before;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import timber.log.Timber;

public abstract class BookHelperTest extends GnuCashTest {
    protected SQLiteDatabase mImportedDb;
    protected BooksDbAdapter mBooksDbAdapter;
    protected TransactionsDbAdapter mTransactionsDbAdapter;
    protected AccountsDbAdapter mAccountsDbAdapter;
    protected ScheduledActionDbAdapter mScheduledActionDbAdapter;
    protected CommoditiesDbAdapter mCommoditiesDbAdapter;
    protected BudgetsDbAdapter mBudgetsDbAdapter;

    static {
        Timber.plant((Timber.Tree) new ConsoleTree(BuildConfig.DEBUG));
    }

    protected String importGnuCashXml(String filename) {
        SAXParser parser;
        GncXmlHandler handler = null;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser();
            XMLReader reader = parser.getXMLReader();
            handler = new GncXmlHandler();
            reader.setContentHandler(handler);
            InputStream inputStream = openResourceStream(filename);
            InputSource inputSource = new InputSource(new BufferedInputStream(inputStream));
            reader.parse(inputSource);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Timber.e(e);
            fail();
        }
        String bookUID = handler.getImportedBookUID();
        setUpDbAdapters(bookUID);
        return bookUID;
    }

    private void setUpDbAdapters(String bookUID) {
        DatabaseHelper databaseHelper = new DatabaseHelper(GnuCashApplication.getAppContext(), bookUID);
        SQLiteDatabase mainDb = databaseHelper.getReadableDatabase();
        mCommoditiesDbAdapter = new CommoditiesDbAdapter(mainDb);
        mTransactionsDbAdapter = new TransactionsDbAdapter(mCommoditiesDbAdapter);
        mAccountsDbAdapter = new AccountsDbAdapter(mTransactionsDbAdapter);
        RecurrenceDbAdapter recurrenceDbAdapter = new RecurrenceDbAdapter(mainDb);
        mScheduledActionDbAdapter = new ScheduledActionDbAdapter(recurrenceDbAdapter);
        mBudgetsDbAdapter = new BudgetsDbAdapter(recurrenceDbAdapter);
        mImportedDb = mainDb;
    }

    @Before
    public void setUp() throws Exception {
        mBooksDbAdapter = BooksDbAdapter.getInstance();
        mBooksDbAdapter.deleteAllRecords();
        assertThat(mBooksDbAdapter.getRecordsCount()).isZero();
    }

    @After
    public void tearDown() throws Exception {
        if (mTransactionsDbAdapter != null) {
            mTransactionsDbAdapter.close();
            mTransactionsDbAdapter = null;
        }
        if (mAccountsDbAdapter != null) {
            mAccountsDbAdapter.close();
            mAccountsDbAdapter = null;
        }
        if (mScheduledActionDbAdapter != null) {
            mScheduledActionDbAdapter.close();
            mScheduledActionDbAdapter = null;
        }
        if (mImportedDb != null) {
            mImportedDb.close();
            mImportedDb = null;
        }
    }
}
