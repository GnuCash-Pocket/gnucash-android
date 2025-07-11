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
import static org.gnucash.android.export.xml.GncXmlHelper.*;

import android.content.ContentValues;
import android.content.Context;
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
import org.gnucash.android.model.PriceType;
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
     * (Absolute) quantity of the split, which uses split account currency
     */
    private BigDecimal mQuantity;

    /**
     * (Absolute) value of the split, which uses transaction currency
     */
    private BigDecimal mValue;

    /**
     * price table entry
     */
    private Price mPrice;

    private boolean mPriceCommodity;
    private boolean mPriceCurrency;

    /**
     * Whether the quantity is negative
     */
    private boolean mNegativeQuantity;

    /**
     * The list for all added split for autobalancing
     */
    private final List<Split> mAutoBalanceSplits = new ArrayList<>();

    /**
     * Ignore certain elements in GnuCash XML file, such as "<gnc:template-transactions>"
     */
    private String mIgnoreElement = null;

    /**
     * {@link ScheduledAction} instance for each scheduled action parsed
     */
    private ScheduledAction mScheduledAction;

    private Budget mBudget;
    private Recurrence mRecurrence;
    private BudgetAmount mBudgetAmount;
    private Commodity mCommodity;
    private final Map<String, Map<String, Commodity>> mCommodities = new HashMap<>();
    private String mCommoditySpace;
    private String mCommodityId;

    private boolean mIsDatePosted = false;
    private boolean mIsDateEntered = false;
    private boolean mIsNote = false;
    private boolean mInTemplates = false;
    private boolean mIsScheduledStart = false;
    private boolean mIsScheduledEnd = false;
    private boolean mIsLastRun = false;
    private boolean mIsRecurrenceStart = false;
    private boolean mInBudgetSlot = false;

    private final Stack<Slot> slots = new Stack<>();
    private String slotKey = null;

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
    private DatabaseHelper mDatabaseHelper;
    @NonNull
    private final Context context;
    @Nullable
    private final GncProgressListener listener;
    @Nullable
    private String countDataType;
    private boolean isValidRoot = false;

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
        super();
        this.context = context;
        this.listener = listener;
        initDb(mBook.getUID());
    }

    private void initDb(@NonNull String bookUID) {
        DatabaseHelper databaseHelper = new DatabaseHelper(context, bookUID);
        mDatabaseHelper = databaseHelper;
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
    }

    private void maybeInitDb(@Nullable String bookUIDOld, @NonNull String bookUIDNew) {
        if (bookUIDOld != null && !bookUIDOld.equals(bookUIDNew)) {
            mDatabaseHelper.close();
            initDb(bookUIDNew);
        }
    }

    @Override
    public void startElement(String uri, String localName,
                             String qualifiedName, Attributes attributes) throws SAXException {
        if (!isValidRoot) {
            if (TAG_ROOT.equals(qualifiedName) || AccountsTemplate.TAG_ROOT.equals(qualifiedName)) {
                isValidRoot = true;
                return;
            }
            throw new SAXException("Expected root element " + TAG_ROOT);
        }

        switch (qualifiedName) {
            case TAG_BOOK:
            case AccountsTemplate.TAG_ROOT:
                break;
            case TAG_ACCOUNT:
                mAccount = new Account(""); // dummy name, will be replaced when we find name tag
                break;
            case TAG_TRANSACTION:
                mTransaction = new Transaction(""); // dummy name will be replaced
                mTransaction.setExported(true);     // default to exported when import transactions
                break;
            case TAG_TRN_SPLIT:
                mSplit = new Split(Money.createZeroInstance(mRootAccount.getCommodity()), "");
                break;
            case TAG_DATE_POSTED:
                mIsDatePosted = true;
                break;
            case TAG_DATE_ENTERED:
                mIsDateEntered = true;
                break;
            case TAG_TEMPLATE_TRANSACTIONS:
                mInTemplates = true;
                break;
            case TAG_SCHEDULED_ACTION:
                //default to transaction type, will be changed during parsing
                mScheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
                break;
            case TAG_SX_START:
                mIsScheduledStart = true;
                break;
            case TAG_SX_END:
                mIsScheduledEnd = true;
                break;
            case TAG_SX_LAST:
                mIsLastRun = true;
                break;
            case TAG_RX_START:
                mIsRecurrenceStart = true;
                break;
            case TAG_PRICE:
                mPrice = new Price();
                break;
            case TAG_PRICE_CURRENCY:
                mPriceCurrency = true;
                mPriceCommodity = false;
                break;
            case TAG_PRICE_COMMODITY:
                mPriceCurrency = false;
                mPriceCommodity = true;
                break;
            case TAG_BUDGET:
                mBudget = new Budget();
                break;
            case TAG_GNC_RECURRENCE:
            case TAG_BUDGET_RECURRENCE:
                mRecurrence = new Recurrence(PeriodType.MONTH);
                break;
            case TAG_BUDGET_SLOTS:
                mInBudgetSlot = true;
                break;
            case TAG_SLOT:
            case TAG_SLOT_KEY:
                break;
            case TAG_SLOT_VALUE:
                if (!TextUtils.isEmpty(slotKey)) {
                    slots.push(new Slot(slotKey, attributes.getValue(ATTR_KEY_TYPE)));
                }
                slotKey = null;
                break;
            case TAG_COMMODITY:
                mCommodity = new Commodity("", "");
                break;
            case TAG_COUNT_DATA:
                countDataType = attributes.getValue(ATTR_KEY_CD_TYPE);
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qualifiedName) throws SAXException {
        // FIXME: 22.10.2015 First parse the number of accounts/transactions and use the number to init the array lists
        String characterString = mContent.toString().trim();

        if (mIgnoreElement != null) {
            // Ignore everything inside
            if (qualifiedName.equals(mIgnoreElement)) {
                mIgnoreElement = null;
            }
            mContent.setLength(0);
            return;
        }
        Slot slot;

        switch (qualifiedName) {
            case TAG_ACCT_NAME:
                mAccount.setName(characterString);
                mAccount.setFullName(characterString);
                break;
            case TAG_ACCT_ID:
                mAccount.setUID(characterString);
                break;
            case TAG_ACCT_TYPE:
                AccountType accountType = AccountType.valueOf(characterString);
                mAccount.setAccountType(accountType);
                break;
            case TAG_BOOK:
            case TAG_ROOT:
            case AccountsTemplate.TAG_ROOT:
                booksDbAdapter.addRecord(mBook, DatabaseAdapter.UpdateMethod.replace);
                if (listener != null) listener.onBook(mBook);
                break;
            case TAG_BOOK_ID:
                maybeInitDb(mBook.getUID(), characterString);
                mBook.setUID(characterString);
                break;
            case TAG_COMMODITY_SPACE:
                mCommoditySpace = characterString;
                if (!characterString.equals(COMMODITY_ISO4217) && !characterString.equals(COMMODITY_CURRENCY)) {
                    // price of non-ISO4217 commodities cannot be handled
                    mPrice = null;
                }
                if (mCommodity != null) {
                    mCommodity.setNamespace(characterString);
                }
                break;
            case TAG_COMMODITY_ID:
                mCommodityId = characterString;
                if (mCommodity != null) {
                    Commodity commodity = getCommodity(mCommoditySpace, mCommodityId);
                    if (commodity != null) {
                        mCommodity = commodity;
                    } else {
                        mCommodity.setMnemonic(characterString);
                    }
                }
                if (mTransaction != null) {
                    Commodity commodity = getCommodity(mCommoditySpace, mCommodityId);
                    mTransaction.setCommodity(commodity);
                }
                if (mPrice != null) {
                    Commodity commodity = getCommodity(mCommoditySpace, mCommodityId);
                    if (commodity == null) break;
                    if (mPriceCommodity) {
                        mPrice.setCommodity(commodity);
                        mPriceCommodity = false;
                    }
                    if (mPriceCurrency) {
                        mPrice.setCurrency(commodity);
                        mPriceCurrency = false;
                    }
                }
                break;
            case TAG_COMMODITY_FRACTION:
                if (mCommodity != null) {
                    mCommodity.setSmallestFraction(Integer.parseInt(characterString));
                }
                break;
            case TAG_COMMODITY_NAME:
                if (mCommodity != null) {
                    mCommodity.setFullname(characterString);
                }
                break;
            case TAG_COMMODITY_QUOTE_SOURCE:
                if (mCommodity != null) {
                    mCommodity.setQuoteSource(characterString);
                }
                break;
            case TAG_COMMODITY_QUOTE_TZ:
                if (mCommodity != null) {
                    if (!TextUtils.isEmpty(characterString)) {
                        TimeZone tz = TimeZone.getTimeZone(characterString);
                        mCommodity.setQuoteTimeZone(tz);
                    }
                }
                break;
            case TAG_COMMODITY_XCODE:
                if (mCommodity != null) {
                    mCommodity.setCusip(characterString);
                }
                break;
            case TAG_ACCT_DESCRIPTION:
                mAccount.setDescription(characterString);
                break;
            case TAG_ACCT_COMMODITY:
                if (mAccount != null) {
                    Commodity commodity = getCommodity(mCommoditySpace, mCommodityId);
                    if (commodity != null) {
                        mAccount.setCommodity(commodity);
                    } else {
                        throw new SAXException("Commodity with '" + mCommoditySpace + ":" + mCommodityId
                            + "' currency code not found in the database for account " + mAccount.getUID());
                    }
                    if (commodity.isCurrency()) {
                        String currencyId = commodity.getCurrencyCode();
                        Integer currencyCount = mCurrencyCount.get(currencyId);
                        if (currencyCount == null) currencyCount = 0;
                        mCurrencyCount.put(currencyId, currencyCount + 1);
                    }
                }
                break;
            case TAG_ACCT_PARENT:
                mAccount.setParentUID(characterString);
                break;
            case TAG_ACCOUNT:
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
                }
                mAccountsDbAdapter.addRecord(mAccount, DatabaseAdapter.UpdateMethod.insert);
                mAccountList.add(mAccount);
                mAccountMap.put(mAccount.getUID(), mAccount);
                if (listener != null) listener.onAccount(mAccount);
                // prepare for next input
                mAccount = null;
                break;
            case TAG_SLOT:
                handleSlot(slots.pop());
                if (mInBudgetSlot) {
                    budgetPeriod = null;
                }
                break;
            case TAG_SLOT_KEY:
                slotKey = characterString;
                switch (characterString) {
                    case KEY_NOTES:
                        mIsNote = true;
                        budgetAccount = null;
                        break;
                    default:
                        if (mInBudgetSlot) {
                            if (budgetAccount == null) {
                                String accountUID = characterString;
                                Account account = mAccountMap.get(accountUID);
                                if (account != null) {
                                    budgetAccount = account;
                                }
                            } else {
                                try {
                                    budgetPeriod = Long.parseLong(characterString);
                                } catch (NumberFormatException e) {
                                    Timber.e(e, "Bad budget period: %s", characterString);
                                }
                            }
                        }
                        break;
                }
                break;
            case TAG_SLOT_VALUE:
                slot = slots.peek();
                switch (slot.type) {
                    case Slot.TYPE_GDATE:
                    case Slot.TYPE_GUID:
                    case Slot.TYPE_NUMERIC:
                    case Slot.TYPE_STRING:
                        slot.value = characterString;
                        break;
                }
                if (mInBudgetSlot) {
                    switch (slot.type) {
                        case ATTR_VALUE_FRAME:
                            budgetAccount = null;
                            budgetPeriod = null;
                            break;
                        case ATTR_VALUE_NUMERIC:
                            if (!mIsNote && (budgetAccount != null) && (budgetPeriod != null)) {
                                try {
                                    BigDecimal amount = parseSplitAmount(characterString);
                                    mBudget.addAmount(budgetAccount, budgetPeriod, amount);
                                } catch (ParseException e) {
                                    Timber.e(e, "Bad budget amount: %s", characterString);
                                }
                            }
                            budgetPeriod = null;
                            break;
                        case ATTR_VALUE_STRING:
                            if (mIsNote && (budgetAccount != null) && (budgetPeriod != null)) {
                                BudgetAmount budgetAmount = mBudget.getBudgetAmount(budgetAccount, budgetPeriod);
                                if (budgetAmount == null) {
                                    budgetAmount = mBudget.addAmount(budgetAccount, budgetPeriod, BigDecimal.ZERO);
                                }
                                budgetAmount.setNotes(characterString);
                            }
                            budgetPeriod = null;
                            break;
                    }
                } else if (mIsNote && ATTR_VALUE_STRING.equals(slot.type)) {
                    if (mTransaction != null) {
                        mTransaction.setNote(characterString);
                    } else if (mAccount != null) {
                        mAccount.setNote(characterString);
                    }
                    mIsNote = false;
                }
                break;

            case TAG_BUDGET_SLOTS:
                mInBudgetSlot = false;
                mIsNote = false;
                slots.clear();
                break;

            //================  PROCESSING OF TRANSACTION TAGS =====================================
            case TAG_TRX_ID:
                mTransaction.setUID(characterString);
                break;
            case TAG_TRN_DESCRIPTION:
                mTransaction.setDescription(characterString);
                break;
            case TAG_TS_DATE:
                try {
                    if (mIsDatePosted && mTransaction != null) {
                        mTransaction.setTime(parseDateTime(characterString));
                        mIsDatePosted = false;
                    }
                    if (mIsDateEntered && mTransaction != null) {
                        Timestamp timestamp = new Timestamp(parseDateTime(characterString));
                        mTransaction.setCreatedTimestamp(timestamp);
                        mIsDateEntered = false;
                    }
                    if (mPrice != null) {
                        mPrice.setDate(new Timestamp(parseDateTime(characterString)));
                    }
                } catch (ParseException e) {
                    String message = "Unable to parse transaction time - " + characterString;
                    throw new SAXException(message, e);
                }
                break;
            case TAG_RECURRENCE_PERIOD: //for parsing of old backup files
                mRecurrencePeriod = Long.parseLong(characterString);
                mTransaction.setTemplate(mRecurrencePeriod > 0);
                break;
            case TAG_SPLIT_ID:
                mSplit.setUID(characterString);
                break;
            case TAG_SPLIT_MEMO:
                mSplit.setMemo(characterString);
                break;
            case TAG_SPLIT_VALUE:
                try {
                    // The value and quantity can have different sign for custom currency(stock).
                    // Use the sign of value for split, as it would not be custom currency
                    mNegativeQuantity = characterString.charAt(0) == '-';
                    mValue = parseSplitAmount(characterString).abs(); // use sign from quantity
                } catch (ParseException e) {
                    String msg = "Error parsing split quantity - " + characterString;
                    throw new SAXException(msg, e);
                }
                break;
            case TAG_SPLIT_QUANTITY:
                // delay the assignment of currency when the split account is seen
                try {
                    mQuantity = parseSplitAmount(characterString).abs();
                } catch (ParseException e) {
                    String msg = "Error parsing split quantity - " + characterString;
                    throw new SAXException(msg, e);
                }
                break;
            case TAG_SPLIT_ACCOUNT:
                String splitAccountUID = characterString;
                mSplit.setAccountUID(splitAccountUID);
                if (mInTemplates) {
                    mTemplateAccountToTransactionMap.put(splitAccountUID, mTransaction.getUID());
                } else {
                    //this is intentional: GnuCash XML formats split amounts, credits are negative, debits are positive.
                    mSplit.setType(mNegativeQuantity ? TransactionType.CREDIT : TransactionType.DEBIT);
                    //the split amount uses the account currency
                    mSplit.setQuantity(new Money(mQuantity, getCommodityForAccount(splitAccountUID)));
                    //the split value uses the transaction currency
                    mSplit.setValue(new Money(mValue, mTransaction.getCommodity()));
                }
                break;
            //todo: import split reconciled state and date
            case TAG_TRN_SPLIT:
                mTransaction.addSplit(mSplit);
                break;
            case TAG_TRANSACTION:
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
                break;
            case TAG_TEMPLATE_TRANSACTIONS:
                mInTemplates = false;
                break;

            // ========================= PROCESSING SCHEDULED ACTIONS ==================================
            case TAG_SX_ID:
                // The template account name.
                mScheduledAction.setUID(characterString);
                break;
            case TAG_SX_NAME:
                if (characterString.equals(ScheduledAction.ActionType.BACKUP.name()))
                    mScheduledAction.setActionType(ScheduledAction.ActionType.BACKUP);
                else
                    mScheduledAction.setActionType(ScheduledAction.ActionType.TRANSACTION);
                break;
            case TAG_SX_ENABLED:
                mScheduledAction.setEnabled(characterString.equals("y"));
                break;
            case TAG_SX_AUTO_CREATE:
                mScheduledAction.setAutoCreate(characterString.equals("y"));
                break;
            case TAG_SX_AUTO_CREATE_NOTIFY:
                mScheduledAction.setAutoNotify(characterString.equals("y"));
                break;
            case TAG_SX_ADVANCE_CREATE_DAYS:
                mScheduledAction.setAdvanceCreateDays(Integer.parseInt(characterString));
                break;
            case TAG_SX_ADVANCE_REMIND_DAYS:
                mScheduledAction.setAdvanceNotifyDays(Integer.parseInt(characterString));
                break;
            case TAG_SX_INSTANCE_COUNT:
                mScheduledAction.setExecutionCount(Integer.parseInt(characterString));
                break;
            //todo: export auto_notify, advance_create, advance_notify
            case TAG_SX_NUM_OCCUR:
                mScheduledAction.setTotalPlannedExecutionCount(Integer.parseInt(characterString));
                break;
            case TAG_SX_REM_OCCUR:
                mScheduledAction.setTotalPlannedExecutionCount(Integer.parseInt(characterString));
                break;
            case TAG_RX_START:
                mIsRecurrenceStart = false;
                break;
            case TAG_RX_MULT:
                mRecurrence.setMultiplier(Integer.parseInt(characterString));
                break;
            case TAG_RX_PERIOD_TYPE:
                PeriodType periodType = PeriodType.of(characterString);
                if (periodType != PeriodType.ONCE) {
                    mRecurrence.setPeriodType(periodType);
                } else {
                    Timber.e("Unsupported period: %s", characterString);
                    mIgnoreScheduledAction = true;
                }
                break;
            case TAG_RX_WEEKEND_ADJ:
                WeekendAdjust weekendAdjust = WeekendAdjust.of(characterString);
                mRecurrence.setWeekendAdjust(weekendAdjust);
                break;
            case TAG_GDATE:
                try {
                    long date = parseDate(characterString);
                    if (mIsScheduledStart && mScheduledAction != null) {
                        mScheduledAction.setCreatedTimestamp(new Timestamp(date));
                        mIsScheduledStart = false;
                    }

                    if (mIsScheduledEnd && mScheduledAction != null) {
                        mScheduledAction.setEndTime(date);
                        mIsScheduledEnd = false;
                    }

                    if (mIsLastRun && mScheduledAction != null) {
                        mScheduledAction.setLastRunTime(date);
                        mIsLastRun = false;
                    }

                    if (mIsRecurrenceStart && mRecurrence != null) {
                        mRecurrence.setPeriodStart(date);
                        mIsRecurrenceStart = false;
                    }
                } catch (ParseException e) {
                    String msg = "Error parsing scheduled action date " + characterString;
                    throw new SAXException(msg, e);
                }
                break;
            case TAG_SX_TEMPL_ACCOUNT:
                if (mScheduledAction.getActionType() == ScheduledAction.ActionType.TRANSACTION) {
                    String accountUID = characterString;
                    mScheduledAction.setTemplateAccountUID(accountUID);
                    String transactionUID = mTemplateAccountToTransactionMap.get(accountUID);
                    mScheduledAction.setActionUID(transactionUID);
                } else {
                    mScheduledAction.setActionUID(mBook.getUID());
                }
                break;
            case TAG_GNC_RECURRENCE:
                if (mScheduledAction != null) {
                    mScheduledAction.setRecurrence(mRecurrence);
                }
                break;

            case TAG_SCHEDULED_ACTION:
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
                break;
            // price table
            case TAG_PRICE_ID:
                mPrice.setUID(characterString);
                break;
            case TAG_PRICE_SOURCE:
                if (mPrice != null) {
                    mPrice.setSource(characterString);
                }
                break;
            case TAG_PRICE_VALUE:
                if (mPrice != null) {
                    String[] parts = characterString.split("/");
                    if (parts.length != 2) {
                        String message = "Illegal price - " + characterString;
                        throw new SAXException(message);
                    } else {
                        mPrice.setValueNum(Long.valueOf(parts[0]));
                        mPrice.setValueDenom(Long.valueOf(parts[1]));
                        Timber.d("price " + characterString +
                            " .. " + mPrice.getValueNum() + "/" + mPrice.getValueDenom());
                    }
                }
                break;
            case TAG_PRICE_TYPE:
                if (mPrice != null) {
                    mPrice.setType(PriceType.of(characterString));
                }
                break;
            case TAG_PRICE:
                if (mPrice != null) {
                    mPricesDbAdapter.addRecord(mPrice, DatabaseAdapter.UpdateMethod.insert);
                    if (listener != null) listener.onPrice(mPrice);
                    mPrice = null;
                }
                break;
            case TAG_BUDGET:
                if (mBudget != null && !mBudget.getBudgetAmounts().isEmpty()) { //ignore if no budget amounts exist for the budget
                    //// TODO: 01.06.2016 Re-enable import of Budget stuff when the UI is complete
                    mBudgetsDbAdapter.addRecord(mBudget, DatabaseAdapter.UpdateMethod.insert);
                    if (listener != null) listener.onBudget(mBudget);
                    mBudget = null;
                }
                break;
            case TAG_BUDGET_ID:
                mBudget.setUID(characterString);
                break;
            case TAG_BUDGET_NAME:
                mBudget.setName(characterString);
                break;
            case TAG_BUDGET_DESCRIPTION:
                mBudget.setDescription(characterString);
                break;
            case TAG_BUDGET_NUM_PERIODS:
                mBudget.setNumberOfPeriods(Long.parseLong(characterString));
                break;
            case TAG_BUDGET_RECURRENCE:
                mBudget.setRecurrence(mRecurrence);
                break;
            case TAG_COMMODITY:
                if (mCommodity != null) {
                    Commodity commodity = putCommodity(mCommodity);
                    if (commodity != null) {
                        Commodity commodityDb = mCommoditiesDbAdapter.getRecordOrNull(commodity.getUID());
                        if (commodityDb == null) {
                            mCommoditiesDbAdapter.addRecord(commodity, DatabaseAdapter.UpdateMethod.insert);
                        }
                    }
                    if (listener != null) listener.onCommodity(mCommodity);
                    mCommodity = null;
                }
                break;
            case TAG_COUNT_DATA:
                if (!TextUtils.isEmpty(countDataType)) {
                    if (!TextUtils.isEmpty(characterString)) {
                        long count = Long.parseLong(characterString);
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
                }
                countDataType = null;
                break;
            case TAG_ACCT_TITLE:
                mBook.setDisplayName(characterString);
                break;
        }

        //reset the accumulated characters
        mContent.setLength(0);
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
        mDatabaseHelper.close();
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
     * Handles the case when we reach the end of the template numeric slot
     *
     * @param value Parsed characters containing split amount
     */
    private void handleEndOfTemplateNumericSlot(@NonNull Split split, String value, TransactionType splitType) {
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
            Timber.e(e, "Error parsing template split amount %s", value);
        }
    }

    /**
     * Handles the case when we reach the end of the template formula slot
     *
     * @param value Parsed characters containing split amount
     */
    private void handleEndOfTemplateFormulaSlot(@NonNull Split split, String value, TransactionType splitType) {
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
            Timber.e(e, "Error parsing template split amount %s", value);
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
    private Commodity getCommodity(@Nullable String space, @Nullable String id) {
        if (TextUtils.isEmpty(space)) return null;
        if (TextUtils.isEmpty(id)) return null;
        Map<String, Commodity> commoditiesById = mCommodities.get(space);
        if (commoditiesById == null) {
            if (Commodity.COMMODITY_CURRENCY.equals(space)) {
                commoditiesById = mCommodities.get(Commodity.COMMODITY_ISO4217);
            } else if (Commodity.COMMODITY_ISO4217.equals(space)) {
                commoditiesById = mCommodities.get(Commodity.COMMODITY_CURRENCY);
            }
        }
        if (commoditiesById != null) {
            Commodity commodity = commoditiesById.get(id);
            if (commodity != null) return commodity;
        }
        return mCommoditiesDbAdapter.getCommodity(id);
    }

    @Nullable
    private Commodity putCommodity(@NonNull Commodity commodity) {
        //if (commodity.isTemplate()) return null;
        String space = commodity.getNamespace();
        if (TextUtils.isEmpty(space)) return null;
        String id = commodity.getMnemonic();
        if (TextUtils.isEmpty(id)) return null;

        // Already a database record?
        if (commodity.id != 0L) return null;

        Map<String, Commodity> commoditiesById = mCommodities.get(space);
        if (commoditiesById == null) {
            commoditiesById = new HashMap<>();
            mCommodities.put(space, commoditiesById);
        }
        commoditiesById.put(id, commodity);
        return commodity;
    }

    private void handleSlot(@NonNull Slot slot) {
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
                                handleEndOfTemplateFormulaSlot(mSplit, s.getAsString(), TransactionType.CREDIT);
                                break;
                            case KEY_CREDIT_NUMERIC:
                                handleEndOfTemplateNumericSlot(mSplit, s.getAsNumeric(), TransactionType.CREDIT);
                                break;
                            case KEY_DEBIT_FORMULA:
                                handleEndOfTemplateFormulaSlot(mSplit, s.getAsString(), TransactionType.DEBIT);
                                break;
                            case KEY_DEBIT_NUMERIC:
                                handleEndOfTemplateNumericSlot(mSplit, s.getAsNumeric(), TransactionType.DEBIT);
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
}
