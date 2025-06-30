/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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

package org.gnucash.android.export.xml;

import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import static org.gnucash.android.db.adapter.AccountsDbAdapter.TEMPLATE_ACCOUNT_NAME;
import static org.gnucash.android.export.xml.GncXmlHelper.*;
import static org.gnucash.android.importer.CommoditiesXmlHandler.SOURCE_CURRENCY;
import static org.gnucash.android.math.MathExtKt.toBigDecimal;
import static org.gnucash.android.model.Commodity.TEMPLATE;
import static org.gnucash.android.util.ColorExtKt.formatRGB;

import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.gnc.GncProgressListener;
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
import org.gnucash.android.model.PriceType;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Slot;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.model.WeekendAdjust;
import org.gnucash.android.util.TimestampHelper;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import timber.log.Timber;

/**
 * Creates a GnuCash XML representation of the accounts and transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
public class GncXmlExporter extends Exporter {

    /**
     * Root account for template accounts
     */
    private Account mRootTemplateAccount;
    private final Map<String, Account> mTransactionToTemplateAccountMap = new TreeMap<>();

    /**
     * Creates an exporter with an already open database instance.
     *
     * @param context The context.
     * @param params  Parameters for the export
     * @param bookUID The book UID.
     */
    public GncXmlExporter(@NonNull Context context,
                          @NonNull ExportParams params,
                          @NonNull String bookUID) {
        this(context, params, bookUID, null);
    }

    /**
     * Creates an exporter with an already open database instance.
     *
     * @param context  The context.
     * @param params   Parameters for the export
     * @param bookUID  The book UID.
     * @param listener The listener to receive events.
     */
    public GncXmlExporter(@NonNull Context context,
                          @NonNull ExportParams params,
                          @NonNull String bookUID,
                          @Nullable GncProgressListener listener) {
        super(context, params, bookUID, listener);
    }

    private void writeCounts(XmlSerializer xmlSerializer) throws IOException {
        // commodities count
        long count = mAccountsDbAdapter.getCommoditiesInUseCount();
        if (listener != null) listener.onCommodityCount(count);
        writeCount(xmlSerializer, CD_TYPE_COMMODITY, count);

        // accounts count
        count = mAccountsDbAdapter.getRecordsCount(AccountEntry.COLUMN_TEMPLATE + "=0", null);
        if (listener != null) listener.onAccountCount(count);
        writeCount(xmlSerializer, CD_TYPE_ACCOUNT, count);

        // transactions count
        count = mTransactionsDbAdapter.getRecordsCount(TransactionEntry.COLUMN_TEMPLATE + "=0", null);
        if (listener != null) listener.onTransactionCount(count);
        writeCount(xmlSerializer, CD_TYPE_TRANSACTION, count);

        // scheduled transactions count
        count = mScheduledActionDbAdapter.getRecordsCount(ScheduledAction.ActionType.TRANSACTION);
        if (listener != null) listener.onScheduleCount(count);
        writeCount(xmlSerializer, CD_TYPE_SCHEDXACTION, count);

        // budgets count
        count = mBudgetsDbAdapter.getRecordsCount();
        if (listener != null) listener.onBudgetCount(count);
        writeCount(xmlSerializer, CD_TYPE_BUDGET, count);

        // prices count
        count = mPricesDbAdapter.getRecordsCount();
        if (listener != null) listener.onPriceCount(count);
        writeCount(xmlSerializer, CD_TYPE_PRICE, count);
    }

    private void writeCount(XmlSerializer xmlSerializer, String type, long count) throws IOException {
        if (count <= 0) return;
        xmlSerializer.startTag(null, TAG_COUNT_DATA);
        xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, type);
        xmlSerializer.text(String.valueOf(count));
        xmlSerializer.endTag(null, TAG_COUNT_DATA);
    }

    private void writeSlots(XmlSerializer xmlSerializer, List<Slot> slots) throws IOException {
        if (slots == null || slots.isEmpty()) {
            return;
        }

        final int length = slots.size();
        for (int i = 0; i < length; i++) {
            writeSlot(xmlSerializer, slots.get(i));
        }
    }

    private void writeSlot(XmlSerializer xmlSerializer, Slot slot) throws IOException {
        xmlSerializer.startTag(null, TAG_SLOT);
        xmlSerializer.startTag(null, TAG_SLOT_KEY);
        xmlSerializer.text(slot.key);
        xmlSerializer.endTag(null, TAG_SLOT_KEY);
        xmlSerializer.startTag(null, TAG_SLOT_VALUE);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, slot.type);
        if (slot.value != null) {
            if (slot.isDate()) {
                xmlSerializer.startTag(null, TAG_GDATE);
                xmlSerializer.text(formatDate(slot.getAsDate()));
                xmlSerializer.endTag(null, TAG_GDATE);
            } else if (slot.isFrame()) {
                List<Slot> frame = slot.getAsFrame();
                writeSlots(xmlSerializer, frame);
            } else {
                xmlSerializer.text(slot.toString());
            }
        }
        xmlSerializer.endTag(null, TAG_SLOT_VALUE);
        xmlSerializer.endTag(null, TAG_SLOT);
    }

    private void writeAccounts(XmlSerializer xmlSerializer, boolean isTemplate) throws IOException {
        Timber.i("export accounts. template: %s", isTemplate);
        final String rootUID;
        if (isTemplate) {
            Account account = getRootTemplateAccount();
            if (account == null) {
                Timber.i("No template root account found!");
                return;
            }
            rootUID = account.getUID();
        } else {
            rootUID = mAccountsDbAdapter.getOrCreateRootAccountUID();
            if (TextUtils.isEmpty(rootUID)) {
                throw new ExporterException(mExportParams, "No root account found!");
            }
        }
        Account account = mAccountsDbAdapter.getSimpleRecord(rootUID);
        writeAccount(xmlSerializer, account);
    }

    private void writeAccount(XmlSerializer xmlSerializer, @NonNull Account account) throws IOException {
        cancellationSignal.throwIfCanceled();
        if (listener != null && !account.isTemplate()) listener.onAccount(account);
        // write account
        xmlSerializer.startTag(null, TAG_ACCOUNT);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // account name
        xmlSerializer.startTag(null, TAG_ACCT_NAME);
        xmlSerializer.text(account.getName());
        xmlSerializer.endTag(null, TAG_ACCT_NAME);
        // account guid
        xmlSerializer.startTag(null, TAG_ACCT_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(account.getUID());
        xmlSerializer.endTag(null, TAG_ACCT_ID);
        // account type
        xmlSerializer.startTag(null, TAG_ACCT_TYPE);
        AccountType accountType = account.getAccountType();
        xmlSerializer.text(accountType.name());
        xmlSerializer.endTag(null, TAG_ACCT_TYPE);
        // commodity
        Commodity commodity = account.getCommodity();
        xmlSerializer.startTag(null, TAG_ACCT_COMMODITY);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        xmlSerializer.endTag(null, TAG_ACCT_COMMODITY);
        // commodity scu
        xmlSerializer.startTag(null, TAG_ACCT_COMMODITY_SCU);
        xmlSerializer.text(Integer.toString(commodity.getSmallestFraction()));
        xmlSerializer.endTag(null, TAG_ACCT_COMMODITY_SCU);
        // account description
        String description = account.getDescription();
        if (!TextUtils.isEmpty(description)) {
            xmlSerializer.startTag(null, TAG_ACCT_DESCRIPTION);
            xmlSerializer.text(description);
            xmlSerializer.endTag(null, TAG_ACCT_DESCRIPTION);
        }
        // account slots, color, placeholder, default transfer account, favorite
        List<Slot> slots = new ArrayList<>();

        if (account.isPlaceholder()) {
            slots.add(Slot.string(KEY_PLACEHOLDER, "true"));
        }

        int color = account.getColor();
        if (color != Account.DEFAULT_COLOR) {
            slots.add(Slot.string(KEY_COLOR, formatRGB(color)));
        }

        String defaultTransferAcctUID = account.getDefaultTransferAccountUID();
        if (!TextUtils.isEmpty(defaultTransferAcctUID)) {
            slots.add(Slot.string(KEY_DEFAULT_TRANSFER_ACCOUNT, defaultTransferAcctUID));
        }

        if (account.isFavorite()) {
            slots.add(Slot.string(KEY_FAVORITE, "true"));
        }

        if (account.isHidden()) {
            slots.add(Slot.string(KEY_HIDDEN, "true"));
        }

        String notes = account.getNote();
        if (!TextUtils.isEmpty(notes)) {
            slots.add(Slot.string(KEY_NOTES, notes));
        }

        if (!slots.isEmpty()) {
            xmlSerializer.startTag(null, TAG_ACCT_SLOTS);
            writeSlots(xmlSerializer, slots);
            xmlSerializer.endTag(null, TAG_ACCT_SLOTS);
        }

        // parent uid
        String parentUID = account.getParentUID();
        if ((accountType != AccountType.ROOT) && !TextUtils.isEmpty(parentUID)) {
            xmlSerializer.startTag(null, TAG_ACCT_PARENT);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            xmlSerializer.text(parentUID);
            xmlSerializer.endTag(null, TAG_ACCT_PARENT);
        } else {
            Timber.d("root account : %s", account.getUID());
        }
        xmlSerializer.endTag(null, TAG_ACCOUNT);

        // gnucash desktop requires that parent account appears before its descendants.
        List<String> children = mAccountsDbAdapter.getChildren(account.getUID());
        for (String childUID : children) {
            Account child = mAccountsDbAdapter.getSimpleRecord(childUID);
            writeAccount(xmlSerializer, child);
        }
    }

    /**
     * Serializes transactions from the database to XML
     *
     * @param xmlSerializer XML serializer
     * @param isTemplates   Flag whether to export templates or normal transactions
     * @throws IOException if the XML serializer cannot be written to
     */
    private void writeTransactions(XmlSerializer xmlSerializer, boolean isTemplates) throws IOException {
        Timber.i("write transactions");
        String[] projection = new String[]{
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " AS trans_uid",
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " AS trans_desc",
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_NOTES + " AS trans_notes",
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " AS trans_time",
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_EXPORTED + " AS trans_exported",
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_COMMODITY_UID + " AS trans_commodity",
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_CREATED_AT + " AS trans_date_posted",
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + " AS trans_from_sched_action",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ID + " AS split_id",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_UID + " AS split_uid",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_MEMO + " AS split_memo",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TYPE + " AS split_type",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_NUM + " AS split_value_num",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_DENOM + " AS split_value_denom",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_NUM + " AS split_quantity_num",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_DENOM + " AS split_quantity_denom",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " AS split_acct_uid",
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID + " AS split_sched_xaction_acct_uid"
        };
        final String where;
        if (isTemplates) {
            where = TransactionEntry.COLUMN_TEMPLATE + "=1";
        } else {
            where = TransactionEntry.COLUMN_TEMPLATE + "=0";
        }
        String orderBy = "trans_date_posted ASC"
            + ", " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " ASC"
            + ", " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " ASC"
            + ", " + "split_id ASC";
        final Cursor cursor = mTransactionsDbAdapter.fetchTransactionsWithSplits(projection, where, null, orderBy);
        if (!cursor.moveToFirst()) {
            cursor.close();
            return;
        }

        if (isTemplates) {
            mRootTemplateAccount = new Account(TEMPLATE_ACCOUNT_NAME, Commodity.template);
            mRootTemplateAccount.setAccountType(AccountType.ROOT);
            mTransactionToTemplateAccountMap.put("", mRootTemplateAccount);

            //FIXME: Retrieve the template account GUIDs from the scheduled action table and create accounts with that
            //this will allow use to maintain the template account GUID when we import from the desktop and also use the same for the splits
            do {
                cancellationSignal.throwIfCanceled();
                String txUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"));
                Account account = new Account(BaseModel.generateUID(), Commodity.template);
                account.setAccountType(AccountType.BANK);
                account.setParentUID(mRootTemplateAccount.getUID());
                mTransactionToTemplateAccountMap.put(txUID, account);
            } while (cursor.moveToNext());

            writeTemplateAccounts(xmlSerializer, mTransactionToTemplateAccountMap.values());
            //push cursor back to before the beginning
            cursor.moveToFirst();
        }

        //// FIXME: 12.10.2015 export split reconciled_state and reconciled_date to the export
        String lastTrxUID = "";
        Commodity trnCommodity = null;
        Transaction transaction;
        do {
            cancellationSignal.throwIfCanceled();

            String curTrxUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"));
            // new transaction starts
            if (!lastTrxUID.equals(curTrxUID)) {
                // there's an old transaction, close it
                if (!TextUtils.isEmpty(lastTrxUID)) {
                    xmlSerializer.endTag(null, TAG_TRN_SPLITS);
                    xmlSerializer.endTag(null, TAG_TRANSACTION);
                }
                // new transaction
                String description = cursor.getString(cursor.getColumnIndexOrThrow("trans_desc"));
                String commodityUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_commodity"));
                trnCommodity = mCommoditiesDbAdapter.getRecord(commodityUID);
                transaction = new Transaction(description);
                transaction.setUID(curTrxUID);
                transaction.setCommodity(trnCommodity);
                if (listener != null) listener.onTransaction(transaction);
                xmlSerializer.startTag(null, TAG_TRANSACTION);
                xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
                // transaction id
                xmlSerializer.startTag(null, TAG_TRX_ID);
                xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
                xmlSerializer.text(curTrxUID);
                xmlSerializer.endTag(null, TAG_TRX_ID);
                // currency
                xmlSerializer.startTag(null, TAG_TRX_CURRENCY);
                xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
                xmlSerializer.text(trnCommodity.getNamespace());
                xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
                xmlSerializer.startTag(null, TAG_COMMODITY_ID);
                xmlSerializer.text(trnCommodity.getCurrencyCode());
                xmlSerializer.endTag(null, TAG_COMMODITY_ID);
                xmlSerializer.endTag(null, TAG_TRX_CURRENCY);
                // date posted, time which user put on the transaction
                long datePosted = cursor.getLong(cursor.getColumnIndexOrThrow("trans_time"));
                String strDate = formatDateTime(datePosted);
                xmlSerializer.startTag(null, TAG_DATE_POSTED);
                xmlSerializer.startTag(null, TAG_TS_DATE);
                xmlSerializer.text(strDate);
                xmlSerializer.endTag(null, TAG_TS_DATE);
                xmlSerializer.endTag(null, TAG_DATE_POSTED);

                // date entered, time when the transaction was actually created
                Timestamp timeEntered = TimestampHelper.getTimestampFromUtcString(cursor.getString(cursor.getColumnIndexOrThrow("trans_date_posted")));
                String dateEntered = formatDateTime(timeEntered);
                xmlSerializer.startTag(null, TAG_DATE_ENTERED);
                xmlSerializer.startTag(null, TAG_TS_DATE);
                xmlSerializer.text(dateEntered);
                xmlSerializer.endTag(null, TAG_TS_DATE);
                xmlSerializer.endTag(null, TAG_DATE_ENTERED);

                // description
                xmlSerializer.startTag(null, TAG_TRN_DESCRIPTION);
                xmlSerializer.text(transaction.getDescription());
                xmlSerializer.endTag(null, TAG_TRN_DESCRIPTION);
                lastTrxUID = curTrxUID;

                // slots
                List<Slot> slots = new ArrayList<>();
                slots.add(Slot.gdate(ATTR_KEY_DATE_POSTED, datePosted));

                String notes = cursor.getString(cursor.getColumnIndexOrThrow("trans_notes"));
                if (!TextUtils.isEmpty(notes)) {
                    slots.add(Slot.string(KEY_NOTES, notes));
                }

                String scheduledActionUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_from_sched_action"));
                if (!TextUtils.isEmpty(scheduledActionUID)) {
                    slots.add(Slot.guid(KEY_FROM_SCHED_ACTION, scheduledActionUID));
                }
                if (!slots.isEmpty()) {
                    xmlSerializer.startTag(null, TAG_TRN_SLOTS);
                    writeSlots(xmlSerializer, slots);
                    xmlSerializer.endTag(null, TAG_TRN_SLOTS);
                }

                // splits start
                xmlSerializer.startTag(null, TAG_TRN_SPLITS);
            }
            xmlSerializer.startTag(null, TAG_TRN_SPLIT);
            // split id
            xmlSerializer.startTag(null, TAG_SPLIT_ID);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            xmlSerializer.text(cursor.getString(cursor.getColumnIndexOrThrow("split_uid")));
            xmlSerializer.endTag(null, TAG_SPLIT_ID);
            // memo
            String memo = cursor.getString(cursor.getColumnIndexOrThrow("split_memo"));
            if (!TextUtils.isEmpty(memo)) {
                xmlSerializer.startTag(null, TAG_SPLIT_MEMO);
                xmlSerializer.text(memo);
                xmlSerializer.endTag(null, TAG_SPLIT_MEMO);
            }
            // reconciled
            xmlSerializer.startTag(null, TAG_RECONCILED_STATE);
            xmlSerializer.text("n"); //fixme: retrieve reconciled state from the split in the db
            xmlSerializer.endTag(null, TAG_RECONCILED_STATE);
            //todo: if split is reconciled, add reconciled date
            // value, in the transaction's currency
            TransactionType trxType = TransactionType.of(cursor.getString(cursor.getColumnIndexOrThrow("split_type")));
            long splitValueNum = cursor.getLong(cursor.getColumnIndexOrThrow("split_value_num"));
            long splitValueDenom = cursor.getLong(cursor.getColumnIndexOrThrow("split_value_denom"));
            BigDecimal splitAmount = toBigDecimal(splitValueNum, splitValueDenom);
            String strValue = "0/100";
            if (!isTemplates) { //when doing normal transaction export
                strValue = (trxType == TransactionType.CREDIT ? "-" : "") + splitValueNum + "/" + splitValueDenom;
            }
            xmlSerializer.startTag(null, TAG_SPLIT_VALUE);
            xmlSerializer.text(strValue);
            xmlSerializer.endTag(null, TAG_SPLIT_VALUE);
            // quantity, in the split account's currency
            long splitQuantityNum = cursor.getLong(cursor.getColumnIndexOrThrow("split_quantity_num"));
            long splitQuantityDenom = cursor.getLong(cursor.getColumnIndexOrThrow("split_quantity_denom"));
            strValue = "0/1";
            if (!isTemplates) {
                strValue = (trxType == TransactionType.CREDIT ? "-" : "") + splitQuantityNum + "/" + splitQuantityDenom;
            }
            xmlSerializer.startTag(null, TAG_SPLIT_QUANTITY);
            xmlSerializer.text(strValue);
            xmlSerializer.endTag(null, TAG_SPLIT_QUANTITY);
            // account guid
            xmlSerializer.startTag(null, TAG_SPLIT_ACCOUNT);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            String splitAccountUID = cursor.getString(cursor.getColumnIndexOrThrow("split_acct_uid"));
            if (isTemplates) {
                //get the UID of the template account
                splitAccountUID = mTransactionToTemplateAccountMap.get(curTrxUID).getUID();
            } else {
                splitAccountUID = cursor.getString(cursor.getColumnIndexOrThrow("split_acct_uid"));
            }
            xmlSerializer.text(splitAccountUID);
            xmlSerializer.endTag(null, TAG_SPLIT_ACCOUNT);

            //if we are exporting a template transaction, then we need to add some extra slots
            // TODO be able to import `KEY_SCHED_XACTION` slots.
            if (isTemplates) {
                List<Slot> slots = new ArrayList<>();
                List<Slot> frame = new ArrayList<>();
                String sched_xaction_acct_uid = cursor.getString(cursor.getColumnIndexOrThrow("split_sched_xaction_acct_uid"));
                if (TextUtils.isEmpty(sched_xaction_acct_uid)) {
                    sched_xaction_acct_uid = splitAccountUID;
                }
                frame.add(Slot.guid(KEY_SPLIT_ACCOUNT_SLOT, sched_xaction_acct_uid));
                if (trxType == TransactionType.CREDIT) {
                    frame.add(Slot.string(KEY_CREDIT_FORMULA, formatFormula(splitAmount, trnCommodity)));
                    frame.add(Slot.numeric(KEY_CREDIT_NUMERIC, splitValueNum, splitValueDenom));
                    frame.add(Slot.string(KEY_DEBIT_FORMULA, ""));
                    frame.add(Slot.numeric(KEY_DEBIT_NUMERIC, 0, 1));
                } else {
                    frame.add(Slot.string(KEY_CREDIT_FORMULA, ""));
                    frame.add(Slot.numeric(KEY_CREDIT_NUMERIC, 0, 1));
                    frame.add(Slot.string(KEY_DEBIT_FORMULA, formatFormula(splitAmount, trnCommodity)));
                    frame.add(Slot.numeric(KEY_DEBIT_NUMERIC, splitValueNum, splitValueDenom));
                }
                slots.add(Slot.frame(KEY_SCHED_XACTION, frame));

                xmlSerializer.startTag(null, TAG_SPLIT_SLOTS);
                writeSlots(xmlSerializer, slots);
                xmlSerializer.endTag(null, TAG_SPLIT_SLOTS);
            }

            xmlSerializer.endTag(null, TAG_TRN_SPLIT);
        } while (cursor.moveToNext());
        if (!TextUtils.isEmpty(lastTrxUID)) { // there's an unfinished transaction, close it
            xmlSerializer.endTag(null, TAG_TRN_SPLITS);
            xmlSerializer.endTag(null, TAG_TRANSACTION);
        }
        cursor.close();
    }

    private void writeTemplateAccounts(XmlSerializer xmlSerializer, Collection<Account> accounts) throws IOException {
        for (Account account : accounts) {
            writeAccount(xmlSerializer, account);
        }
    }

    private void writeTemplateTransactions(XmlSerializer xmlSerializer) throws IOException {
        if (mTransactionsDbAdapter.getTemplateTransactionsCount() > 0) {
            xmlSerializer.startTag(null, TAG_TEMPLATE_TRANSACTIONS);
            //TODO writeAccounts(xmlSerializer, true);
            writeTransactions(xmlSerializer, true);
            xmlSerializer.endTag(null, TAG_TEMPLATE_TRANSACTIONS);
        }
    }

    /**
     * Serializes {@link ScheduledAction}s from the database to XML
     *
     * @param xmlSerializer XML serializer
     * @throws IOException
     */
    private void writeScheduledTransactions(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("write scheduled transactions");
        List<ScheduledAction> actions = mScheduledActionDbAdapter.getRecords(ScheduledAction.ActionType.TRANSACTION);
        for (ScheduledAction scheduledAction : actions) {
            writeScheduledTransaction(xmlSerializer, scheduledAction);
        }
    }

    private void writeScheduledTransaction(XmlSerializer xmlSerializer, ScheduledAction scheduledAction) throws IOException {
        String uid = scheduledAction.getUID();
        String actionUID = scheduledAction.getActionUID();
        String accountUID = scheduledAction.getTemplateAccountUID();
        Account account = null;
        if (!TextUtils.isEmpty(accountUID)) {
            try {
                account = mAccountsDbAdapter.getSimpleRecord(accountUID);
            } catch (IllegalArgumentException ignore) {
            }
        }
        if (account == null) {
            account = mTransactionToTemplateAccountMap.get(actionUID);
            uid = account.getName();
        }
        if (account == null) { //if the action UID does not belong to a transaction we've seen before, skip it
            return;
        }
        if (listener != null) listener.onSchedule(scheduledAction);
        ScheduledAction.ActionType actionType = scheduledAction.getActionType();

        xmlSerializer.startTag(null, TAG_SCHEDULED_ACTION);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        xmlSerializer.startTag(null, TAG_SX_ID);

        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(uid);
        xmlSerializer.endTag(null, TAG_SX_ID);

        String name = null;
        if (name == null) {
            if (actionType == ScheduledAction.ActionType.TRANSACTION) {
                String transactionUID = actionUID;
                name = mTransactionsDbAdapter.getAttribute(transactionUID, TransactionEntry.COLUMN_DESCRIPTION);
            } else {
                name = actionType.name();
            }
        }
        xmlSerializer.startTag(null, TAG_SX_NAME);
        xmlSerializer.text(name);
        xmlSerializer.endTag(null, TAG_SX_NAME);

        xmlSerializer.startTag(null, TAG_SX_ENABLED);
        xmlSerializer.text(scheduledAction.isEnabled() ? "y" : "n");
        xmlSerializer.endTag(null, TAG_SX_ENABLED);
        xmlSerializer.startTag(null, TAG_SX_AUTO_CREATE);
        xmlSerializer.text(scheduledAction.shouldAutoCreate() ? "y" : "n");
        xmlSerializer.endTag(null, TAG_SX_AUTO_CREATE);
        xmlSerializer.startTag(null, TAG_SX_AUTO_CREATE_NOTIFY);
        xmlSerializer.text(scheduledAction.shouldAutoNotify() ? "y" : "n");
        xmlSerializer.endTag(null, TAG_SX_AUTO_CREATE_NOTIFY);
        xmlSerializer.startTag(null, TAG_SX_ADVANCE_CREATE_DAYS);
        xmlSerializer.text(Integer.toString(scheduledAction.getAdvanceCreateDays()));
        xmlSerializer.endTag(null, TAG_SX_ADVANCE_CREATE_DAYS);
        xmlSerializer.startTag(null, TAG_SX_ADVANCE_REMIND_DAYS);
        xmlSerializer.text(Integer.toString(scheduledAction.getAdvanceNotifyDays()));
        xmlSerializer.endTag(null, TAG_SX_ADVANCE_REMIND_DAYS);
        xmlSerializer.startTag(null, TAG_SX_INSTANCE_COUNT);
        String scheduledActionUID = scheduledAction.getUID();
        long instanceCount = mScheduledActionDbAdapter.getActionInstanceCount(scheduledActionUID);
        xmlSerializer.text(Long.toString(instanceCount));
        xmlSerializer.endTag(null, TAG_SX_INSTANCE_COUNT);

        //start date
        long scheduleStartTime = scheduledAction.getCreatedTimestamp().getTime();
        writeDate(xmlSerializer, TAG_SX_START, scheduleStartTime);

        long lastRunTime = scheduledAction.getLastRunTime();
        if (lastRunTime > 0) {
            writeDate(xmlSerializer, TAG_SX_LAST, lastRunTime);
        }

        long endTime = scheduledAction.getEndTime();
        if (endTime > 0) {
            //end date
            writeDate(xmlSerializer, TAG_SX_END, endTime);
        } else { //add number of occurrences
            int totalFrequency = scheduledAction.getTotalPlannedExecutionCount();
            if (totalFrequency > 0) {
                xmlSerializer.startTag(null, TAG_SX_NUM_OCCUR);
                xmlSerializer.text(Integer.toString(totalFrequency));
                xmlSerializer.endTag(null, TAG_SX_NUM_OCCUR);
            }

            //remaining occurrences
            int executionCount = scheduledAction.getExecutionCount();
            int remaining = totalFrequency - executionCount;
            if (remaining > 0) {
                xmlSerializer.startTag(null, TAG_SX_REM_OCCUR);
                xmlSerializer.text(Integer.toString(remaining));
                xmlSerializer.endTag(null, TAG_SX_REM_OCCUR);
            }
        }

        String tag = scheduledAction.getTag();
        if (!TextUtils.isEmpty(tag)) {
            xmlSerializer.startTag(null, TAG_SX_TAG);
            xmlSerializer.text(tag);
            xmlSerializer.endTag(null, TAG_SX_TAG);
        }

        xmlSerializer.startTag(null, TAG_SX_TEMPL_ACCOUNT);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(accountUID);
        xmlSerializer.endTag(null, TAG_SX_TEMPL_ACCOUNT);

        //// FIXME: 11.10.2015 Retrieve the information for this section from the recurrence table
        xmlSerializer.startTag(null, TAG_SX_SCHEDULE);
        xmlSerializer.startTag(null, TAG_GNC_RECURRENCE);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION);
        writeRecurrence(xmlSerializer, scheduledAction.getRecurrence());
        xmlSerializer.endTag(null, TAG_GNC_RECURRENCE);
        xmlSerializer.endTag(null, TAG_SX_SCHEDULE);

        xmlSerializer.endTag(null, TAG_SCHEDULED_ACTION);
    }

    /**
     * Serializes a date as a {@code tag} which has a nested {@link GncXmlHelper#TAG_GDATE} which
     * has the date as a text element formatted.
     *
     * @param xmlSerializer XML serializer
     * @param tag           Enclosing tag
     * @param timeMillis    Date to be formatted and output
     * @throws IOException
     */
    private void writeDate(XmlSerializer xmlSerializer, String tag, long timeMillis) throws IOException {
        xmlSerializer.startTag(null, tag);
        xmlSerializer.startTag(null, TAG_GDATE);
        xmlSerializer.text(formatDate(timeMillis));
        xmlSerializer.endTag(null, TAG_GDATE);
        xmlSerializer.endTag(null, tag);
    }

    private void writeCommodities(XmlSerializer xmlSerializer, List<Commodity> commodities) throws IOException {
        Timber.i("write commodities");
        boolean hasTemplate = false;
        for (Commodity commodity : commodities) {
            writeCommodity(xmlSerializer, commodity);
            if (commodity.isTemplate()) {
                hasTemplate = true;
            }
        }
        if (!hasTemplate) {
            writeCommodity(xmlSerializer, Commodity.template);
        }
    }

    private void writeCommodities(XmlSerializer xmlSerializer) throws IOException {
        List<Commodity> commodities = mAccountsDbAdapter.getCommoditiesInUse();
        writeCommodities(xmlSerializer, commodities);
    }

    private void writeCommodity(XmlSerializer xmlSerializer, Commodity commodity) throws IOException {
        if (listener != null) listener.onCommodity(commodity);
        xmlSerializer.startTag(null, TAG_COMMODITY);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        if (!SOURCE_CURRENCY.equals(commodity.getQuoteSource())) {
            if (!TextUtils.isEmpty(commodity.getFullname()) && !commodity.isCurrency()) {
                xmlSerializer.startTag(null, TAG_COMMODITY_NAME);
                xmlSerializer.text(commodity.getFullname());
                xmlSerializer.endTag(null, TAG_COMMODITY_NAME);
            }
            String cusip = commodity.getCusip();
            if (!TextUtils.isEmpty(cusip)) {
                try {
                    // "exchange-code is stored in ISIN/CUSIP"
                    Integer.parseInt(cusip);
                } catch (NumberFormatException e) {
                    xmlSerializer.startTag(null, TAG_COMMODITY_XCODE);
                    xmlSerializer.text(cusip);
                    xmlSerializer.endTag(null, TAG_COMMODITY_XCODE);
                }
            }
            xmlSerializer.startTag(null, TAG_COMMODITY_FRACTION);
            xmlSerializer.text(String.valueOf(commodity.getSmallestFraction()));
            xmlSerializer.endTag(null, TAG_COMMODITY_FRACTION);
        }
        if (commodity.getQuoteFlag()) {
            xmlSerializer.startTag(null, TAG_COMMODITY_GET_QUOTES);
            xmlSerializer.endTag(null, TAG_COMMODITY_GET_QUOTES);
            xmlSerializer.startTag(null, TAG_COMMODITY_QUOTE_SOURCE);
            xmlSerializer.text(commodity.getQuoteSource());
            xmlSerializer.endTag(null, TAG_COMMODITY_QUOTE_SOURCE);
            TimeZone tz = commodity.getQuoteTimeZone();
            xmlSerializer.startTag(null, TAG_COMMODITY_QUOTE_TZ);
            if (tz != null) {
                xmlSerializer.text(tz.getID());
            }
            xmlSerializer.endTag(null, TAG_COMMODITY_QUOTE_TZ);
        }
        xmlSerializer.endTag(null, TAG_COMMODITY);
    }

    private void writePrices(XmlSerializer xmlSerializer) throws IOException {
        List<Price> prices = mPricesDbAdapter.getAllRecords();
        if (prices.isEmpty()) return;

        Timber.i("write prices");
        xmlSerializer.startTag(null, TAG_PRICEDB);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, "1");
        for (Price price : prices) {
            writePrice(xmlSerializer, price);
        }
        xmlSerializer.endTag(null, TAG_PRICEDB);
    }

    private void writePrice(XmlSerializer xmlSerializer, Price price) throws IOException {
        if (listener != null) listener.onPrice(price);
        xmlSerializer.startTag(null, TAG_PRICE);
        // GUID
        xmlSerializer.startTag(null, TAG_PRICE_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(price.getUID());
        xmlSerializer.endTag(null, TAG_PRICE_ID);
        // commodity
        Commodity commodity = price.getCommodity();
        xmlSerializer.startTag(null, TAG_PRICE_COMMODITY);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        xmlSerializer.endTag(null, TAG_PRICE_COMMODITY);
        // currency
        Commodity currency = price.getCurrency();
        xmlSerializer.startTag(null, TAG_PRICE_CURRENCY);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(currency.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(currency.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        xmlSerializer.endTag(null, TAG_PRICE_CURRENCY);
        // time
        xmlSerializer.startTag(null, TAG_PRICE_TIME);
        xmlSerializer.startTag(null, TAG_TS_DATE);
        xmlSerializer.text(formatDateTime(price.getDate()));
        xmlSerializer.endTag(null, TAG_TS_DATE);
        xmlSerializer.endTag(null, TAG_PRICE_TIME);
        // source
        if (!TextUtils.isEmpty(price.getSource())) {
            xmlSerializer.startTag(null, TAG_PRICE_SOURCE);
            xmlSerializer.text(price.getSource());
            xmlSerializer.endTag(null, TAG_PRICE_SOURCE);
        }
        // type, optional
        PriceType type = price.getType();
        if (type != null) {
            xmlSerializer.startTag(null, TAG_PRICE_TYPE);
            xmlSerializer.text(type.getValue());
            xmlSerializer.endTag(null, TAG_PRICE_TYPE);
        }
        // value
        xmlSerializer.startTag(null, TAG_PRICE_VALUE);
        xmlSerializer.text(price.getValueNum() + "/" + price.getValueDenom());
        xmlSerializer.endTag(null, TAG_PRICE_VALUE);
        xmlSerializer.endTag(null, TAG_PRICE);
    }

    /**
     * Exports the recurrence to GnuCash XML, except the recurrence tags itself i.e. the actual recurrence attributes only
     * <p>This is because there are different recurrence start tags for transactions and budgets.<br>
     * So make sure to write the recurrence start/closing tags before/after calling this method.</p>
     *
     * @param xmlSerializer XML serializer
     * @param recurrence    Recurrence object
     */
    private void writeRecurrence(XmlSerializer xmlSerializer, @Nullable Recurrence recurrence) throws IOException {
        if (recurrence == null) return;
        PeriodType periodType = recurrence.getPeriodType();
        xmlSerializer.startTag(null, TAG_RX_MULT);
        xmlSerializer.text(String.valueOf(recurrence.getMultiplier()));
        xmlSerializer.endTag(null, TAG_RX_MULT);
        xmlSerializer.startTag(null, TAG_RX_PERIOD_TYPE);
        xmlSerializer.text(periodType.value);
        xmlSerializer.endTag(null, TAG_RX_PERIOD_TYPE);

        long recurrenceStartTime = recurrence.getPeriodStart();
        writeDate(xmlSerializer, TAG_RX_START, recurrenceStartTime);

        WeekendAdjust weekendAdjust = recurrence.getWeekendAdjust();
        if (weekendAdjust != WeekendAdjust.NONE) {
            /* In r17725 and r17751, I introduced this extra XML child
            element, but this means a gnucash-2.2.x cannot read the SX
            recurrence of a >=2.3.x file anymore, which is bad. In order
            to improve this broken backward compatibility for most of the
            cases, we don't write out this XML element as long as it is
            only "none". */
            xmlSerializer.startTag(null, GncXmlHelper.TAG_RX_WEEKEND_ADJ);
            xmlSerializer.text(weekendAdjust.value);
            xmlSerializer.endTag(null, GncXmlHelper.TAG_RX_WEEKEND_ADJ);
        }
    }

    private void writeBudgets(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("write budgets");
        Cursor cursor = mBudgetsDbAdapter.fetchAllRecords();
        while (cursor.moveToNext()) {
            cancellationSignal.throwIfCanceled();
            Budget budget = mBudgetsDbAdapter.buildModelInstance(cursor);
            writeBudget(xmlSerializer, budget);
        }
        cursor.close();
    }

    private void writeBudget(XmlSerializer xmlSerializer, Budget budget) throws IOException {
        if (listener != null) listener.onBudget(budget);
        xmlSerializer.startTag(null, TAG_BUDGET);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // budget id
        xmlSerializer.startTag(null, TAG_BUDGET_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(budget.getUID());
        xmlSerializer.endTag(null, TAG_BUDGET_ID);
        // budget name
        xmlSerializer.startTag(null, TAG_BUDGET_NAME);
        xmlSerializer.text(budget.getName());
        xmlSerializer.endTag(null, TAG_BUDGET_NAME);
        // budget description
        String description = budget.getDescription();
        if (!TextUtils.isEmpty(description)) {
            xmlSerializer.startTag(null, TAG_BUDGET_DESCRIPTION);
            xmlSerializer.text(description);
            xmlSerializer.endTag(null, TAG_BUDGET_DESCRIPTION);
        }
        // budget periods
        xmlSerializer.startTag(null, TAG_BUDGET_NUM_PERIODS);
        xmlSerializer.text(String.valueOf(budget.getNumberOfPeriods()));
        xmlSerializer.endTag(null, TAG_BUDGET_NUM_PERIODS);
        // budget recurrence
        xmlSerializer.startTag(null, TAG_BUDGET_RECURRENCE);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION);
        writeRecurrence(xmlSerializer, budget.getRecurrence());
        xmlSerializer.endTag(null, TAG_BUDGET_RECURRENCE);

        // budget as slots
        xmlSerializer.startTag(null, TAG_BUDGET_SLOTS);

        writeBudgetAmounts(xmlSerializer, budget);

        // Notes are grouped together.
        writeBudgetNotes(xmlSerializer, budget);

        xmlSerializer.endTag(null, TAG_BUDGET_SLOTS);
        xmlSerializer.endTag(null, TAG_BUDGET);
    }

    private void writeBudgetAmounts(XmlSerializer xmlSerializer, Budget budget) throws IOException {
        List<Slot> slots = new ArrayList<>();

        for (String accountID : budget.getAccounts()) {
            slots.clear();

            final long periodCount = budget.getNumberOfPeriods();
            for (long period = 0; period < periodCount; period++) {
                BudgetAmount budgetAmount = budget.getBudgetAmount(accountID, period);
                if (budgetAmount == null) continue;
                Money amount = budgetAmount.getAmount();
                if (amount.isAmountZero()) continue;
                slots.add(Slot.numeric(String.valueOf(period), amount));
            }

            if (slots.isEmpty()) continue;

            xmlSerializer.startTag(null, TAG_SLOT);
            xmlSerializer.startTag(null, TAG_SLOT_KEY);
            xmlSerializer.text(accountID);
            xmlSerializer.endTag(null, TAG_SLOT_KEY);
            xmlSerializer.startTag(null, TAG_SLOT_VALUE);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_FRAME);
            writeSlots(xmlSerializer, slots);
            xmlSerializer.endTag(null, TAG_SLOT_VALUE);
            xmlSerializer.endTag(null, TAG_SLOT);
        }
    }

    private void writeBudgetNotes(XmlSerializer xmlSerializer, Budget budget) throws IOException {
        List<Slot> notes = new ArrayList<>();

        for (String accountID : budget.getAccounts()) {
            List<Slot> frame = new ArrayList<>();

            final long periodCount = budget.getNumberOfPeriods();
            for (long period = 0; period < periodCount; period++) {
                BudgetAmount budgetAmount = budget.getBudgetAmount(accountID, period);
                if (budgetAmount == null) continue;
                String note = budgetAmount.getNotes();
                if (TextUtils.isEmpty(note)) continue;
                frame.add(Slot.string(String.valueOf(period), note));
            }

            if (!frame.isEmpty()) {
                notes.add(Slot.frame(accountID, frame));
            }
        }

        if (!notes.isEmpty()) {
            List<Slot> slots = new ArrayList<>();
            slots.add(Slot.frame(KEY_NOTES, notes));
            writeSlots(xmlSerializer, slots);
        }
    }

    /**
     * Generates an XML export of the database and writes it to the {@code writer} output stream
     *
     * @param writer Output stream
     * @throws ExporterException
     */
    public void export(@NonNull Writer writer) throws ExporterException {
        Book book = mBooksDbADapter.getActiveBook();
        export(book, writer);
    }

    /**
     * Generates an XML export of the database and writes it to the {@code writer} output stream
     *
     * @param bookUID the book UID to export.
     * @param writer  Output stream
     * @throws ExporterException
     */
    public void export(@NonNull String bookUID, @NonNull Writer writer) throws ExporterException {
        Book book = mBooksDbADapter.getRecord(bookUID);
        export(book, writer);
    }

    /**
     * Generates an XML export of the database and writes it to the {@code writer} output stream
     *
     * @param book   the book to export.
     * @param writer Output stream
     * @throws ExporterException
     */
    public void export(@NonNull Book book, @NonNull Writer writer) throws ExporterException {
        Timber.i("generate export for book %s", book.getUID());
        final long timeStart = SystemClock.elapsedRealtime();
        try {
            String[] namespaces = new String[]{"gnc", "act", "book", "cd", "cmdty", "price", "slot",
                "split", "trn", "ts", "sx", "bgt", "recurrence"};
            XmlSerializer xmlSerializer = XmlPullParserFactory.newInstance().newSerializer();
            try {
                xmlSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            } catch (IllegalStateException ignore) {
                // Feature not supported. No problem
            }
            xmlSerializer.setOutput(writer);
            xmlSerializer.startDocument(StandardCharsets.UTF_8.name(), true);
            // root tag
            xmlSerializer.startTag(null, TAG_ROOT);
            for (String ns : namespaces) {
                xmlSerializer.attribute(null, "xmlns:" + ns, "http://www.gnucash.org/XML/" + ns);
            }
            // book count
            if (listener != null) listener.onBookCount(1);
            writeCount(xmlSerializer, CD_TYPE_BOOK, 1);
            writeBook(xmlSerializer, book);
            xmlSerializer.endTag(null, TAG_ROOT);
            xmlSerializer.endDocument();
            xmlSerializer.flush();
        } catch (Exception e) {
            Timber.e(e);
            throw new ExporterException(mExportParams, e);
        }
        final long timeFinish = SystemClock.elapsedRealtime();
        Timber.v("exported in %d ms", timeFinish - timeStart);
    }

    private void writeBook(XmlSerializer xmlSerializer, Book book) throws IOException {
        if (listener != null) listener.onBook(book);
        // book
        xmlSerializer.startTag(null, TAG_BOOK);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // book id
        xmlSerializer.startTag(null, TAG_BOOK_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(book.getUID());
        xmlSerializer.endTag(null, TAG_BOOK_ID);
        writeCounts(xmlSerializer);
        // export the commodities used in the DB
        writeCommodities(xmlSerializer);
        // prices
        writePrices(xmlSerializer);
        // accounts.
        writeAccounts(xmlSerializer, false);
        // transactions.
        writeTransactions(xmlSerializer, false);
        //transaction templates
        writeTemplateTransactions(xmlSerializer);
        //scheduled actions
        writeScheduledTransactions(xmlSerializer);
        //budgets
        writeBudgets(xmlSerializer);

        xmlSerializer.endTag(null, TAG_BOOK);
    }

    @Override
    protected void writeExport(@NonNull Writer writer, @NonNull ExportParams exportParams) throws Exporter.ExporterException {
        export(getBookUID(), writer);
    }

    @Nullable
    private Account getRootTemplateAccount() {
        Account account = mRootTemplateAccount;
        if (account != null) {
            return account;
        }
        Commodity commodity = mCommoditiesDbAdapter.getCommodity(TEMPLATE);
        final String where;
        final String[] whereArgs;
        if (commodity != null) {
            where = AccountEntry.COLUMN_TYPE + "=? AND " + AccountEntry.COLUMN_COMMODITY_UID + "=?";
            whereArgs = new String[]{AccountType.ROOT.name(), commodity.getUID()};
        } else {
            where = AccountEntry.COLUMN_TYPE + "=? AND " + AccountEntry.COLUMN_NAME + "=?";
            whereArgs = new String[]{AccountType.ROOT.name(), TEMPLATE_ACCOUNT_NAME};
        }
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccounts(where, whereArgs, null);
        if (accounts.isEmpty()) {
            return null;
        }
        mRootTemplateAccount = account = accounts.get(0);
        return account;
    }
}
