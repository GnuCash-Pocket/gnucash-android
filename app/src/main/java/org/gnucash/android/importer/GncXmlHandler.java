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

import static org.gnucash.android.export.xml.GncXmlHelper.ATTR_KEY_TYPE;
import static org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_FRAME;
import static org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_NUMERIC;
import static org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_STRING;
import static org.gnucash.android.export.xml.GncXmlHelper.COMMODITY_CURRENCY;
import static org.gnucash.android.export.xml.GncXmlHelper.COMMODITY_ISO4217;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_COLOR;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_CREDIT_NUMERIC;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_DEBIT_NUMERIC;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_DEFAULT_TRANSFER_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_EXPORTED;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_FAVORITE;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_HIDDEN;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_NOTES;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_PLACEHOLDER;
import static org.gnucash.android.export.xml.GncXmlHelper.KEY_SPLIT_ACCOUNT_SLOT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCT_COMMODITY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCT_DESCRIPTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCT_ID;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCT_NAME;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCT_PARENT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCT_TITLE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCT_TYPE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET_DESCRIPTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET_ID;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET_NAME;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET_NUM_PERIODS;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET_RECURRENCE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET_SLOTS;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY_FRACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY_ID;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY_NAME;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY_QUOTE_SOURCE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY_QUOTE_TZ;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY_SPACE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY_XCODE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_DATE_ENTERED;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_DATE_POSTED;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_GDATE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_GNC_RECURRENCE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICE_COMMODITY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICE_CURRENCY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICE_ID;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICE_SOURCE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICE_TYPE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICE_VALUE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_RECURRENCE_PERIOD;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_ROOT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_RX_MULT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_RX_PERIOD_TYPE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_RX_START;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_RX_WEEKEND_ADJ;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SCHEDULED_ACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SLOT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SLOT_KEY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SLOT_VALUE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SPLIT_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SPLIT_ID;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SPLIT_MEMO;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SPLIT_QUANTITY;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SPLIT_VALUE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SX_AUTO_CREATE;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SX_ENABLED;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SX_END;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SX_ID;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SX_LAST;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SX_NAME;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SX_NUM_OCCUR;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SX_START;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_SX_TEMPL_ACCOUNT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TEMPLATE_TRANSACTIONS;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TRANSACTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TRN_DESCRIPTION;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TRN_SPLIT;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TRX_ID;
import static org.gnucash.android.export.xml.GncXmlHelper.TAG_TS_DATE;
import static org.gnucash.android.export.xml.GncXmlHelper.parseDate;
import static org.gnucash.android.export.xml.GncXmlHelper.parseDateTime;
import static org.gnucash.android.export.xml.GncXmlHelper.parseSplitAmount;
import static org.gnucash.android.model.Commodity.TEMPLATE;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Price;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.model.ScheduledAction;
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
    private final AccountsDbAdapter mAccountsDbAdapter;

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
     * List of all the template accounts found
     */
    private final List<Account> mTemplatAccountList = new ArrayList<>();

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

    /**
     * Transaction instance which will be built for each transaction found
     */
    private Transaction mTransaction;

    /**
     * All the transaction instances found in a file to be inserted, used in bulk mode
     */
    private final List<Transaction> mTransactionList = new ArrayList<>();

    /**
     * All the template transactions found during parsing of the XML
     */
    private final List<Transaction> mTemplateTransactions = new ArrayList<>();

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

    private final List<Price> mPriceList = new ArrayList<>();

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

    /**
     * List of scheduled actions to be bulk inserted
     */
    private final List<ScheduledAction> mScheduledActionsList = new ArrayList<>();

    /**
     * List of budgets which have been parsed from XML
     */
    private final List<Budget> mBudgetList = new ArrayList<>();
    private Budget mBudget;
    private Recurrence mRecurrence;
    private Commodity mCommodity;
    private final Map<String, Map<String, Commodity>> mCommodities = new HashMap<>();
    private String mCommoditySpace;
    private String mCommodityId;

    private boolean mInColorSlot = false;
    private boolean mInPlaceHolderSlot = false;
    private boolean mInFavoriteSlot = false;
    private boolean mInHiddenSlot = false;
    private boolean mIsDatePosted = false;
    private boolean mIsDateEntered = false;
    private boolean mIsNote = false;
    private boolean mInDefaultTransferAccount = false;
    private boolean mInExported = false;
    private boolean mInTemplates = false;
    private boolean mInSplitAccountSlot = false;
    private boolean mInCreditNumericSlot = false;
    private boolean mInDebitNumericSlot = false;
    private boolean mIsScheduledStart = false;
    private boolean mIsScheduledEnd = false;
    private boolean mIsLastRun = false;
    private boolean mIsRecurrenceStart = false;
    private boolean mInBudgetSlot = false;

    /**
     * Saves the attribute of the slot tag
     * Used for determining where we are in the budget amounts
     */
    private final Stack<String> slotTypes = new Stack<>();

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
    private final TransactionsDbAdapter mTransactionsDbAdapter;
    @NonNull
    private final ScheduledActionDbAdapter mScheduledActionsDbAdapter;
    @NonNull
    private final CommoditiesDbAdapter mCommoditiesDbAdapter;
    @NonNull
    private final PricesDbAdapter mPricesDbAdapter;
    @NonNull
    private final Map<String, Integer> mCurrencyCount = new HashMap<>();
    @NonNull
    private final BudgetsDbAdapter mBudgetsDbAdapter;
    private final Book mBook = new Book();
    private final SQLiteDatabase mDB;
    @NonNull
    private final DatabaseHelper mDatabaseHelper;
    @NonNull
    private final Context context;
    private boolean isValidRoot = false;

    /**
     * Creates a handler for handling XML stream events when parsing the XML backup file
     */
    public GncXmlHandler() {
        this(GnuCashApplication.getAppContext());
    }

    /**
     * Creates a handler for handling XML stream events when parsing the XML backup file
     */
    public GncXmlHandler(@NonNull Context context) {
        super();
        this.context = context;
        DatabaseHelper databaseHelper = new DatabaseHelper(context, mBook.getUID());
        mDatabaseHelper = databaseHelper;
        mDB = databaseHelper.getWritableDatabase();
        mCommoditiesDbAdapter = new CommoditiesDbAdapter(mDB);
        mPricesDbAdapter = new PricesDbAdapter(mCommoditiesDbAdapter);
        mTransactionsDbAdapter = new TransactionsDbAdapter(mCommoditiesDbAdapter);
        mAccountsDbAdapter = new AccountsDbAdapter(mTransactionsDbAdapter, mPricesDbAdapter);
        RecurrenceDbAdapter recurrenceDbAdapter = new RecurrenceDbAdapter(mDB);
        mScheduledActionsDbAdapter = new ScheduledActionDbAdapter(recurrenceDbAdapter);
        mBudgetsDbAdapter = new BudgetsDbAdapter(recurrenceDbAdapter);
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
                slotTypes.push(attributes.getValue(ATTR_KEY_TYPE));
                break;
            case TAG_COMMODITY:
                mCommodity = new Commodity("", "", 100);
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
                mAccount.setHidden(accountType == AccountType.ROOT); //flag root account as hidden
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
                if (mAccount != null && !mInTemplates) {
                    Commodity commodity = getCommodity(mCommoditySpace, mCommodityId);
                    if (commodity != null) {
                        mAccount.setCommodity(commodity);
                    } else {
                        throw new SAXException("Commodity with '" + mCommoditySpace + ":" + mCommodityId
                            + "' currency code not found in the database for account " + mAccount.getUID());
                    }
                    String currencyId = commodity.getCurrencyCode();
                    Integer currencyCount = mCurrencyCount.get(currencyId);
                    if (currencyCount == null) currencyCount = 0;
                    mCurrencyCount.put(currencyId, currencyCount + 1);
                }
                break;
            case TAG_ACCT_PARENT:
                mAccount.setParentUID(characterString);
                break;
            case TAG_ACCOUNT:
                if (!mInTemplates) { //we ignore template accounts, we have no use for them. FIXME someday and import the templates too
                    mAccountList.add(mAccount);
                    mAccountMap.put(mAccount.getUID(), mAccount);
                    // check ROOT account
                    if (mAccount.isRoot()) {
                        if (mRootAccount == null) {
                            mRootAccount = mAccount;
                        } else {
                            throw new SAXException("Multiple ROOT accounts exist in book");
                        }
                    }
                    // prepare for next input
                    mAccount = null;
                }
                break;
            case TAG_SLOT:
                if (mInBudgetSlot) {
                    budgetPeriod = null;
                }
                break;
            case TAG_SLOT_KEY:
                switch (characterString) {
                    case KEY_PLACEHOLDER:
                        mInPlaceHolderSlot = true;
                        break;
                    case KEY_COLOR:
                        mInColorSlot = true;
                        break;
                    case KEY_FAVORITE:
                        mInFavoriteSlot = true;
                        break;
                    case KEY_HIDDEN:
                        mInHiddenSlot = true;
                        break;
                    case KEY_NOTES:
                        mIsNote = true;
                        budgetAccount = null;
                        break;
                    case KEY_DEFAULT_TRANSFER_ACCOUNT:
                        mInDefaultTransferAccount = true;
                        break;
                    case KEY_EXPORTED:
                        mInExported = true;
                        break;
                    case KEY_SPLIT_ACCOUNT_SLOT:
                        mInSplitAccountSlot = true;
                        break;
                    case KEY_CREDIT_NUMERIC:
                        mInCreditNumericSlot = true;
                        break;
                    case KEY_DEBIT_NUMERIC:
                        mInDebitNumericSlot = true;
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
                String slotType = slotTypes.pop();
                if (mInPlaceHolderSlot) {
                    //Timber.v("Setting account placeholder flag");
                    mAccount.setPlaceholder(Boolean.parseBoolean(characterString));
                    mInPlaceHolderSlot = false;
                } else if (mInColorSlot) {
                    //Timber.d("Parsing color code: " + characterString);
                    String color = characterString.trim();
                    //GnuCash exports the account color in format #rrrgggbbb, but we need only #rrggbb.
                    //so we trim the last digit in each block, doesn't affect the color much
                    if (mAccount != null) {
                        try {
                            mAccount.setColor(color);
                        } catch (IllegalArgumentException ex) {
                            //sometimes the color entry in the account file is "Not set" instead of just blank. So catch!
                            Timber.e(ex, "Invalid color code '" + color + "' for account " + mAccount.getName());
                        }
                    }
                    mInColorSlot = false;
                } else if (mInFavoriteSlot) {
                    mAccount.setFavorite(Boolean.parseBoolean(characterString));
                    mInFavoriteSlot = false;
                } else if (mInHiddenSlot) {
                    mAccount.setHidden(Boolean.parseBoolean(characterString));
                    mInHiddenSlot = false;
                } else if (mInDefaultTransferAccount) {
                    mAccount.setDefaultTransferAccountUID(characterString);
                    mInDefaultTransferAccount = false;
                } else if (mInExported) {
                    if (mTransaction != null) {
                        mTransaction.setExported(Boolean.parseBoolean(characterString));
                    }
                    mInExported = false;
                } else if (mInTemplates && mInSplitAccountSlot) {
                    mSplit.setAccountUID(characterString);
                    mInSplitAccountSlot = false;
                } else if (mInTemplates && mInCreditNumericSlot) {
                    handleEndOfTemplateNumericSlot(mSplit, characterString, TransactionType.CREDIT);
                } else if (mInTemplates && mInDebitNumericSlot) {
                    handleEndOfTemplateNumericSlot(mSplit, characterString, TransactionType.DEBIT);
                } else if (mInBudgetSlot) {
                    switch (slotType) {
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
                                if (budgetAmount != null) {
                                    budgetAmount.setNotes(characterString);
                                }
                            }
                            budgetPeriod = null;
                            break;
                    }
                } else if (mIsNote && ATTR_VALUE_STRING.equals(slotType)) {
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
                slotTypes.clear();
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
                String splitAccountId = characterString;
                mSplit.setAccountUID(splitAccountId);
                if (!mInTemplates) {
                    //this is intentional: GnuCash XML formats split amounts, credits are negative, debits are positive.
                    mSplit.setType(mNegativeQuantity ? TransactionType.CREDIT : TransactionType.DEBIT);
                    //the split amount uses the account currency
                    mSplit.setQuantity(new Money(mQuantity, getCommodityForAccount(splitAccountId)));
                    //the split value uses the transaction currency
                    mSplit.setValue(new Money(mValue, mTransaction.getCommodity()));
                } else {
                    if (!mIgnoreTemplateTransaction) {
                        mTemplateAccountToTransactionMap.put(splitAccountId, mTransaction.getUID());
                    }
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
                    if (!mIgnoreTemplateTransaction)
                        mTemplateTransactions.add(mTransaction);
                } else {
                    mTransactionList.add(mTransaction);
                }
                if (mRecurrencePeriod > 0) { //if we find an old format recurrence period, parse it
                    mTransaction.setTemplate(true);
                    ScheduledAction scheduledAction = ScheduledAction.parseScheduledAction(mTransaction, mRecurrencePeriod);
                    mScheduledActionsList.add(scheduledAction);
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
            //todo: export auto_notify, advance_create, advance_notify
            case TAG_SX_NUM_OCCUR:
                mScheduledAction.setTotalPlannedExecutionCount(Integer.parseInt(characterString));
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

                    if (mIsRecurrenceStart && mScheduledAction != null) {
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
                    mScheduledAction.setActionUID(mTemplateAccountToTransactionMap.get(characterString));
                } else {
                    mScheduledAction.setActionUID(BaseModel.generateUID());
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
                    mScheduledActionsList.add(mScheduledAction);
                    int count = generateMissedScheduledTransactions(mScheduledAction);
                    Timber.i("Generated %d transactions from scheduled action", count);
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
                    mPrice.setType(characterString);
                }
                break;
            case TAG_PRICE:
                if (mPrice != null) {
                    mPriceList.add(mPrice);
                    mPrice = null;
                }
                break;
            case TAG_BUDGET:
                if (!mBudget.getBudgetAmounts().isEmpty()) //ignore if no budget amounts exist for the budget
                    mBudgetList.add(mBudget);
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
                    putCommodity(mCommodity);
                    mCommodity = null;
                }
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
        Map<String, String> mapFullName = new HashMap<>(mAccountList.size());
        Map<String, Account> mapImbalanceAccount = new HashMap<>();

        // The XML has no ROOT, create one
        if (mRootAccount == null) {
            mRootAccount = new Account(AccountsDbAdapter.ROOT_ACCOUNT_NAME);
            mRootAccount.setAccountType(AccountType.ROOT);
            mAccountList.add(mRootAccount);
            mAccountMap.put(mRootAccount.getUID(), mRootAccount);
        }

        String imbalancePrefix = AccountsDbAdapter.getImbalanceAccountPrefix(context);

        // Add all account without a parent to ROOT, and collect top level imbalance accounts
        for (Account account : mAccountList) {
            mapFullName.put(account.getUID(), null);
            boolean topLevel = false;
            if (account.getParentUID() == null && account.getAccountType() != AccountType.ROOT) {
                account.setParentUID(mRootAccount.getUID());
                topLevel = true;
            }
            if (topLevel || mRootAccount.getUID().equals(account.getParentUID())) {
                if (account.getName().startsWith(imbalancePrefix)) {
                    mapImbalanceAccount.put(account.getName().substring(imbalancePrefix.length()), account);
                }
            }
        }

        // Set the account for created balancing splits to correct imbalance accounts
        for (Split split : mAutoBalanceSplits) {
            // XXX: yes, getAccountUID() returns a currency code in this case (see Transaction.createAutoBalanceSplit())
            String currencyCode = split.getAccountUID();
            Account imbAccount = mapImbalanceAccount.get(currencyCode);
            if (imbAccount == null) {
                imbAccount = new Account(imbalancePrefix + currencyCode, getCommodity(Commodity.COMMODITY_CURRENCY, currencyCode));
                imbAccount.setParentUID(mRootAccount.getUID());
                imbAccount.setAccountType(AccountType.BANK);
                mapImbalanceAccount.put(currencyCode, imbAccount);
                mAccountList.add(imbAccount);
            }
            split.setAccountUID(imbAccount.getUID());
        }

        Stack<Account> stack = new Stack<>();
        for (Account account : mAccountList) {
            if (mapFullName.get(account.getUID()) != null) {
                continue;
            }
            stack.push(account);
            String parentAccountFullName;
            while (!stack.isEmpty()) {
                Account acc = stack.peek();
                if (acc.isRoot()) {
                    // ROOT_ACCOUNT_FULL_NAME should ensure ROOT always sorts first
                    mapFullName.put(acc.getUID(), AccountsDbAdapter.ROOT_ACCOUNT_FULL_NAME);
                    stack.pop();
                    continue;
                }
                String parentUID = acc.getParentUID();
                Account parentAccount = mAccountMap.get(parentUID);
                // ROOT account will be added if not exist, so now only ROOT
                // has an empty parent
                if (parentAccount.isRoot()) {
                    // top level account, full name is the same as its name
                    mapFullName.put(acc.getUID(), acc.getName());
                    stack.pop();
                    continue;
                }
                parentAccountFullName = mapFullName.get(parentUID);
                if (parentAccountFullName == null) {
                    // non-top-level account, parent full name still unknown
                    stack.push(parentAccount);
                    continue;
                }
                mapFullName.put(acc.getUID(), parentAccountFullName +
                    AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + acc.getName());
                stack.pop();
            }
        }
        for (Account account : mAccountList) {
            account.setFullName(mapFullName.get(account.getUID()));
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
            GnuCashApplication.setDefaultCurrencyCode(mostAppearedCurrency);
        }

        saveToDatabase();
    }

    /**
     * Saves the imported data to the database
     *
     * @return GUID of the newly created book, or null if not successful
     */
    private void saveToDatabase() {
        mBook.setRootAccountUID(mRootAccount.getUID());
        //we on purpose do not set the book active. Only import. Caller should handle activation

        long startTime = System.nanoTime();
        Timber.d("bulk insert starts");
        try {
            mAccountsDbAdapter.beginTransaction();
            // disable foreign key. The database structure should be ensured by the data inserted.
            // it will make insertion much faster.
            mAccountsDbAdapter.enableForeignKey(false);
            Timber.d("before clean up db");
            mAccountsDbAdapter.deleteAllRecords();
            Timber.d("db clean up done %d ns", System.nanoTime() - startTime);

            List<Commodity> commodities = new ArrayList<>();
            for (Map<String, Commodity> commoditiesById : mCommodities.values()) {
                commodities.addAll(commoditiesById.values());
            }
            long nCommodities = mCommoditiesDbAdapter.bulkAddRecords(commodities, DatabaseAdapter.UpdateMethod.insert);
            Timber.d("%d commodities inserted", nCommodities);

            long nAccounts = mAccountsDbAdapter.bulkAddRecords(mAccountList, DatabaseAdapter.UpdateMethod.insert);
            Timber.d("%d accounts inserted", nAccounts);
            //We need to add scheduled actions first because there is a foreign key constraint on transactions
            //which are generated from scheduled actions (we do auto-create some transactions during import)
            long nSchedActions = mScheduledActionsDbAdapter.bulkAddRecords(mScheduledActionsList, DatabaseAdapter.UpdateMethod.insert);
            Timber.d("%d scheduled actions inserted", nSchedActions);

            long nTempTransactions = mTransactionsDbAdapter.bulkAddRecords(mTemplateTransactions, DatabaseAdapter.UpdateMethod.insert);
            Timber.d("%d template transactions inserted", nTempTransactions);

            long nTransactions = mTransactionsDbAdapter.bulkAddRecords(mTransactionList, DatabaseAdapter.UpdateMethod.insert);
            Timber.d("%d transactions inserted", nTransactions);

            long nPrices = mPricesDbAdapter.bulkAddRecords(mPriceList, DatabaseAdapter.UpdateMethod.insert);
            Timber.d("%d prices inserted", nPrices);

            //// TODO: 01.06.2016 Re-enable import of Budget stuff when the UI is complete
            long nBudgets = mBudgetsDbAdapter.bulkAddRecords(mBudgetList, DatabaseAdapter.UpdateMethod.insert);
            Timber.d("%d budgets inserted", nBudgets);

            long endTime = System.nanoTime();
            Timber.d("bulk insert time: %d", endTime - startTime);

            //if all of the import went smoothly, then add the book to the book db
            booksDbAdapter.addRecord(mBook, DatabaseAdapter.UpdateMethod.insert);
            mAccountsDbAdapter.setTransactionSuccessful();
        } finally {
            mAccountsDbAdapter.enableForeignKey(true);
            mAccountsDbAdapter.endTransaction();
            close();
        }
    }

    @Override
    public void close() {
        mDatabaseHelper.close(); //close it after import
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
                Commodity commodity = getCommodityForAccount(split.getAccountUID());
                Money amount = new Money(splitAmount, commodity);

                split.setValue(amount);
                split.setType(splitType);
                mIgnoreTemplateTransaction = false; //we have successfully parsed an amount
            }
        } catch (NumberFormatException | ParseException e) {
            Timber.e(e, "Error parsing template credit split amount %s", value);
        } finally {
            mInCreditNumericSlot = false;
            mInDebitNumericSlot = false;
        }
    }

    /**
     * Generates the runs of the scheduled action which have been missed since the file was last opened.
     *
     * @param scheduledAction Scheduled action for transaction
     * @return Number of transaction instances generated
     */
    private int generateMissedScheduledTransactions(ScheduledAction scheduledAction) {
        //if this scheduled action should not be run for any reason, return immediately
        if (scheduledAction.getActionType() != ScheduledAction.ActionType.TRANSACTION
            || !scheduledAction.isEnabled() || !scheduledAction.shouldAutoCreate()
            || (scheduledAction.getEndTime() > 0 && scheduledAction.getEndTime() > System.currentTimeMillis())
            || (scheduledAction.getTotalPlannedExecutionCount() > 0 && scheduledAction.getExecutionCount() >= scheduledAction.getTotalPlannedExecutionCount())) {
            return 0;
        }

        long lastRuntime = scheduledAction.getStartTime();
        if (scheduledAction.getLastRunTime() > 0) {
            lastRuntime = scheduledAction.getLastRunTime();
        }

        int generatedTransactionCount = 0;
        long period = scheduledAction.getPeriod();
        final String actionUID = scheduledAction.getActionUID();
        while ((lastRuntime = lastRuntime + period) <= System.currentTimeMillis()) {
            for (Transaction templateTransaction : mTemplateTransactions) {
                if (templateTransaction.getUID().equals(actionUID)) {
                    Transaction transaction = new Transaction(templateTransaction, true);
                    transaction.setTime(lastRuntime);
                    transaction.setScheduledActionUID(scheduledAction.getUID());
                    mTransactionList.add(transaction);
                    //autobalance splits are generated with the currency of the transactions as the GUID
                    //so we add them to the mAutoBalanceSplits which will be updated to real GUIDs before saving
                    List<Split> autoBalanceSplits = transaction.getSplits(transaction.getCurrencyCode());
                    mAutoBalanceSplits.addAll(autoBalanceSplits);
                    scheduledAction.setExecutionCount(scheduledAction.getExecutionCount() + 1);
                    ++generatedTransactionCount;
                    break;
                }
            }
        }
        scheduledAction.setLastRunTime(lastRuntime);
        return generatedTransactionCount;
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
        String space = commodity.getNamespace();
        if (TextUtils.isEmpty(space)) return null;
        if (TEMPLATE.equals(space)) return null;
        String id = commodity.getMnemonic();
        if (TextUtils.isEmpty(id)) return null;
        if (TEMPLATE.equals(id)) return null;

        // Already a database record?
        if (commodity.id != 0L) return null;

        Map<String, Commodity> commoditiesById = mCommodities.get(space);
        if (commoditiesById == null) {
            commoditiesById = new HashMap<>();
            mCommodities.put(space, commoditiesById);
        }
        return commoditiesById.put(id, commodity);
    }
}
