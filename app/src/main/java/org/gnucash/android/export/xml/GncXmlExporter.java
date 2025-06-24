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
        xmlSerializer.startTag(NS_GNUCASH, TAG_COUNT_DATA);
        xmlSerializer.attribute(NS_CD, ATTR_KEY_TYPE, type);
        xmlSerializer.text(String.valueOf(count));
        xmlSerializer.endTag(NS_GNUCASH, TAG_COUNT_DATA);
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
        xmlSerializer.startTag(NS_SLOT, TAG_KEY);
        xmlSerializer.text(slot.key);
        xmlSerializer.endTag(NS_SLOT, TAG_KEY);
        xmlSerializer.startTag(NS_SLOT, TAG_VALUE);
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
        xmlSerializer.endTag(NS_SLOT, TAG_VALUE);
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
        xmlSerializer.startTag(NS_GNUCASH, TAG_ACCOUNT);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // account name
        xmlSerializer.startTag(NS_ACCOUNT, TAG_NAME);
        xmlSerializer.text(account.getName());
        xmlSerializer.endTag(NS_ACCOUNT, TAG_NAME);
        // account guid
        xmlSerializer.startTag(NS_ACCOUNT, TAG_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(account.getUID());
        xmlSerializer.endTag(NS_ACCOUNT, TAG_ID);
        // account type
        xmlSerializer.startTag(NS_ACCOUNT, TAG_TYPE);
        AccountType accountType = account.getAccountType();
        xmlSerializer.text(accountType.name());
        xmlSerializer.endTag(NS_ACCOUNT, TAG_TYPE);
        // commodity
        Commodity commodity = account.getCommodity();
        xmlSerializer.startTag(NS_ACCOUNT, TAG_COMMODITY);
        xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE);
        xmlSerializer.startTag(NS_COMMODITY, TAG_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(NS_COMMODITY, TAG_ID);
        xmlSerializer.endTag(NS_ACCOUNT, TAG_COMMODITY);
        // commodity scu
        xmlSerializer.startTag(NS_ACCOUNT, TAG_COMMODITY_SCU);
        xmlSerializer.text(Integer.toString(commodity.getSmallestFraction()));
        xmlSerializer.endTag(NS_ACCOUNT, TAG_COMMODITY_SCU);
        // account description
        String description = account.getDescription();
        if (!TextUtils.isEmpty(description)) {
            xmlSerializer.startTag(NS_ACCOUNT, TAG_DESCRIPTION);
            xmlSerializer.text(description);
            xmlSerializer.endTag(NS_ACCOUNT, TAG_DESCRIPTION);
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
            xmlSerializer.startTag(NS_ACCOUNT, TAG_SLOTS);
            writeSlots(xmlSerializer, slots);
            xmlSerializer.endTag(NS_ACCOUNT, TAG_SLOTS);
        }

        // parent uid
        String parentUID = account.getParentUID();
        if ((accountType != AccountType.ROOT) && !TextUtils.isEmpty(parentUID)) {
            xmlSerializer.startTag(NS_ACCOUNT, TAG_PARENT);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            xmlSerializer.text(parentUID);
            xmlSerializer.endTag(NS_ACCOUNT, TAG_PARENT);
        } else {
            Timber.d("root account : %s", account.getUID());
        }
        xmlSerializer.endTag(NS_GNUCASH, TAG_ACCOUNT);

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
            Account rootTemplateAccount = getRootTemplateAccount();
            mTransactionToTemplateAccountMap.put("", rootTemplateAccount);

            //FIXME: Retrieve the template account GUIDs from the scheduled action table and create accounts with that
            //this will allow use to maintain the template account GUID when we import from the desktop and also use the same for the splits
            do {
                cancellationSignal.throwIfCanceled();
                String txUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"));
                Account account = new Account(BaseModel.generateUID(), Commodity.template);
                account.setAccountType(AccountType.BANK);
                account.setParentUID(rootTemplateAccount.getUID());
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
                    xmlSerializer.endTag(NS_TRANSACTION, TAG_SPLITS);
                    xmlSerializer.endTag(NS_GNUCASH, TAG_TRANSACTION);
                }
                // new transaction
                String description = cursor.getString(cursor.getColumnIndexOrThrow("trans_desc"));
                String commodityUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_commodity"));
                trnCommodity = mCommoditiesDbAdapter.getRecord(commodityUID);
                transaction = new Transaction(description);
                transaction.setUID(curTrxUID);
                transaction.setCommodity(trnCommodity);
                if (listener != null) listener.onTransaction(transaction);
                xmlSerializer.startTag(NS_GNUCASH, TAG_TRANSACTION);
                xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
                // transaction id
                xmlSerializer.startTag(NS_TRANSACTION, TAG_ID);
                xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
                xmlSerializer.text(curTrxUID);
                xmlSerializer.endTag(NS_TRANSACTION, TAG_ID);
                // currency
                xmlSerializer.startTag(NS_TRANSACTION, TAG_CURRENCY);
                xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE);
                xmlSerializer.text(trnCommodity.getNamespace());
                xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE);
                xmlSerializer.startTag(NS_COMMODITY, TAG_ID);
                xmlSerializer.text(trnCommodity.getCurrencyCode());
                xmlSerializer.endTag(NS_COMMODITY, TAG_ID);
                xmlSerializer.endTag(NS_TRANSACTION, TAG_CURRENCY);
                // date posted, time which user put on the transaction
                long datePosted = cursor.getLong(cursor.getColumnIndexOrThrow("trans_time"));
                String strDate = formatDateTime(datePosted);
                xmlSerializer.startTag(NS_TRANSACTION, TAG_DATE_POSTED);
                xmlSerializer.startTag(NS_TS, TAG_DATE);
                xmlSerializer.text(strDate);
                xmlSerializer.endTag(NS_TS, TAG_DATE);
                xmlSerializer.endTag(NS_TRANSACTION, TAG_DATE_POSTED);

                // date entered, time when the transaction was actually created
                Timestamp timeEntered = TimestampHelper.getTimestampFromUtcString(cursor.getString(cursor.getColumnIndexOrThrow("trans_date_posted")));
                xmlSerializer.startTag(NS_TRANSACTION, TAG_DATE_ENTERED);
                xmlSerializer.startTag(NS_TS, TAG_DATE);
                xmlSerializer.text(formatDateTime(timeEntered));
                xmlSerializer.endTag(NS_TS, TAG_DATE);
                xmlSerializer.endTag(NS_TRANSACTION, TAG_DATE_ENTERED);

                // description
                xmlSerializer.startTag(NS_TRANSACTION, TAG_DESCRIPTION);
                xmlSerializer.text(transaction.getDescription());
                xmlSerializer.endTag(NS_TRANSACTION, TAG_DESCRIPTION);
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
                    xmlSerializer.startTag(NS_TRANSACTION, TAG_SLOTS);
                    writeSlots(xmlSerializer, slots);
                    xmlSerializer.endTag(NS_TRANSACTION, TAG_SLOTS);
                }

                // splits start
                xmlSerializer.startTag(NS_TRANSACTION, TAG_SPLITS);
            }
            xmlSerializer.startTag(NS_TRANSACTION, TAG_SPLIT);
            // split id
            xmlSerializer.startTag(NS_SPLIT, TAG_ID);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            xmlSerializer.text(cursor.getString(cursor.getColumnIndexOrThrow("split_uid")));
            xmlSerializer.endTag(NS_SPLIT, TAG_ID);
            // memo
            String memo = cursor.getString(cursor.getColumnIndexOrThrow("split_memo"));
            if (!TextUtils.isEmpty(memo)) {
                xmlSerializer.startTag(NS_SPLIT, TAG_MEMO);
                xmlSerializer.text(memo);
                xmlSerializer.endTag(NS_SPLIT, TAG_MEMO);
            }
            // reconciled
            xmlSerializer.startTag(NS_SPLIT, TAG_RECONCILED_STATE);
            //FIXME: retrieve reconciled state from the split in the db
            // xmlSerializer.text(split.reconcileState);
            xmlSerializer.text("n");
            xmlSerializer.endTag(NS_SPLIT, TAG_RECONCILED_STATE);
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
            xmlSerializer.startTag(NS_SPLIT, TAG_VALUE);
            xmlSerializer.text(strValue);
            xmlSerializer.endTag(NS_SPLIT, TAG_VALUE);
            // quantity, in the split account's currency
            long splitQuantityNum = cursor.getLong(cursor.getColumnIndexOrThrow("split_quantity_num"));
            long splitQuantityDenom = cursor.getLong(cursor.getColumnIndexOrThrow("split_quantity_denom"));
            strValue = "0/1";
            if (!isTemplates) {
                strValue = (trxType == TransactionType.CREDIT ? "-" : "") + splitQuantityNum + "/" + splitQuantityDenom;
            }
            xmlSerializer.startTag(NS_SPLIT, TAG_QUANTITY);
            xmlSerializer.text(strValue);
            xmlSerializer.endTag(NS_SPLIT, TAG_QUANTITY);
            // account guid
            xmlSerializer.startTag(NS_SPLIT, TAG_ACCOUNT);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            String splitAccountUID = cursor.getString(cursor.getColumnIndexOrThrow("split_acct_uid"));
            if (isTemplates) {
                //get the UID of the template account
                splitAccountUID = mTransactionToTemplateAccountMap.get(curTrxUID).getUID();
            } else {
                splitAccountUID = cursor.getString(cursor.getColumnIndexOrThrow("split_acct_uid"));
            }
            xmlSerializer.text(splitAccountUID);
            xmlSerializer.endTag(NS_SPLIT, TAG_ACCOUNT);

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

                xmlSerializer.startTag(NS_SPLIT, TAG_SLOTS);
                writeSlots(xmlSerializer, slots);
                xmlSerializer.endTag(NS_SPLIT, TAG_SLOTS);
            }

            xmlSerializer.endTag(NS_TRANSACTION, TAG_SPLIT);
        } while (cursor.moveToNext());
        if (!TextUtils.isEmpty(lastTrxUID)) { // there's an unfinished transaction, close it
            xmlSerializer.endTag(NS_TRANSACTION, TAG_SPLITS);
            xmlSerializer.endTag(NS_GNUCASH, TAG_TRANSACTION);
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
            xmlSerializer.startTag(NS_GNUCASH, TAG_TEMPLATE_TRANSACTIONS);
            //TODO writeAccounts(xmlSerializer, true);
            writeTransactions(xmlSerializer, true);
            xmlSerializer.endTag(NS_GNUCASH, TAG_TEMPLATE_TRANSACTIONS);
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
            if (account == null) { //if the action UID does not belong to a transaction we've seen before, skip it
                return;
            }
            uid = account.getName();
        }
        if (listener != null) listener.onSchedule(scheduledAction);
        ScheduledAction.ActionType actionType = scheduledAction.getActionType();

        xmlSerializer.startTag(NS_GNUCASH, TAG_SCHEDULED_ACTION);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        xmlSerializer.startTag(NS_SX, TAG_ID);

        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(uid);
        xmlSerializer.endTag(NS_SX, TAG_ID);

        String name = null;
        if (name == null) {
            if (actionType == ScheduledAction.ActionType.TRANSACTION) {
                String transactionUID = actionUID;
                name = mTransactionsDbAdapter.getAttribute(transactionUID, TransactionEntry.COLUMN_DESCRIPTION);
            } else {
                name = actionType.name();
            }
        }
        xmlSerializer.startTag(NS_SX, TAG_NAME);
        xmlSerializer.text(name);
        xmlSerializer.endTag(NS_SX, TAG_NAME);

        xmlSerializer.startTag(NS_SX, TAG_ENABLED);
        xmlSerializer.text(scheduledAction.isEnabled() ? "y" : "n");
        xmlSerializer.endTag(NS_SX, TAG_ENABLED);
        xmlSerializer.startTag(NS_SX, TAG_AUTO_CREATE);
        xmlSerializer.text(scheduledAction.shouldAutoCreate() ? "y" : "n");
        xmlSerializer.endTag(NS_SX, TAG_AUTO_CREATE);
        xmlSerializer.startTag(NS_SX, TAG_AUTO_CREATE_NOTIFY);
        xmlSerializer.text(scheduledAction.shouldAutoNotify() ? "y" : "n");
        xmlSerializer.endTag(NS_SX, TAG_AUTO_CREATE_NOTIFY);
        xmlSerializer.startTag(NS_SX, TAG_ADVANCE_CREATE_DAYS);
        xmlSerializer.text(Integer.toString(scheduledAction.getAdvanceCreateDays()));
        xmlSerializer.endTag(NS_SX, TAG_ADVANCE_CREATE_DAYS);
        xmlSerializer.startTag(NS_SX, TAG_ADVANCE_REMIND_DAYS);
        xmlSerializer.text(Integer.toString(scheduledAction.getAdvanceNotifyDays()));
        xmlSerializer.endTag(NS_SX, TAG_ADVANCE_REMIND_DAYS);
        xmlSerializer.startTag(NS_SX, TAG_INSTANCE_COUNT);
        String scheduledActionUID = scheduledAction.getUID();
        long instanceCount = mScheduledActionDbAdapter.getActionInstanceCount(scheduledActionUID);
        xmlSerializer.text(Long.toString(instanceCount));
        xmlSerializer.endTag(NS_SX, TAG_INSTANCE_COUNT);

        //start date
        long scheduleStartTime = scheduledAction.getStartTime();
        writeDate(xmlSerializer, NS_SX, TAG_START, scheduleStartTime);

        long lastRunTime = scheduledAction.getLastRunTime();
        if (lastRunTime > 0) {
            writeDate(xmlSerializer, NS_SX, TAG_LAST, lastRunTime);
        }

        long endTime = scheduledAction.getEndTime();
        if (endTime > 0) {
            //end date
            writeDate(xmlSerializer, NS_SX, TAG_END, endTime);
        } else { //add number of occurrences
            int totalFrequency = scheduledAction.getTotalPlannedExecutionCount();
            if (totalFrequency > 0) {
                xmlSerializer.startTag(NS_SX, TAG_NUM_OCCUR);
                xmlSerializer.text(Integer.toString(totalFrequency));
                xmlSerializer.endTag(NS_SX, TAG_NUM_OCCUR);
            }

            //remaining occurrences
            int executionCount = scheduledAction.getExecutionCount();
            int remaining = totalFrequency - executionCount;
            if (remaining > 0) {
                xmlSerializer.startTag(NS_SX, TAG_REM_OCCUR);
                xmlSerializer.text(Integer.toString(remaining));
                xmlSerializer.endTag(NS_SX, TAG_REM_OCCUR);
            }
        }

        String tag = scheduledAction.getTag();
        if (!TextUtils.isEmpty(tag)) {
            xmlSerializer.startTag(NS_SX, TAG_TAG);
            xmlSerializer.text(tag);
            xmlSerializer.endTag(NS_SX, TAG_TAG);
        }

        xmlSerializer.startTag(NS_SX, TAG_TEMPLATE_ACCOUNT);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(accountUID);
        xmlSerializer.endTag(NS_SX, TAG_TEMPLATE_ACCOUNT);

        //// FIXME: 11.10.2015 Retrieve the information for this section from the recurrence table
        xmlSerializer.startTag(NS_SX, TAG_SCHEDULE);
        xmlSerializer.startTag(NS_GNUCASH, TAG_RECURRENCE);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION);
        writeRecurrence(xmlSerializer, scheduledAction.getRecurrence());
        xmlSerializer.endTag(NS_GNUCASH, TAG_RECURRENCE);
        xmlSerializer.endTag(NS_SX, TAG_SCHEDULE);

        xmlSerializer.endTag(NS_GNUCASH, TAG_SCHEDULED_ACTION);
    }

    /**
     * Serializes a date as a {@code tag} which has a nested {@link GncXmlHelper#TAG_GDATE} which
     * has the date as a text element formatted.
     *
     * @param xmlSerializer XML serializer
     * @param namespace     The tag namespace.
     * @param tag           Enclosing tag
     * @param timeMillis    Date to be formatted and output
     */
    private void writeDate(XmlSerializer xmlSerializer, String namespace, String tag, long timeMillis) throws IOException {
        xmlSerializer.startTag(namespace, tag);
        xmlSerializer.startTag(null, TAG_GDATE);
        xmlSerializer.text(formatDate(timeMillis));
        xmlSerializer.endTag(null, TAG_GDATE);
        xmlSerializer.endTag(namespace, tag);
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
        xmlSerializer.startTag(NS_GNUCASH, TAG_COMMODITY);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE);
        xmlSerializer.startTag(NS_COMMODITY, TAG_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(NS_COMMODITY, TAG_ID);
        if (!SOURCE_CURRENCY.equals(commodity.getQuoteSource())) {
            if (!TextUtils.isEmpty(commodity.getFullname()) && !commodity.isCurrency()) {
                xmlSerializer.startTag(NS_COMMODITY, TAG_NAME);
                xmlSerializer.text(commodity.getFullname());
                xmlSerializer.endTag(NS_COMMODITY, TAG_NAME);
            }
            String cusip = commodity.getCusip();
            if (!TextUtils.isEmpty(cusip)) {
                try {
                    // "exchange-code is stored in ISIN/CUSIP"
                    Integer.parseInt(cusip);
                } catch (NumberFormatException e) {
                    xmlSerializer.startTag(NS_COMMODITY, TAG_XCODE);
                    xmlSerializer.text(cusip);
                    xmlSerializer.endTag(NS_COMMODITY, TAG_XCODE);
                }
            }
            xmlSerializer.startTag(NS_COMMODITY, TAG_FRACTION);
            xmlSerializer.text(String.valueOf(commodity.getSmallestFraction()));
            xmlSerializer.endTag(NS_COMMODITY, TAG_FRACTION);
        }
        if (commodity.getQuoteFlag()) {
            xmlSerializer.startTag(NS_COMMODITY, TAG_GET_QUOTES);
            xmlSerializer.endTag(NS_COMMODITY, TAG_GET_QUOTES);
            xmlSerializer.startTag(NS_COMMODITY, TAG_QUOTE_SOURCE);
            xmlSerializer.text(commodity.getQuoteSource());
            xmlSerializer.endTag(NS_COMMODITY, TAG_QUOTE_SOURCE);
            TimeZone tz = commodity.getQuoteTimeZone();
            xmlSerializer.startTag(NS_COMMODITY, TAG_QUOTE_TZ);
            if (tz != null) {
                xmlSerializer.text(tz.getID());
            }
            xmlSerializer.endTag(NS_COMMODITY, TAG_QUOTE_TZ);
        }
        xmlSerializer.endTag(NS_GNUCASH, TAG_COMMODITY);
    }

    private void writePrices(XmlSerializer xmlSerializer) throws IOException {
        List<Price> prices = mPricesDbAdapter.getAllRecords();
        if (prices.isEmpty()) return;

        Timber.i("write prices");
        xmlSerializer.startTag(NS_GNUCASH, TAG_PRICEDB);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, "1");
        for (Price price : prices) {
            writePrice(xmlSerializer, price);
        }
        xmlSerializer.endTag(NS_GNUCASH, TAG_PRICEDB);
    }

    private void writePrice(XmlSerializer xmlSerializer, Price price) throws IOException {
        if (listener != null) listener.onPrice(price);
        xmlSerializer.startTag(null, TAG_PRICE);
        // GUID
        xmlSerializer.startTag(NS_PRICE, TAG_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(price.getUID());
        xmlSerializer.endTag(NS_PRICE, TAG_ID);
        // commodity
        Commodity commodity = price.getCommodity();
        xmlSerializer.startTag(NS_PRICE, TAG_COMMODITY);
        xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE);
        xmlSerializer.startTag(NS_COMMODITY, TAG_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(NS_COMMODITY, TAG_ID);
        xmlSerializer.endTag(NS_PRICE, TAG_COMMODITY);
        // currency
        Commodity currency = price.getCurrency();
        xmlSerializer.startTag(NS_PRICE, TAG_CURRENCY);
        xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE);
        xmlSerializer.text(currency.getNamespace());
        xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE);
        xmlSerializer.startTag(NS_COMMODITY, TAG_ID);
        xmlSerializer.text(currency.getCurrencyCode());
        xmlSerializer.endTag(NS_COMMODITY, TAG_ID);
        xmlSerializer.endTag(NS_PRICE, TAG_CURRENCY);
        // time
        xmlSerializer.startTag(NS_PRICE, TAG_TIME);
        xmlSerializer.startTag(NS_TS, TAG_DATE);
        xmlSerializer.text(formatDateTime(price.getDate()));
        xmlSerializer.endTag(NS_TS, TAG_DATE);
        xmlSerializer.endTag(NS_PRICE, TAG_TIME);
        // source
        if (!TextUtils.isEmpty(price.getSource())) {
            xmlSerializer.startTag(NS_PRICE, TAG_SOURCE);
            xmlSerializer.text(price.getSource());
            xmlSerializer.endTag(NS_PRICE, TAG_SOURCE);
        }
        // type, optional
        Price.Type type = price.getType();
        if (type != Price.Type.Unknown) {
            xmlSerializer.startTag(NS_PRICE, TAG_TYPE);
            xmlSerializer.text(type.getValue());
            xmlSerializer.endTag(NS_PRICE, TAG_TYPE);
        }
        // value
        xmlSerializer.startTag(NS_PRICE, TAG_VALUE);
        xmlSerializer.text(price.getValueNum() + "/" + price.getValueDenom());
        xmlSerializer.endTag(NS_PRICE, TAG_VALUE);
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
        xmlSerializer.startTag(NS_RECURRENCE, TAG_MULT);
        xmlSerializer.text(String.valueOf(recurrence.getMultiplier()));
        xmlSerializer.endTag(NS_RECURRENCE, TAG_MULT);
        xmlSerializer.startTag(NS_RECURRENCE, TAG_PERIOD_TYPE);
        xmlSerializer.text(periodType.value);
        xmlSerializer.endTag(NS_RECURRENCE, TAG_PERIOD_TYPE);

        long recurrenceStartTime = recurrence.getPeriodStart();
        writeDate(xmlSerializer, NS_RECURRENCE, TAG_START, recurrenceStartTime);

        WeekendAdjust weekendAdjust = recurrence.getWeekendAdjust();
        if (weekendAdjust != WeekendAdjust.NONE) {
            /* In r17725 and r17751, I introduced this extra XML child
            element, but this means a gnucash-2.2.x cannot read the SX
            recurrence of a >=2.3.x file anymore, which is bad. In order
            to improve this broken backward compatibility for most of the
            cases, we don't write out this XML element as long as it is
            only "none". */
            xmlSerializer.startTag(NS_RECURRENCE, GncXmlHelper.TAG_WEEKEND_ADJ);
            xmlSerializer.text(weekendAdjust.value);
            xmlSerializer.endTag(NS_RECURRENCE, GncXmlHelper.TAG_WEEKEND_ADJ);
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
        xmlSerializer.startTag(NS_GNUCASH, TAG_BUDGET);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // budget id
        xmlSerializer.startTag(NS_BUDGET, TAG_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(budget.getUID());
        xmlSerializer.endTag(NS_BUDGET, TAG_ID);
        // budget name
        xmlSerializer.startTag(NS_BUDGET, TAG_NAME);
        xmlSerializer.text(budget.getName());
        xmlSerializer.endTag(NS_BUDGET, TAG_NAME);
        // budget description
        String description = budget.getDescription();
        if (!TextUtils.isEmpty(description)) {
            xmlSerializer.startTag(NS_BUDGET, TAG_DESCRIPTION);
            xmlSerializer.text(description);
            xmlSerializer.endTag(NS_BUDGET, TAG_DESCRIPTION);
        }
        // budget periods
        xmlSerializer.startTag(NS_BUDGET, TAG_NUM_PERIODS);
        xmlSerializer.text(String.valueOf(budget.getNumberOfPeriods()));
        xmlSerializer.endTag(NS_BUDGET, TAG_NUM_PERIODS);
        // budget recurrence
        xmlSerializer.startTag(NS_BUDGET, TAG_RECURRENCE);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION);
        writeRecurrence(xmlSerializer, budget.getRecurrence());
        xmlSerializer.endTag(NS_BUDGET, TAG_RECURRENCE);

        // budget as slots
        xmlSerializer.startTag(NS_BUDGET, TAG_SLOTS);

        writeBudgetAmounts(xmlSerializer, budget);

        // Notes are grouped together.
        writeBudgetNotes(xmlSerializer, budget);

        xmlSerializer.endTag(NS_BUDGET, TAG_SLOTS);
        xmlSerializer.endTag(NS_GNUCASH, TAG_BUDGET);
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
            xmlSerializer.startTag(NS_SLOT, TAG_KEY);
            xmlSerializer.text(accountID);
            xmlSerializer.endTag(NS_SLOT, TAG_KEY);
            xmlSerializer.startTag(NS_SLOT, TAG_VALUE);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_FRAME);
            writeSlots(xmlSerializer, slots);
            xmlSerializer.endTag(NS_SLOT, TAG_VALUE);
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
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlSerializer xmlSerializer = factory.newSerializer();
            try {
                xmlSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            } catch (IllegalStateException ignore) {
                // Feature not supported. No problem
            }
            xmlSerializer.setOutput(writer);
            xmlSerializer.startDocument(StandardCharsets.UTF_8.name(), true);
            // root tag
            xmlSerializer.setPrefix(NS_ACCOUNT_PREFIX, NS_ACCOUNT);
            xmlSerializer.setPrefix(NS_BOOK_PREFIX, NS_BOOK);
            xmlSerializer.setPrefix(NS_GNUCASH_PREFIX, NS_GNUCASH);
            xmlSerializer.setPrefix(NS_CD_PREFIX, NS_CD);
            xmlSerializer.setPrefix(NS_COMMODITY_PREFIX, NS_COMMODITY);
            xmlSerializer.setPrefix(NS_PRICE_PREFIX, NS_PRICE);
            xmlSerializer.setPrefix(NS_SLOT_PREFIX, NS_SLOT);
            xmlSerializer.setPrefix(NS_SPLIT_PREFIX, NS_SPLIT);
            xmlSerializer.setPrefix(NS_SX_PREFIX, NS_SX);
            xmlSerializer.setPrefix(NS_TRANSACTION_PREFIX, NS_TRANSACTION);
            xmlSerializer.setPrefix(NS_TS_PREFIX, NS_TS);
            xmlSerializer.setPrefix(NS_FS_PREFIX, NS_FS);
            xmlSerializer.setPrefix(NS_BUDGET_PREFIX, NS_BUDGET);
            xmlSerializer.setPrefix(NS_RECURRENCE_PREFIX, NS_RECURRENCE);
            xmlSerializer.setPrefix(NS_LOT_PREFIX, NS_LOT);
            xmlSerializer.setPrefix(NS_ADDRESS_PREFIX, NS_ADDRESS);
            xmlSerializer.setPrefix(NS_BILLTERM_PREFIX, NS_BILLTERM);
            xmlSerializer.setPrefix(NS_BT_DAYS_PREFIX, NS_BT_DAYS);
            xmlSerializer.setPrefix(NS_BT_PROX_PREFIX, NS_BT_PROX);
            xmlSerializer.setPrefix(NS_CUSTOMER_PREFIX, NS_CUSTOMER);
            xmlSerializer.setPrefix(NS_EMPLOYEE_PREFIX, NS_EMPLOYEE);
            xmlSerializer.setPrefix(NS_ENTRY_PREFIX, NS_ENTRY);
            xmlSerializer.setPrefix(NS_INVOICE_PREFIX, NS_INVOICE);
            xmlSerializer.setPrefix(NS_JOB_PREFIX, NS_JOB);
            xmlSerializer.setPrefix(NS_ORDER_PREFIX, NS_ORDER);
            xmlSerializer.setPrefix(NS_OWNER_PREFIX, NS_OWNER);
            xmlSerializer.setPrefix(NS_TAXTABLE_PREFIX, NS_TAXTABLE);
            xmlSerializer.setPrefix(NS_TTE_PREFIX, NS_TTE);
            xmlSerializer.setPrefix(NS_VENDOR_PREFIX, NS_VENDOR);
            xmlSerializer.startTag(null, TAG_ROOT);
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
        xmlSerializer.startTag(NS_GNUCASH, TAG_BOOK);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // book id
        xmlSerializer.startTag(NS_BOOK, TAG_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(book.getUID());
        xmlSerializer.endTag(NS_BOOK, TAG_ID);
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

        xmlSerializer.endTag(NS_GNUCASH, TAG_BOOK);
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
        String where = AccountEntry.COLUMN_TYPE + "=? AND " + AccountEntry.COLUMN_TEMPLATE + "=1";
        String[] whereArgs = new String[]{AccountType.ROOT.name()};
        Commodity template = mCommoditiesDbAdapter.getCurrency(TEMPLATE);
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccounts(where, whereArgs, null);
        if (accounts.isEmpty()) {
            Commodity commodity = mCommoditiesDbAdapter.getCurrency(TEMPLATE);
            if (commodity != null) {
                where = AccountEntry.COLUMN_TYPE + "=? AND " + AccountEntry.COLUMN_COMMODITY_UID + "=?";
                whereArgs = new String[]{AccountType.ROOT.name(), commodity.getUID()};
            } else {
                where = AccountEntry.COLUMN_TYPE + "=? AND " + AccountEntry.COLUMN_NAME + "=?";
                whereArgs = new String[]{AccountType.ROOT.name(), TEMPLATE_ACCOUNT_NAME};
            }
            accounts = mAccountsDbAdapter.getSimpleAccounts(where, whereArgs, null);
            if (accounts.isEmpty()) {
                mRootTemplateAccount = account = new Account(TEMPLATE_ACCOUNT_NAME, Commodity.template);
                account.setAccountType(AccountType.ROOT);
                return account;
            }
        }
        mRootTemplateAccount = account = accounts.get(0);
        return account;
    }
}
