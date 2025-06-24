/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 - 2015 Yongxin Wang <fefe.wyx@gmail.com>
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

package org.gnucash.android.importer;

import static org.gnucash.android.db.adapter.AccountsDbAdapter.ROOT_ACCOUNT_NAME;
import static org.gnucash.android.db.adapter.AccountsDbAdapter.TEMPLATE_ACCOUNT_NAME;
import static org.gnucash.android.export.xml.GncXmlHelper.ATTR_KEY_TYPE;
import static org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_FRAME;
import static org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_NUMERIC;
import static org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_STRING;
import static org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_BOOK;
import static org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_BUDGET;
import static org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_COMMODITY;
import static org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_PRICE;
import static org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_SCHEDXACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_TRANSACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_COLOR;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_CREDIT_FORMULA;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_CREDIT_NUMERIC;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_DEBIT_FORMULA;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_DEBIT_NUMERIC;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_DEFAULT_TRANSFER_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_EXPORTED;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_FAVORITE;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_HIDDEN;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_NOTES;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_PLACEHOLDER;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_SCHED_XACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_SPLIT_ACCOUNT_SLOT;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_BOOK;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_BUDGET;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_CD;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_COMMODITY;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_GNUCASH;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_GNUCASH_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_PRICE;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_RECURRENCE;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_SLOT;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_SPLIT;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_SX;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_TRANSACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.NS_TS;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ADVANCE_CREATE_DAYS;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ADVANCE_REMIND_DAYS;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_AUTO_CREATE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_AUTO_CREATE_NOTIFY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_BOOK;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COUNT_DATA;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_CURRENCY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_DATE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_DATE_ENTERED;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_DATE_POSTED;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_DESCRIPTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ENABLED;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_END;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_FRACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_GDATE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ID;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_INSTANCE_COUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_KEY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_LAST;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_MEMO;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_MULT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_NAME;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_NUM_OCCUR;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_NUM_PERIODS;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PARENT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PERIOD_TYPE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_QUANTITY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_QUOTE_SOURCE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_QUOTE_TZ;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_RECONCILED_DATE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_RECURRENCE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_RECURRENCE_PERIOD;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_REM_OCCUR;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ROOT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SCHEDULED_ACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SLOT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SLOTS;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SOURCE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SPACE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SPLIT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_START;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TEMPLATE_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TEMPLATE_TRANSACTIONS;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TIME;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TITLE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TRANSACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TYPE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_VALUE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_WEEKEND_ADJ;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_XCODE;
import static org.gnucash.android.export.xml.GncXmlHelper.parseDate;
import static org.gnucash.android.export.xml.GncXmlHelper.parseDateTime;
import static org.gnucash.android.export.xml.GncXmlHelper.parseSplitAmount;

import android.content.ContentValues;
import android.content.Context;
import android.os.CancellationSignal;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.DatabaseHolder;
import org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.gnc.GncProgressListener;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Price;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Slot;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.model.WeekendAdjust;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.Closeable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TimeZone;

import timber.log.Timber;

/**
 * Handler for parsing the GnuCash XML file.
 * The discovered accounts and transactions are automatically added to the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
public class GncXmlHandler extends DefaultHandler implements Closeable {

    /**
     * Adapter for saving the imported accounts
     */
    @NonNull
    private AccountsDbAdapter mAccountsDbAdapter;

    /**
     * StringBuilder for accumulating characters between XML tags
     */
    private final StringBuilder mContent = new StringBuilder();

    /**
     * Reference to account which is built when each account tag is parsed in the XML file
     */
    private Account mAccount;

    /**
     * All the accounts found in a file to be imported, used for bulk import mode
     */
    private final List<Account> mAccountList = new ArrayList<>();

    /**
     * Map of the template accounts to the template transactions UIDs
     */
    private final Map<String, String> mTemplateAccountToTransactionMap = new HashMap<>();

    /**
     * Account map for quick referencing from UID
     */
    private final Map<String, Account> mAccountMap = new HashMap();

    /**
     * ROOT account of the imported book
     */
    private Account mRootAccount;
    private Account rootTemplateAccount;

    /**
     * Transaction instance which will be built for each transaction found
     */
    private Transaction mTransaction;

    /**
     * Accumulate attributes of splits found in this object
     */
    private Split mSplit;

    /**
     * price table entry
     */
    private Price mPrice;

    /**
     * The list for all added split for autobalancing
     */
    private final List<Split> mAutoBalanceSplits = new ArrayList<>();

    /**
     * {@link ScheduledAction} instance for each scheduled action parsed
     */
    private ScheduledAction mScheduledAction;

    private Budget mBudget;
    private Recurrence mRecurrence;
    private Commodity mCommodity;
    private final Map<String, Commodity> mCommodities = new HashMap<>();

    private boolean mInTemplates = false;

    private final Stack<Slot> slots = new Stack<>();

    private Account budgetAccount = null;
    private Long budgetPeriod = null;

    /**
     * Flag which says to ignore template transactions until we successfully parse a split amount
     * Is updated for each transaction template split parsed
     */
    private boolean mIgnoreTemplateTransaction = true;

    /**
     * Flag which notifies the handler to ignore a scheduled action because some error occurred during parsing
     */
    private boolean mIgnoreScheduledAction = false;

    /**
     * Used for parsing old backup files where recurrence was saved inside the transaction.
     * Newer backup files will not require this
     *
     * @deprecated Use the new scheduled action elements instead
     */
    @Deprecated
    private long mRecurrencePeriod = 0;

    @NonNull
    private final BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
    @NonNull
    private TransactionsDbAdapter mTransactionsDbAdapter;
    @NonNull
    private ScheduledActionDbAdapter mScheduledActionsDbAdapter;
    @NonNull
    private CommoditiesDbAdapter mCommoditiesDbAdapter;
    @NonNull
    private PricesDbAdapter mPricesDbAdapter;
    @NonNull
    private final Map<String, Integer> mCurrencyCount = new HashMap<>();
    @NonNull
    private BudgetsDbAdapter mBudgetsDbAdapter;
    private final Book mBook = new Book();
    @NonNull
    private DatabaseHolder holder;
    @NonNull
    private final Context context;
    @Nullable
    private final GncProgressListener listener;
    @Nullable
    private String countDataType;
    private boolean isValidRoot = false;
    private boolean hasBookElement = false;
    private final Stack<ElementName> elementNames = new Stack<>();
    @NonNull
    private final CancellationSignal cancellationSignal;

    /**
     * Creates a handler for handling XML stream events when parsing the XML backup file
     */
    public GncXmlHandler() {
        this(GnuCashApplication.getAppContext(), null);
    }

    /**
     * Creates a handler for handling XML stream events when parsing the XML backup file
     */
    public GncXmlHandler(@NonNull Context context, @Nullable GncProgressListener listener) {
        this(context, listener, new CancellationSignal());
    }

    /**
     * Creates a handler for handling XML stream events when parsing the XML backup file
     */
    public GncXmlHandler(@NonNull Context context, @Nullable GncProgressListener listener, @NonNull CancellationSignal cancellationSignal) {
        super();
        this.context = context;
        this.listener = listener;
        this.cancellationSignal = cancellationSignal;
        initDb(mBook.getUID());
    }

    private void initDb(@NonNull String bookUID) {
        DatabaseHelper databaseHelper = new DatabaseHelper(context, bookUID);
        holder = databaseHelper.getHolder();
        mCommoditiesDbAdapter = new CommoditiesDbAdapter(holder);
        mPricesDbAdapter = new PricesDbAdapter(mCommoditiesDbAdapter);
        mTransactionsDbAdapter = new TransactionsDbAdapter(mCommoditiesDbAdapter);
        mAccountsDbAdapter = new AccountsDbAdapter(mTransactionsDbAdapter, mPricesDbAdapter);
        RecurrenceDbAdapter recurrenceDbAdapter = new RecurrenceDbAdapter(holder);
        mScheduledActionsDbAdapter = new ScheduledActionDbAdapter(recurrenceDbAdapter);
        mBudgetsDbAdapter = new BudgetsDbAdapter(recurrenceDbAdapter);

        Timber.d("before clean up db");
        // disable foreign key. The database structure should be ensured by the data inserted.
        // it will make insertion much faster.
        mAccountsDbAdapter.enableForeignKey(false);

        recurrenceDbAdapter.deleteAllRecords();
        mBudgetsDbAdapter.deleteAllRecords();
        mPricesDbAdapter.deleteAllRecords();
        mScheduledActionsDbAdapter.deleteAllRecords();
        mTransactionsDbAdapter.deleteAllRecords();
        mAccountsDbAdapter.deleteAllRecords();

        mCommodities.clear();
        List<Commodity> commodities = mCommoditiesDbAdapter.getAllRecords();
        for (Commodity commodity : commodities) {
            mCommodities.put(commodity.getKey(), commodity);
        }
    }

    private void maybeInitDb(@Nullable String bookUIDOld, @NonNull String bookUIDNew) {
        if (bookUIDOld != null && !bookUIDOld.equals(bookUIDNew)) {
            holder.close();
            initDb(bookUIDNew);
        }
    }

    @Override
    public void startElement(String uri, String localName,
                             String qualifiedName, Attributes attributes) throws SAXException {
        cancellationSignal.throwIfCanceled();
        elementNames.push(new ElementName(uri, localName, qualifiedName));
        if (!isValidRoot) {
            if (TAG_ROOT.equals(localName) || AccountsTemplate.TAG_ROOT.equals(localName)) {
                isValidRoot = true;
                return;
            }
            throw new SAXException("Expected root element");
        }

        switch (localName) {
            case TAG_BOOK:
                handleStartBook(uri);
                break;
            case TAG_ACCOUNT:
                handleStartAccount(uri);
                break;
            case TAG_TRANSACTION:
                handleStartTransaction();
                break;
            case TAG_SPLIT:
                handleStartSplit(uri);
                break;
            case TAG_TEMPLATE_TRANSACTIONS:
                handleStartTemplateTransactions();
                break;
            case TAG_SCHEDULED_ACTION:
                handleStartScheduledAction();
                break;
            case TAG_PRICE:
                handleStartPrice();
                break;
            case TAG_CURRENCY:
                handleStartCurrency();
                break;
            case TAG_COMMODITY:
                handleStartCommodity();
                break;
            case TAG_BUDGET:
                handleStartBudget(uri);
                break;
            case TAG_RECURRENCE:
                handleStartRecurrence(uri);
                break;
            case TAG_SLOT:
                handleStartSlot();
                break;
            case TAG_VALUE:
                handleStartValue(uri, attributes);
                break;
            case TAG_COUNT_DATA:
                handleStartCountData(attributes);
                break;
        }
    }

    private void handleStartTemplateTransactions() {
        mInTemplates = true;
    }

    private void handleStartSlot() {
        slots.push(Slot.empty());
    }

    @Override
    public void endElement(String uri, String localName, String qualifiedName) throws SAXException {
        ElementName elementName = elementNames.pop();
        if (!isValidRoot) {
            return;
        }
        if (!uri.equals(elementName.uri) || !localName.equals(elementName.localName)) {
            throw new SAXException("Inconsistent element: {" + uri + ", " + localName + "}"
                + " Expected " + elementName);
        }

        String characterString = mContent.toString().trim();
        //reset the accumulated characters
        mContent.setLength(0);

        switch (localName) {
            case TAG_NAME:
                handleEndName(uri, characterString);
                break;
            case TAG_ID:
                handleEndId(uri, characterString);
                break;
            case TAG_TYPE:
                handleEndType(uri, characterString);
                break;
            case TAG_BOOK:
            case TAG_ROOT:
            case AccountsTemplate.TAG_ROOT:
                handleEndBook(localName);
                break;
            case TAG_SPACE:
                handleEndSpace(uri, characterString);
                break;
            case TAG_FRACTION:
                handleEndFraction(characterString);
                break;
            case TAG_QUOTE_SOURCE:
                handleEndQuoteSource(characterString);
                break;
            case TAG_QUOTE_TZ:
                handleEndQuoteTz(characterString);
                break;
            case TAG_XCODE:
                handleEndXcode(characterString);
                break;
            case TAG_DESCRIPTION:
                handleEndDescription(uri, characterString);
                break;
            case TAG_COMMODITY:
                handleEndCommodity(uri);
                break;
            case TAG_CURRENCY:
                handleEndCurrency(uri);
                break;
            case TAG_PARENT:
                handleEndParent(uri, characterString);
                break;
            case TAG_ACCOUNT:
                handleEndAccount(uri, characterString);
                break;
            case TAG_SLOT:
                handleEndSlot();
                break;
            case TAG_KEY:
                handleEndKey(uri, characterString);
                break;
            case TAG_VALUE:
                handleEndValue(uri, characterString);
                break;
            case TAG_SLOTS:
                handleEndSlots(uri);
                break;
            case TAG_DATE:
                handleEndDate(uri, characterString);
                break;
            case TAG_RECURRENCE_PERIOD:
                handleEndPeriod(uri, characterString);
                break;
            case TAG_MEMO:
                handleEndMemo(uri, characterString);
                break;
            case TAG_QUANTITY:
                handleEndQuantity(uri, characterString);
                break;
            case TAG_SPLIT:
                handleEndSplit(uri);
                break;
            case TAG_TRANSACTION:
                handleEndTransaction();
                break;
            case TAG_TEMPLATE_TRANSACTIONS:
                handleEndTemplateTransactions();
                break;
            case TAG_ENABLED:
                handleEndEnabled(uri, characterString);
                break;
            case TAG_AUTO_CREATE:
                handleEndAutoCreate(uri, characterString);
                break;
            case TAG_AUTO_CREATE_NOTIFY:
                handleEndAutoCreateNotify(uri, characterString);
                break;
            case TAG_ADVANCE_CREATE_DAYS:
                handleEndAdvanceCreateDays(uri, characterString);
                break;
            case TAG_ADVANCE_REMIND_DAYS:
                handleEndAdvanceRemindDays(uri, characterString);
                break;
            case TAG_INSTANCE_COUNT:
                handleEndInstanceCount(uri, characterString);
                break;
            case TAG_NUM_OCCUR:
                handleEndNumberOccurrence(uri, characterString);
                break;
            case TAG_REM_OCCUR:
                handleEndRemainingOccurrence(uri, characterString);
                break;
            case TAG_MULT:
                handleEndMultiplier(uri, characterString);
                break;
            case TAG_PERIOD_TYPE:
                handleEndPeriodType(uri, characterString);
                break;
            case TAG_WEEKEND_ADJ:
                handleEndWeekendAdjust(uri, characterString);
                break;
            case TAG_GDATE:
                handleEndGDate(characterString);
                break;
            case TAG_TEMPLATE_ACCOUNT:
                handleEndTemplateAccount(uri, characterString);
                break;
            case TAG_RECURRENCE:
                handleEndRecurrence(uri);
                break;
            case TAG_SCHEDULED_ACTION:
                handleEndScheduledAction();
                break;
            case TAG_SOURCE:
                handleEndSource(uri, characterString);
                break;
            case TAG_PRICE:
                handleEndPrice();
                break;
            case TAG_BUDGET:
                handleEndBudget();
                break;
            case TAG_NUM_PERIODS:
                handleEndNumPeriods(uri, characterString);
                break;
            case TAG_COUNT_DATA:
                handleEndCountData(characterString);
                break;
            case TAG_TITLE:
                handleEndTitle(uri, characterString);
                break;
        }
    }

    @Override
    public void characters(char[] chars, int start, int length) throws SAXException {
        mContent.append(chars, start, length);
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();

        Map<String, Account> imbalanceAccounts = new HashMap<>();
        String imbalancePrefix = AccountsDbAdapter.getImbalanceAccountPrefix(context);
        final String rootUID = mRootAccount.getUID();
        for (Account account : mAccountList) {
            if ((account.getParentUID() == null && !account.isRoot())
                || rootUID.equals(account.getParentUID())) {
                if (account.getName().startsWith(imbalancePrefix)) {
                    imbalanceAccounts.put(account.getName().substring(imbalancePrefix.length()), account);
                }
            }
        }

        // Set the account for created balancing splits to correct imbalance accounts
        for (Split split : mAutoBalanceSplits) {
            // XXX: yes, getAccountUID() returns a currency UID in this case (see Transaction.createAutoBalanceSplit())
            String currencyUID = split.getAccountUID();
            if (currencyUID == null) continue;
            Account imbAccount = imbalanceAccounts.get(currencyUID);
            if (imbAccount == null) {
                Commodity commodity = mCommoditiesDbAdapter.getRecord(currencyUID);
                imbAccount = new Account(imbalancePrefix + commodity.getCurrencyCode(), commodity);
                imbAccount.setParentUID(mRootAccount.getUID());
                imbAccount.setAccountType(AccountType.BANK);
                imbalanceAccounts.put(currencyUID, imbAccount);
                mAccountsDbAdapter.addRecord(imbAccount, DatabaseAdapter.UpdateMethod.insert);
                if (listener != null) listener.onAccount(imbAccount);
            }
            split.setAccountUID(imbAccount.getUID());
        }

        String mostAppearedCurrency = "";
        int mostCurrencyAppearance = 0;
        for (Map.Entry<String, Integer> entry : mCurrencyCount.entrySet()) {
            if (entry.getValue() > mostCurrencyAppearance) {
                mostCurrencyAppearance = entry.getValue();
                mostAppearedCurrency = entry.getKey();
            }
        }
        if (mostCurrencyAppearance > 0) {
            mCommoditiesDbAdapter.setDefaultCurrencyCode(mostAppearedCurrency);
        }

        saveToDatabase();

        // generate missed scheduled transactions.
        //FIXME ScheduledActionService.schedulePeriodic(context);
    }

    /**
     * Saves the imported data to the database.
     * We on purpose do not set the book active. Only import. Caller should handle activation
     */
    private void saveToDatabase() {
        mAccountsDbAdapter.enableForeignKey(true);
        maybeClose(); //close it after import
    }

    @Override
    public void close() {
        holder.close();
    }

    private void maybeClose() {
        String activeBookUID = null;
        try {
            activeBookUID = GnuCashApplication.getActiveBookUID();
        } catch (BooksDbAdapter.NoActiveBookFoundException ignore) {
        }
        String newBookUID = mBook.getUID();
        if (activeBookUID == null || !activeBookUID.equals(newBookUID)) {
            close();
        }
    }

    public void cancel() {
        cancellationSignal.cancel();
    }

    /**
     * Returns the unique identifier of the just-imported book
     *
     * @return GUID of the newly imported book
     */
    public @NonNull String getImportedBookUID() {
        return getImportedBook().getUID();
    }

    /**
     * Returns the just-imported book
     *
     * @return the newly imported book
     */
    public @NonNull Book getImportedBook() {
        return mBook;
    }

    /**
     * Returns the currency for an account which has been parsed (but not yet saved to the db)
     * <p>This is used when parsing splits to assign the right currencies to the splits</p>
     *
     * @param accountUID GUID of the account
     * @return Commodity of the account
     */
    private Commodity getCommodityForAccount(String accountUID) {
        try {
            return mAccountMap.get(accountUID).getCommodity();
        } catch (Exception e) {
            Timber.e(e);
            return Commodity.DEFAULT_COMMODITY;
        }
    }

    /**
     * Sets the by days of the scheduled action to the day of the week of the start time.
     *
     * <p>Until we implement parsing of days of the week for scheduled actions,
     * this ensures they are executed at least once per week.</p>
     */
    private void setMinimalScheduledActionByDays() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(mScheduledAction.getStartTime());
        mScheduledAction.getRecurrence().setByDays(
            Collections.singletonList(calendar.get(Calendar.DAY_OF_WEEK)));
    }

    @Nullable
    private Commodity getCommodity(@Nullable final Commodity commodity) {
        if (commodity == null) return null;
        String namespace = commodity.getNamespace();
        if (TextUtils.isEmpty(namespace)) return null;
        final String code = commodity.getMnemonic();
        if (TextUtils.isEmpty(code)) return null;

        if (Commodity.COMMODITY_ISO4217.equals(namespace)) {
            namespace = Commodity.COMMODITY_CURRENCY;
        }
        String key = namespace + "::" + code;
        return mCommodities.get(key);
    }

    private void handleEndAccount(String uri, String value) throws SAXException {
        if (NS_GNUCASH.equals(uri)) {
            if (mInTemplates) {
                // check ROOT account
                if (mAccount.isRoot()) {
                    if (rootTemplateAccount == null) {
                        rootTemplateAccount = mAccount;
                    } else {
                        throw new SAXException("Multiple ROOT Template accounts exist in book");
                    }
                } else if (rootTemplateAccount == null) {
                    rootTemplateAccount = mAccount = new Account(TEMPLATE_ACCOUNT_NAME, Commodity.template);
                    rootTemplateAccount.setAccountType(AccountType.ROOT);
                }
            } else {
                // check ROOT account
                if (mAccount.isRoot()) {
                    if (mRootAccount == null) {
                        mRootAccount = mAccount;
                        mBook.setRootAccountUID(mRootAccount.getUID());
                    } else {
                        throw new SAXException("Multiple ROOT accounts exist in book");
                    }
                } else if (mRootAccount == null) {
                    mRootAccount = mAccount = new Account(ROOT_ACCOUNT_NAME);
                    mRootAccount.setAccountType(AccountType.ROOT);
                    mBook.setRootAccountUID(mRootAccount.getUID());
                }
                mAccountList.add(mAccount);
                if (listener != null) listener.onAccount(mAccount);
            }
            mAccountsDbAdapter.addRecord(mAccount, DatabaseAdapter.UpdateMethod.insert);
            mAccountMap.put(mAccount.getUID(), mAccount);
            // prepare for next input
            mAccount = null;
        } else if (NS_SPLIT.equals(uri)) {
            String accountUID = value;
            mSplit.setAccountUID(accountUID);
            if (mInTemplates) {
                mTemplateAccountToTransactionMap.put(accountUID, mTransaction.getUID());
            } else {
                //the split amount uses the account currency
                mSplit.setQuantity(mSplit.getQuantity().withCommodity(getCommodityForAccount(accountUID)));
                //the split value uses the transaction currency
                mSplit.setValue(mSplit.getValue().withCommodity(mTransaction.getCommodity()));
            }
        }
    }

    private void handleEndAdvanceCreateDays(String uri, String value) {
        if (NS_SX.equals(uri)) {
            mScheduledAction.setAdvanceCreateDays(Integer.parseInt(value));
        }
    }

    private void handleEndAdvanceRemindDays(String uri, String value) {
        if (NS_SX.equals(uri)) {
            mScheduledAction.setAdvanceNotifyDays(Integer.parseInt(value));
        }
    }

    private void handleEndAutoCreate(String uri, String value) {
        if (NS_SX.equals(uri)) {
            mScheduledAction.setAutoCreate(value.equals("y"));
        }
    }

    private void handleEndAutoCreateNotify(String uri, String value) {
        if (NS_SX.equals(uri)) {
            mScheduledAction.setAutoNotify(value.equals("y"));
        }
    }

    private void handleEndBook(String localName) {
        if (hasBookElement) {
            if (TAG_BOOK.equals(localName)) {
                booksDbAdapter.addRecord(mBook, DatabaseAdapter.UpdateMethod.replace);
                if (listener != null) listener.onBook(mBook);
            }
        } else {
            booksDbAdapter.addRecord(mBook, DatabaseAdapter.UpdateMethod.replace);
            if (listener != null) listener.onBook(mBook);
        }
    }

    private void handleEndBudget() {
        if (mBudget != null && !mBudget.getBudgetAmounts().isEmpty()) { //ignore if no budget amounts exist for the budget
            //// TODO: 01.06.2016 Re-enable import of Budget stuff when the UI is complete
            mBudgetsDbAdapter.addRecord(mBudget, DatabaseAdapter.UpdateMethod.insert);
            if (listener != null) listener.onBudget(mBudget);
        }
        mBudget = null;
    }

    private void handleEndCommodity(String uri) throws SAXException {
        if (NS_ACCOUNT.equals(uri)) {
            if (mAccount != null) {
                Commodity commodity = getCommodity(mCommodity);
                if (commodity == null) {
                    throw new SAXException("Commodity with '" + mCommodity + "' not found in the database for account");
                }
                mAccount.setCommodity(commodity);
                if (commodity.isCurrency()) {
                    String currencyId = commodity.getCurrencyCode();
                    Integer currencyCount = mCurrencyCount.get(currencyId);
                    if (currencyCount == null) currencyCount = 0;
                    mCurrencyCount.put(currencyId, currencyCount + 1);
                }
            }
        } else if (NS_GNUCASH.equals(uri)) {
            Commodity commodity = getCommodity(mCommodity);
            if (commodity == null) {
                commodity = mCommodity;
                mCommoditiesDbAdapter.addRecord(commodity, DatabaseAdapter.UpdateMethod.insert);
                mCommodities.put(commodity.getKey(), commodity);
            }
            if (listener != null) listener.onCommodity(commodity);
        } else if (NS_PRICE.equals(uri)) {
            if (mPrice != null) {
                Commodity commodity = getCommodity(mCommodity);
                if (commodity == null) {
                    throw new SAXException("Commodity with '" + mCommodity + "' not found in the database for price");
                }
                mPrice.setCommodity(commodity);
            }
        }
        mCommodity = null;
    }

    private void handleEndCountData(String value) {
        if (!TextUtils.isEmpty(countDataType) && !TextUtils.isEmpty(value)) {
            long count = Long.parseLong(value);
            switch (countDataType) {
                case CD_TYPE_ACCOUNT:
                    if (listener != null) listener.onAccountCount(count);
                    break;
                case CD_TYPE_BOOK:
                    if (listener != null) listener.onBookCount(count);
                    break;
                case CD_TYPE_BUDGET:
                    if (listener != null) listener.onBudgetCount(count);
                    break;
                case CD_TYPE_COMMODITY:
                    if (listener != null) listener.onCommodityCount(count);
                    break;
                case CD_TYPE_PRICE:
                    if (listener != null) listener.onPriceCount(count);
                    break;
                case CD_TYPE_SCHEDXACTION:
                    if (listener != null) listener.onScheduleCount(count);
                    break;
                case CD_TYPE_TRANSACTION:
                    if (listener != null) listener.onTransactionCount(count);
                    break;
            }
        }
        countDataType = null;
    }

    private void handleEndCurrency(String uri) throws SAXException {
        Commodity commodity = getCommodity(mCommodity);
        if (NS_PRICE.equals(uri)) {
            if (commodity == null) {
                throw new SAXException("Currency with '" + mCommodity + "' not found in the database for price");
            }
            if (mPrice != null) {
                mPrice.setCurrency(commodity);
            }
        } else if (NS_TRANSACTION.equals(uri)) {
            if (commodity == null) {
                throw new SAXException("Currency with '" + mCommodity + "' not found in the database for transaction");
            }
            if (mTransaction != null) {
                mTransaction.setCommodity(commodity);
            }
        }
        mCommodity = null;
    }

    private void handleEndDate(String uri, String dateString) throws SAXException {
        if (NS_TS.equals(uri)) {
            try {
                long date = parseDateTime(dateString);

                ElementName elementParent = elementNames.peek();
                final String uriParent = elementParent.uri;
                final String tagParent = elementParent.localName;

                if (NS_TRANSACTION.equals(uriParent)) {
                    switch (tagParent) {
                        case TAG_DATE_ENTERED:
                            mTransaction.setCreatedTimestamp(new Timestamp(date));
                            break;
                        case TAG_DATE_POSTED:
                            mTransaction.setTime(date);
                            break;
                    }
                } else if (NS_PRICE.equals(uriParent)) {
                    if (TAG_TIME.equals(tagParent)) {
                        mPrice.setDate(date);
                    }
                } else if (NS_SPLIT.equals(uriParent)) {
                    if (TAG_RECONCILED_DATE.equals(tagParent)) {
                        mSplit.setReconcileDate(date);
                    }
                }
            } catch (ParseException e) {
                String message = "Unable to parse transaction date " + dateString;
                throw new SAXException(message, e);
            }
        }
    }

    private void handleEndDescription(String uri, String description) {
        if (NS_ACCOUNT.equals(uri)) {
            mAccount.setDescription(description);
        } else if (NS_BUDGET.equals(uri)) {
            mBudget.setDescription(description);
        } else if (NS_TRANSACTION.equals(uri)) {
            mTransaction.setDescription(description);
        }
    }

    private void handleEndEnabled(String uri, String value) {
        if (NS_SX.equals(uri)) {
            mScheduledAction.setEnabled(value.equals("y"));
        }
    }

    private void handleEndFraction(String fraction) {
        if (mCommodity != null) {
            mCommodity.setSmallestFraction(Integer.parseInt(fraction));
        }
    }

    private void handleEndGDate(String dateString) throws SAXException {
        try {
            long date = parseDate(dateString);

            ElementName elementParent = elementNames.peek();
            final String uriParent = elementParent.uri;
            final String tagParent = elementParent.localName;

            if (NS_SLOT.equals(uriParent)) {
                Slot slot = slots.peek();
                if (slot.type.equals(Slot.TYPE_GDATE)) {
                    slot.value = date;
                }
            } else if (NS_RECURRENCE.equals(uriParent)) {
                if (TAG_START.equals(tagParent)) {
                    mRecurrence.setPeriodStart(date);
                } else if (TAG_END.equals(tagParent)) {
                    mRecurrence.setPeriodEnd(date);
                }
            } else if (NS_SX.equals(uriParent)) {
                if (TAG_START.equals(tagParent)) {
                    mScheduledAction.setStartTime(date);
                } else if (TAG_END.equals(tagParent)) {
                    mScheduledAction.setEndTime(date);
                } else if (TAG_LAST.equals(tagParent)) {
                    mScheduledAction.setLastRunTime(date);
                }
            }
        } catch (ParseException e) {
            String msg = "Invalid scheduled action date " + dateString;
            throw new SAXException(msg, e);
        }
    }

    private void handleEndId(String uri, String id) {
        if (NS_ACCOUNT.equals(uri)) {
            mAccount.setUID(id);
        } else if (NS_BOOK.equals(uri)) {
            maybeInitDb(mBook.getUID(), id);
            mBook.setUID(id);
        } else if (NS_BUDGET.equals(uri)) {
            mBudget.setUID(id);
        } else if (NS_COMMODITY.equals(uri)) {
            if (mCommodity != null) {
                mCommodity.setMnemonic(id);
            }
        } else if (NS_PRICE.equals(uri)) {
            mPrice.setUID(id);
        } else if (NS_SPLIT.equals(uri)) {
            mSplit.setUID(id);
        } else if (NS_SX.equals(uri)) {
            // The template account name.
            mScheduledAction.setUID(id);
        } else if (NS_TRANSACTION.equals(uri)) {
            mTransaction.setUID(id);
        }
    }

    private void handleEndInstanceCount(String uri, String value) {
        if (NS_SX.equals(uri)) {
            mScheduledAction.setExecutionCount(Integer.parseInt(value));
        }
    }

    private void handleEndKey(String uri, String key) {
        if (NS_SLOT.equals(uri)) {
            Slot slot = slots.peek();
            slot.key = key;

            if (mBudget != null && !KEY_NOTES.equals(key)) {
                if (budgetAccount == null) {
                    String accountUID = key;
                    Account account = mAccountMap.get(accountUID);
                    if (account != null) {
                        budgetAccount = account;
                    }
                } else {
                    try {
                        budgetPeriod = Long.parseLong(key);
                    } catch (NumberFormatException e) {
                        Timber.e(e, "Invalid budget period: %s", key);
                    }
                }
            }
        }
    }

    private void handleEndMemo(String uri, String memo) {
        if (NS_SPLIT.equals(uri)) {
            if (mSplit != null) {
                mSplit.setMemo(memo);
            }
        }
    }

    private void handleEndMultiplier(String uri, String multiplier) {
        if (NS_RECURRENCE.equals(uri)) {
            mRecurrence.setMultiplier(Integer.parseInt(multiplier));
        }
    }

    private void handleEndName(String uri, String name) {
        if (NS_ACCOUNT.equals(uri)) {
            mAccount.setName(name);
            mAccount.setFullName(name);
        } else if (NS_BUDGET.equals(uri)) {
            mBudget.setName(name);
        } else if (NS_COMMODITY.equals(uri)) {
            mCommodity.setFullname(name);
        } else if (NS_SX.equals(uri)) {
            if (name.equals(ScheduledAction.ActionType.BACKUP.name())) {
                mScheduledAction.setActionType(ScheduledAction.ActionType.BACKUP);
            } else {
                mScheduledAction.setActionType(ScheduledAction.ActionType.TRANSACTION);
            }
        }
    }

    private void handleEndNumberOccurrence(String uri, String value) {
        if (NS_SX.equals(uri)) {
            mScheduledAction.setTotalPlannedExecutionCount(Integer.parseInt(value));
        }
    }

    private void handleEndNumPeriods(String uri, String periods) {
        if (NS_BUDGET.equals(uri)) {
            mBudget.setNumberOfPeriods(Long.parseLong(periods));
        }
    }

    private void handleEndParent(String uri, String parent) {
        if (NS_ACCOUNT.equals(uri)) {
            mAccount.setParentUID(parent);
        }
    }

    private void handleEndPeriod(String uri, String period) {
        if (NS_TRANSACTION.equals(uri)) {
            //for parsing of old backup files
            mRecurrencePeriod = Long.parseLong(period);
            mTransaction.setTemplate(mRecurrencePeriod > 0);
        }
    }

    private void handleEndPeriodType(String uri, String type) {
        if (NS_RECURRENCE.equals(uri)) {
            PeriodType periodType = PeriodType.of(type);
            if (periodType != PeriodType.ONCE) {
                mRecurrence.setPeriodType(periodType);
            } else {
                Timber.e("Invalid period: %s", type);
                mIgnoreScheduledAction = true;
            }
        }
    }

    private void handleEndPrice() {
        if (mPrice != null) {
            mPricesDbAdapter.addRecord(mPrice, DatabaseAdapter.UpdateMethod.insert);
            if (listener != null) listener.onPrice(mPrice);
            mPrice = null;
        }
    }

    private void handleEndQuantity(String uri, String value) throws SAXException {
        if (NS_SPLIT.equals(uri)) {
            // delay the assignment of currency when the split account is seen
            try {
                BigDecimal amount = parseSplitAmount(value).abs();
                mSplit.setQuantity(new Money(amount, Commodity.DEFAULT_COMMODITY));
            } catch (ParseException e) {
                String msg = "Invalid split quantity " + value;
                throw new SAXException(msg, e);
            }
        }
    }

    private void handleEndQuoteSource(String source) {
        if (mCommodity != null) {
            mCommodity.setQuoteSource(source);
        }
    }

    private void handleEndQuoteTz(String tzId) {
        if (!TextUtils.isEmpty(tzId)) {
            TimeZone tz = TimeZone.getTimeZone(tzId);
            if (mCommodity != null) {
                mCommodity.setQuoteTimeZone(tz);
            }
        }
    }

    private void handleEndRecurrence(String uri) {
        if (NS_BUDGET.equals(uri)) {
            mBudget.setRecurrence(mRecurrence);
        } else if (NS_GNUCASH.equals(uri)) {
            if (mScheduledAction != null) {
                mScheduledAction.setRecurrence(mRecurrence);
            }
        }
    }

    private void handleEndRemainingOccurrence(String uri, String value) {
        if (NS_SX.equals(uri)) {
            mScheduledAction.setTotalPlannedExecutionCount(Integer.parseInt(value));
        }
    }

    private void handleEndScheduledAction() {
        if (mScheduledAction.getActionUID() != null && !mIgnoreScheduledAction) {
            if (mScheduledAction.getRecurrence().getPeriodType() == PeriodType.WEEK) {
                // TODO: implement parsing of by days for scheduled actions
                setMinimalScheduledActionByDays();
            }
            mScheduledActionsDbAdapter.addRecord(mScheduledAction, DatabaseAdapter.UpdateMethod.insert);
            if (listener != null) listener.onSchedule(mScheduledAction);
            if (mScheduledAction.getActionType() == ScheduledAction.ActionType.TRANSACTION) {
                String transactionUID = mScheduledAction.getActionUID();
                ContentValues txValues = new ContentValues();
                txValues.put(TransactionEntry.COLUMN_SCHEDX_ACTION_UID, mScheduledAction.getUID());
                mTransactionsDbAdapter.updateRecord(transactionUID, txValues);
            }
            mScheduledAction = null;
        }
        mIgnoreScheduledAction = false;
    }

    private void handleEndSlot() {
        handleEndSlot(slots.pop());
    }

    private void handleEndSlot(@NonNull Slot slot) {
        switch (slot.key) {
            case KEY_PLACEHOLDER:
                if (mAccount != null) {
                    mAccount.setPlaceholder(Boolean.parseBoolean(slot.getAsString()));
                }
                break;
            case KEY_COLOR:
                String color = slot.getAsString();
                //GnuCash exports the account color in format #rrrgggbbb, but we need only #rrggbb.
                //so we trim the last digit in each block, doesn't affect the color much
                if (mAccount != null) {
                    try {
                        mAccount.setColor(color);
                    } catch (IllegalArgumentException e) {
                        //sometimes the color entry in the account file is "Not set" instead of just blank. So catch!
                        Timber.e(e, "Invalid color code \"" + color + "\" for account " + mAccount);
                    }
                }
                break;
            case KEY_FAVORITE:
                if (mAccount != null) {
                    mAccount.setFavorite(Boolean.parseBoolean(slot.getAsString()));
                }
                break;
            case KEY_HIDDEN:
                if (mAccount != null) {
                    mAccount.setHidden(Boolean.parseBoolean(slot.getAsString()));
                }
                break;
            case KEY_DEFAULT_TRANSFER_ACCOUNT:
                if (mAccount != null) {
                    mAccount.setDefaultTransferAccountUID(slot.getAsString());
                }
                break;
            case KEY_EXPORTED:
                if (mTransaction != null) {
                    mTransaction.setExported(Boolean.parseBoolean(slot.getAsString()));
                }
                break;
            case KEY_SCHED_XACTION:
                if (mSplit != null) {
                    for (Slot s : slot.getAsFrame()) {
                        switch (s.key) {
                            case KEY_SPLIT_ACCOUNT_SLOT:
                                mSplit.setScheduledActionAccountUID(s.getAsGUID());
                                break;
                            case KEY_CREDIT_FORMULA:
                                handleEndSlotTemplateFormula(mSplit, s.getAsString(), TransactionType.CREDIT);
                                break;
                            case KEY_CREDIT_NUMERIC:
                                handleEndSlotTemplateNumeric(mSplit, s.getAsNumeric(), TransactionType.CREDIT);
                                break;
                            case KEY_DEBIT_FORMULA:
                                handleEndSlotTemplateFormula(mSplit, s.getAsString(), TransactionType.DEBIT);
                                break;
                            case KEY_DEBIT_NUMERIC:
                                handleEndSlotTemplateNumeric(mSplit, s.getAsNumeric(), TransactionType.DEBIT);
                                break;
                        }
                    }
                }
                break;
            default:
                if (!slots.isEmpty()) {
                    Slot head = slots.peek();
                    if (head.type.equals(Slot.TYPE_FRAME)) {
                        head.add(slot);
                    }
                }
                break;
        }
    }

    private void handleEndSlots(String uri) {
        slots.clear();
    }

    /**
     * Handles the case when we reach the end of the template formula slot
     *
     * @param value Parsed characters containing split amount
     */
    private void handleEndSlotTemplateFormula(@NonNull Split split, @Nullable String value, @NonNull TransactionType splitType) {
        if (TextUtils.isEmpty(value)) return;
        try {
            // HACK: Check for bug #562. If a value has already been set, ignore the one just read
            if (split.getValue().isAmountZero()) {
                String accountUID = split.getScheduledActionAccountUID();
                if (TextUtils.isEmpty(accountUID)) {
                    accountUID = split.getAccountUID();
                }
                Commodity commodity = getCommodityForAccount(accountUID);
                Money amount = new Money(value, commodity);

                split.setValue(amount);
                split.setType(splitType);
                mIgnoreTemplateTransaction = false; //we have successfully parsed an amount
            }
        } catch (NumberFormatException e) {
            Timber.e(e, "Error parsing template split formula [%s]", value);
        }
    }

    /**
     * Handles the case when we reach the end of the template numeric slot
     *
     * @param value Parsed characters containing split amount
     */
    private void handleEndSlotTemplateNumeric(@NonNull Split split, @Nullable String value, @NonNull TransactionType splitType) {
        if (TextUtils.isEmpty(value)) return;
        try {
            // HACK: Check for bug #562. If a value has already been set, ignore the one just read
            if (split.getValue().isAmountZero()) {
                BigDecimal splitAmount = parseSplitAmount(value);
                String accountUID = split.getScheduledActionAccountUID();
                if (TextUtils.isEmpty(accountUID)) {
                    accountUID = split.getAccountUID();
                }
                Commodity commodity = getCommodityForAccount(accountUID);
                Money amount = new Money(splitAmount, commodity);

                split.setValue(amount);
                split.setType(splitType);
                mIgnoreTemplateTransaction = false; //we have successfully parsed an amount
            }
        } catch (NumberFormatException | ParseException e) {
            Timber.e(e, "Error parsing template split numeric [%s]", value);
        }
    }

    private void handleEndSource(String uri, String source) {
        if (NS_PRICE.equals(uri)) {
            if (mPrice != null) {
                mPrice.setSource(source);
            }
        }
    }

    private void handleEndSpace(String uri, String space) {
        if (NS_COMMODITY.equals(uri)) {
            if (mCommodity != null) {
                mCommodity.setNamespace(space);
            }
        }
    }

    private void handleEndSplit(String uri) {
        //todo: import split reconciled state and date
        if (NS_TRANSACTION.equals(uri)) {
            mTransaction.addSplit(mSplit);
        }
    }

    private void handleEndTemplateAccount(String uri, String uid) {
        if (NS_SX.equals(uri)) {
            if (mScheduledAction.getActionType() == ScheduledAction.ActionType.TRANSACTION) {
                mScheduledAction.setTemplateAccountUID(uid);
                String transactionUID = mTemplateAccountToTransactionMap.get(uid);
                mScheduledAction.setActionUID(transactionUID);
            } else {
                mScheduledAction.setActionUID(mBook.getUID());
            }
        }
    }

    private void handleEndTemplateTransactions() {
        mInTemplates = false;
    }

    private void handleEndTitle(String uri, String title) {
        if (NS_GNUCASH_ACCOUNT.equals(uri)) {
            mBook.setDisplayName(title);
        }
    }

    private void handleEndTransaction() {
        mTransaction.setTemplate(mInTemplates);
        Split imbSplit = mTransaction.createAutoBalanceSplit();
        if (imbSplit != null) {
            mAutoBalanceSplits.add(imbSplit);
        }
        if (mInTemplates) {
            if (!mIgnoreTemplateTransaction) {
                mTransactionsDbAdapter.addRecord(mTransaction, DatabaseAdapter.UpdateMethod.insert);
            }
        } else {
            mTransactionsDbAdapter.addRecord(mTransaction, DatabaseAdapter.UpdateMethod.insert);
            if (listener != null) listener.onTransaction(mTransaction);
        }
        if (mRecurrencePeriod > 0) { //if we find an old format recurrence period, parse it
            mTransaction.setTemplate(true);
            ScheduledAction scheduledAction = ScheduledAction.parseScheduledAction(mTransaction, mRecurrencePeriod);
            mScheduledActionsDbAdapter.addRecord(scheduledAction, DatabaseAdapter.UpdateMethod.insert);
            if (listener != null) listener.onSchedule(scheduledAction);
        }
        mRecurrencePeriod = 0;
        mIgnoreTemplateTransaction = true;
        mTransaction = null;
    }

    private void handleEndType(String uri, String type) {
        if (NS_ACCOUNT.equals(uri)) {
            AccountType accountType = AccountType.valueOf(type);
            mAccount.setAccountType(accountType);
        } else if (NS_PRICE.equals(uri)) {
            if (mPrice != null) {
                mPrice.setType(Price.Type.of(type));
            }
        }
    }

    private void handleEndValue(String uri, String value) throws SAXException {
        if (NS_PRICE.equals(uri)) {
            if (mPrice != null) {
                String[] parts = value.split("/");
                if (parts.length != 2) {
                    throw new SAXException("Invalid price " + value);
                } else {
                    mPrice.setValueNum(Long.parseLong(parts[0]));
                    mPrice.setValueDenom(Long.parseLong(parts[1]));
                    Timber.d("price " + value + " .. " + mPrice.getValueNum() + "/" + mPrice.getValueDenom());
                }
            }
        } else if (NS_SLOT.equals(uri)) {
            Slot slot = slots.peek();
            switch (slot.type) {
                case Slot.TYPE_GUID:
                case Slot.TYPE_NUMERIC:
                case Slot.TYPE_STRING:
                    slot.value = value;
                    break;
            }
            if (mBudget != null) {
                boolean isNote = false;
                if (slots.size() >= 3) {
                    Slot parent = slots.get(slots.size() - 2);
                    boolean isParentSlotIsFrame = parent.type.equals(Slot.TYPE_FRAME);
                    Slot grandparent = slots.get(slots.size() - 3);
                    boolean isGrandparentIsNotes = (grandparent.type.equals(Slot.TYPE_FRAME)) && (KEY_NOTES.equals(grandparent.key));
                    isNote = isParentSlotIsFrame && isGrandparentIsNotes;
                }

                switch (slot.type) {
                    case ATTR_VALUE_FRAME:
                        budgetAccount = null;
                        budgetPeriod = null;
                        break;
                    case ATTR_VALUE_NUMERIC:
                        if (!isNote && (budgetAccount != null) && (budgetPeriod != null)) {
                            try {
                                BigDecimal amount = parseSplitAmount(value);
                                mBudget.addAmount(budgetAccount, budgetPeriod, amount);
                            } catch (ParseException e) {
                                Timber.e(e, "Bad budget amount: %s", value);
                            }
                        }
                        budgetPeriod = null;
                        break;
                    case ATTR_VALUE_STRING:
                        if (isNote && (budgetAccount != null) && (budgetPeriod != null)) {
                            BudgetAmount budgetAmount = mBudget.getBudgetAmount(budgetAccount, budgetPeriod);
                            if (budgetAmount == null) {
                                budgetAmount = mBudget.addAmount(budgetAccount, budgetPeriod, BigDecimal.ZERO);
                            }
                            budgetAmount.setNotes(value);
                        }
                        budgetPeriod = null;
                        break;
                }
            } else if (KEY_NOTES.equals(slot.key) && ATTR_VALUE_STRING.equals(slot.type)) {
                if (mTransaction != null) {
                    mTransaction.setNote(value);
                } else if (mAccount != null) {
                    mAccount.setNote(value);
                }
            }
        } else if (NS_SPLIT.equals(uri)) {
            try {
                // The value and quantity can have different sign for custom currency(stock).
                // Use the sign of value for split, as it would not be custom currency
                //this is intentional: GnuCash XML formats split amounts, credits are negative, debits are positive.
                mSplit.setType(value.charAt(0) == '-' ? TransactionType.CREDIT : TransactionType.DEBIT);
                BigDecimal amount = parseSplitAmount(value).abs(); // use sign from quantity
                mSplit.setValue(new Money(amount, Commodity.DEFAULT_COMMODITY));
            } catch (ParseException e) {
                String msg = "Invalid split quantity " + value;
                throw new SAXException(msg, e);
            }
        }
    }

    private void handleEndWeekendAdjust(String uri, String adjust) {
        if (NS_RECURRENCE.equals(uri)) {
            WeekendAdjust weekendAdjust = WeekendAdjust.of(adjust);
            mRecurrence.setWeekendAdjust(weekendAdjust);
        }
    }

    private void handleEndXcode(String xcode) {
        if (mCommodity != null) {
            mCommodity.setCusip(xcode);
        }
    }

    private void handleStartAccount(String uri) {
        if (NS_GNUCASH.equals(uri)) {
            // dummy name, will be replaced when we find name tag
            mAccount = new Account("");
        }
    }

    private void handleStartBook(String uri) {
        if (NS_GNUCASH.equals(uri)) {
            hasBookElement = true;
        }
    }

    private void handleStartBudget(String uri) {
        if (NS_GNUCASH.equals(uri)) {
            mBudget = new Budget();
        }
    }

    private void handleStartCommodity() {
        mCommodity = new Commodity("", "");
    }

    private void handleStartCountData(Attributes attributes) {
        countDataType = attributes.getValue(NS_CD, ATTR_KEY_TYPE);
    }

    private void handleStartCurrency() {
        mCommodity = new Commodity("", "");
    }

    private void handleStartPrice() {
        mPrice = new Price();
    }

    private void handleStartRecurrence(String uri) {
        mRecurrence = new Recurrence(PeriodType.MONTH);
    }

    private void handleStartScheduledAction() {
        //default to transaction type, will be changed during parsing
        mScheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
    }

    private void handleStartSplit(String uri) {
        if (NS_TRANSACTION.equals(uri)) {
            mSplit = new Split(Money.createZeroInstance(mRootAccount.getCommodity()), "");
        }
    }

    private void handleStartTransaction() {
        mTransaction = new Transaction(""); // dummy name will be replaced
        mTransaction.setExported(true);     // default to exported when import transactions
    }

    private void handleStartValue(String uri, Attributes attributes) {
        if (NS_SLOT.equals(uri)) {
            Slot slot = slots.peek();
            slot.type = attributes.getValue(ATTR_KEY_TYPE);
        }
    }

    private static class ElementName {
        public final String uri;
        public final String localName;
        public final String qualifiedName;

        ElementName(String uri, String localName, String qualifiedName) {
            this.uri = uri;
            this.localName = localName;
            this.qualifiedName = qualifiedName;
        }

        @NonNull
        @Override
        public String toString() {
            return "{" + uri + "," + localName + ", " + qualifiedName + "}";
        }
    }
}
