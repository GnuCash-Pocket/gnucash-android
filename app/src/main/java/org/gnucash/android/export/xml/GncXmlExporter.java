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

import static org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import static org.gnucash.android.export.xml.GncXmlHelper.*;
import static org.gnucash.android.model.Commodity.NO_CURRENCY_CODE;
import static org.gnucash.android.model.Commodity.TEMPLATE;

import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Price;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.model.WeekendAdjust;
import org.gnucash.android.util.TimestampHelper;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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
    private final RecurrenceDbAdapter mRecurrenceDbAdapter;

    /**
     * Overloaded constructor.
     * Creates an exporter with an already open database instance.
     *
     * @param context The context.
     * @param params  Parameters for the export
     * @param bookUID The book UID.
     */
    public GncXmlExporter(@NonNull Context context,
                          @NonNull ExportParams params,
                          @NonNull String bookUID) {
        super(context, params, bookUID);
        mRecurrenceDbAdapter = new RecurrenceDbAdapter(mDb);
    }

    private void exportSlots(XmlSerializer xmlSerializer,
                             List<String> slotKey,
                             List<String> slotType,
                             List<String> slotValue) throws IOException {
        if (slotKey == null || slotType == null || slotValue == null ||
            slotKey.isEmpty() || slotType.size() != slotKey.size() || slotValue.size() != slotKey.size()) {
            return;
        }

        for (int i = 0; i < slotKey.size(); i++) {
            xmlSerializer.startTag(null, TAG_SLOT);
            xmlSerializer.startTag(null, TAG_SLOT_KEY);
            xmlSerializer.text(slotKey.get(i));
            xmlSerializer.endTag(null, TAG_SLOT_KEY);
            xmlSerializer.startTag(null, TAG_SLOT_VALUE);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, slotType.get(i));
            xmlSerializer.text(slotValue.get(i));
            xmlSerializer.endTag(null, TAG_SLOT_VALUE);
            xmlSerializer.endTag(null, TAG_SLOT);
        }
    }

    private void exportAccounts(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("export accounts");
        // gnucash desktop requires that parent account appears before its descendants.
        // sort by full-name to fulfill the request
        Cursor cursor = mAccountsDbAdapter.fetchAccounts(null, null, DatabaseSchema.AccountEntry.COLUMN_FULL_NAME + " ASC");
        while (cursor.moveToNext()) {
            Account account = mAccountsDbAdapter.buildSimpleAccountInstance(cursor);
            exportAccount(xmlSerializer, account);
        }
        cursor.close();
    }

    private void exportAccount(XmlSerializer xmlSerializer, Account account) throws IOException {
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
        String acct_type = account.getAccountType().name();
        xmlSerializer.text(acct_type);
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
        ArrayList<String> slotKey = new ArrayList<>();
        ArrayList<String> slotType = new ArrayList<>();
        ArrayList<String> slotValue = new ArrayList<>();
        slotKey.add(KEY_PLACEHOLDER);
        slotType.add(ATTR_VALUE_STRING);
        slotValue.add(Boolean.toString(account.isPlaceholderAccount()));

        String color = account.getColorHexString();
        if (!TextUtils.isEmpty(color)) {
            slotKey.add(KEY_COLOR);
            slotType.add(ATTR_VALUE_STRING);
            slotValue.add(color);
        }

        String defaultTransferAcctUID = account.getDefaultTransferAccountUID();
        if (!TextUtils.isEmpty(defaultTransferAcctUID)) {
            slotKey.add(KEY_DEFAULT_TRANSFER_ACCOUNT);
            slotType.add(ATTR_VALUE_STRING);
            slotValue.add(defaultTransferAcctUID);
        }

        slotKey.add(KEY_FAVORITE);
        slotType.add(ATTR_VALUE_STRING);
        slotValue.add(Boolean.toString(account.isFavorite()));

        xmlSerializer.startTag(null, TAG_ACCT_SLOTS);
        exportSlots(xmlSerializer, slotKey, slotType, slotValue);
        xmlSerializer.endTag(null, TAG_ACCT_SLOTS);

        // parent uid
        String parentUID = account.getParentUID();
        if (!acct_type.equals("ROOT") && !TextUtils.isEmpty(parentUID)) {
            xmlSerializer.startTag(null, TAG_ACCT_PARENT);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            xmlSerializer.text(parentUID);
            xmlSerializer.endTag(null, TAG_ACCT_PARENT);
        } else {
            Timber.d("root account : %s", account.getUID());
        }
        xmlSerializer.endTag(null, TAG_ACCOUNT);
    }

    /**
     * Exports template accounts
     * <p>Template accounts are just dummy accounts created for use with template transactions</p>
     *
     * @param xmlSerializer XML serializer
     * @param accounts      List of template accounts
     * @throws IOException if could not write XML to output stream
     */
    private void exportTemplateAccounts(XmlSerializer xmlSerializer, Collection<Account> accounts) throws IOException {
        Commodity commodity = new Commodity(TEMPLATE, TEMPLATE, 1);
        commodity.setNamespace(TEMPLATE);
        commodity.setLocalSymbol(TEMPLATE);
        commodity.setCusip(TEMPLATE);

        for (Account account : accounts) {
            account.setCommodity(commodity);
            exportAccount(xmlSerializer, account);
        }
    }

    /**
     * Serializes transactions from the database to XML
     *
     * @param xmlSerializer   XML serializer
     * @param exportTemplates Flag whether to export templates or normal transactions
     * @throws IOException if the XML serializer cannot be written to
     */
    private void exportTransactions(XmlSerializer xmlSerializer, boolean exportTemplates) throws IOException {
        Timber.i("export transactions");
        String where = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + "=0";
        if (exportTemplates) {
            where = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + "=1";
        }
        final Cursor cursor = mTransactionsDbAdapter.fetchTransactionsWithSplits(
            new String[]{
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " AS trans_uid",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " AS trans_desc",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_NOTES + " AS trans_notes",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " AS trans_time",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_EXPORTED + " AS trans_exported",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_CURRENCY + " AS trans_currency",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_CREATED_AT + " AS trans_date_posted",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + " AS trans_from_sched_action",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_UID + " AS split_uid",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_MEMO + " AS split_memo",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TYPE + " AS split_type",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_NUM + " AS split_value_num",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_DENOM + " AS split_value_denom",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_NUM + " AS split_quantity_num",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_DENOM + " AS split_quantity_denom", SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " AS split_acct_uid"},
            where, null,
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " ASC , " +
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " ASC ");
        String lastTrxUID = "";
        Commodity trnCommodity = null;

        if (exportTemplates) {
            mRootTemplateAccount = new Account("Template Root");
            mRootTemplateAccount.setAccountType(AccountType.ROOT);
            mTransactionToTemplateAccountMap.put(" ", mRootTemplateAccount);

            //FIXME: Retrieve the template account GUIDs from the scheduled action table and create accounts with that
            //this will allow use to maintain the template account GUID when we import from the desktop and also use the same for the splits
            while (cursor.moveToNext()) {
                Account account = new Account(BaseModel.generateUID());
                account.setAccountType(AccountType.BANK);
                String trnUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"));
                mTransactionToTemplateAccountMap.put(trnUID, account);
            }

            exportTemplateAccounts(xmlSerializer, mTransactionToTemplateAccountMap.values());
            //push cursor back to before the beginning
            cursor.moveToFirst();
            cursor.moveToPrevious();
        }

        //// FIXME: 12.10.2015 export split reconciled_state and reconciled_date to the export
        while (cursor.moveToNext()) {
            String curTrxUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"));
            if (!lastTrxUID.equals(curTrxUID)) { // new transaction starts
                if (!TextUtils.isEmpty(lastTrxUID)) { // there's an old transaction, close it
                    xmlSerializer.endTag(null, TAG_TRN_SPLITS);
                    xmlSerializer.endTag(null, TAG_TRANSACTION);
                }
                // new transaction
                xmlSerializer.startTag(null, TAG_TRANSACTION);
                xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
                // transaction id
                xmlSerializer.startTag(null, TAG_TRX_ID);
                xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
                xmlSerializer.text(curTrxUID);
                xmlSerializer.endTag(null, TAG_TRX_ID);
                // currency
                String currencyCode = cursor.getString(cursor.getColumnIndexOrThrow("trans_currency"));
                trnCommodity = CommoditiesDbAdapter.getInstance().getCommodity(currencyCode);//Currency.getInstance(currencyCode);
                xmlSerializer.startTag(null, TAG_TRX_CURRENCY);
                xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
                xmlSerializer.text(COMMODITY_CURRENCY);
                xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
                xmlSerializer.startTag(null, TAG_COMMODITY_ID);
                xmlSerializer.text(currencyCode);
                xmlSerializer.endTag(null, TAG_COMMODITY_ID);
                xmlSerializer.endTag(null, TAG_TRX_CURRENCY);
                // date posted, time which user put on the transaction
                String strDate = formatDateTime(cursor.getLong(cursor.getColumnIndexOrThrow("trans_time")));
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
                xmlSerializer.text(cursor.getString(cursor.getColumnIndexOrThrow("trans_desc")));
                xmlSerializer.endTag(null, TAG_TRN_DESCRIPTION);
                lastTrxUID = curTrxUID;
                // slots
                ArrayList<String> slotKey = new ArrayList<>();
                ArrayList<String> slotType = new ArrayList<>();
                ArrayList<String> slotValue = new ArrayList<>();

                String notes = cursor.getString(cursor.getColumnIndexOrThrow("trans_notes"));
                if (!TextUtils.isEmpty(notes)) {
                    slotKey.add(KEY_NOTES);
                    slotType.add(ATTR_VALUE_STRING);
                    slotValue.add(notes);
                }

                String scheduledActionUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_from_sched_action"));
                if (!TextUtils.isEmpty(scheduledActionUID)) {
                    slotKey.add(KEY_FROM_SCHED_ACTION);
                    slotType.add(ATTR_VALUE_GUID);
                    slotValue.add(scheduledActionUID);
                }
                xmlSerializer.startTag(null, TAG_TRN_SLOTS);
                exportSlots(xmlSerializer, slotKey, slotType, slotValue);
                xmlSerializer.endTag(null, TAG_TRN_SLOTS);

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
            String trxType = cursor.getString(cursor.getColumnIndexOrThrow("split_type"));
            int splitValueNum = cursor.getInt(cursor.getColumnIndexOrThrow("split_value_num"));
            int splitValueDenom = cursor.getInt(cursor.getColumnIndexOrThrow("split_value_denom"));
            BigDecimal splitAmount = Money.getBigDecimal(splitValueNum, splitValueDenom);
            String strValue = "0/100";
            if (!exportTemplates) { //when doing normal transaction export
                strValue = (trxType.equals("CREDIT") ? "-" : "") + splitValueNum + "/" + splitValueDenom;
            }
            xmlSerializer.startTag(null, TAG_SPLIT_VALUE);
            xmlSerializer.text(strValue);
            xmlSerializer.endTag(null, TAG_SPLIT_VALUE);
            // quantity, in the split account's currency
            String splitQuantityNum = cursor.getString(cursor.getColumnIndexOrThrow("split_quantity_num"));
            String splitQuantityDenom = cursor.getString(cursor.getColumnIndexOrThrow("split_quantity_denom"));
            if (!exportTemplates) {
                strValue = (trxType.equals("CREDIT") ? "-" : "") + splitQuantityNum + "/" + splitQuantityDenom;
            }
            xmlSerializer.startTag(null, TAG_SPLIT_QUANTITY);
            xmlSerializer.text(strValue);
            xmlSerializer.endTag(null, TAG_SPLIT_QUANTITY);
            // account guid
            xmlSerializer.startTag(null, TAG_SPLIT_ACCOUNT);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            String splitAccountUID;
            if (exportTemplates) {
                //get the UID of the template account
                splitAccountUID = mTransactionToTemplateAccountMap.get(curTrxUID).getUID();
            } else {
                splitAccountUID = cursor.getString(cursor.getColumnIndexOrThrow("split_acct_uid"));
            }
            xmlSerializer.text(splitAccountUID);
            xmlSerializer.endTag(null, TAG_SPLIT_ACCOUNT);

            //if we are exporting a template transaction, then we need to add some extra slots
            // TODO be able to import `KEY_SCHED_XACTION` slots.
            if (exportTemplates) {
                xmlSerializer.startTag(null, TAG_SPLIT_SLOTS);
                xmlSerializer.startTag(null, TAG_SLOT);
                xmlSerializer.startTag(null, TAG_SLOT_KEY);
                xmlSerializer.text(KEY_SCHED_XACTION); //FIXME: not all templates may be scheduled actions
                xmlSerializer.endTag(null, TAG_SLOT_KEY);
                xmlSerializer.startTag(null, TAG_SLOT_VALUE);
                xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_FRAME);

                List<String> slotKeys = new ArrayList<>();
                List<String> slotTypes = new ArrayList<>();
                List<String> slotValues = new ArrayList<>();
                slotKeys.add(KEY_SPLIT_ACCOUNT_SLOT);
                slotTypes.add(ATTR_VALUE_GUID);
                slotValues.add(cursor.getString(cursor.getColumnIndexOrThrow("split_acct_uid")));
                TransactionType type = TransactionType.valueOf(trxType);
                if (type == TransactionType.CREDIT) {
                    slotKeys.add(KEY_CREDIT_FORMULA);
                    slotTypes.add(ATTR_VALUE_STRING);
                    slotValues.add(formatTemplateSplitAmount(splitAmount));
                    slotKeys.add(KEY_CREDIT_NUMERIC);
                    slotTypes.add(ATTR_VALUE_NUMERIC);
                    slotValues.add(formatSplitAmount(splitAmount, trnCommodity));
                } else {
                    slotKeys.add(KEY_DEBIT_FORMULA);
                    slotTypes.add(ATTR_VALUE_STRING);
                    slotValues.add(formatTemplateSplitAmount(splitAmount));
                    slotKeys.add(KEY_DEBIT_NUMERIC);
                    slotTypes.add(ATTR_VALUE_NUMERIC);
                    slotValues.add(formatSplitAmount(splitAmount, trnCommodity));
                }

                exportSlots(xmlSerializer, slotKeys, slotTypes, slotValues);

                xmlSerializer.endTag(null, TAG_SLOT_VALUE);
                xmlSerializer.endTag(null, TAG_SLOT);
                xmlSerializer.endTag(null, TAG_SPLIT_SLOTS);
            }

            xmlSerializer.endTag(null, TAG_TRN_SPLIT);
        }
        if (!TextUtils.isEmpty(lastTrxUID)) { // there's an unfinished transaction, close it
            xmlSerializer.endTag(null, TAG_TRN_SPLITS);
            xmlSerializer.endTag(null, TAG_TRANSACTION);
        }
        cursor.close();
    }

    /**
     * Serializes {@link ScheduledAction}s from the database to XML
     *
     * @param xmlSerializer XML serializer
     * @throws IOException
     */
    private void exportScheduledTransactions(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("export scheduled transactions");
        //for now we will export only scheduled transactions to XML
        Cursor cursor = mScheduledActionDbAdapter.fetchAllRecords(
            ScheduledActionEntry.COLUMN_TYPE + "=?", new String[]{ScheduledAction.ActionType.TRANSACTION.name()}, null);

        while (cursor.moveToNext()) {
            ScheduledAction scheduledAction = mScheduledActionDbAdapter.buildModelInstance(cursor);
            String actionUID = scheduledAction.getActionUID();
            Account accountUID = mTransactionToTemplateAccountMap.get(actionUID);

            if (accountUID == null) //if the action UID does not belong to a transaction we've seen before, skip it
                continue;

            xmlSerializer.startTag(null, TAG_SCHEDULED_ACTION);
            xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
            xmlSerializer.startTag(null, TAG_SX_ID);

            String nameUID = accountUID.getName();
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            xmlSerializer.text(nameUID);
            xmlSerializer.endTag(null, TAG_SX_ID);
            xmlSerializer.startTag(null, TAG_SX_NAME);

            ScheduledAction.ActionType actionType = scheduledAction.getActionType();
            if (actionType == ScheduledAction.ActionType.TRANSACTION) {
                String description = mTransactionsDbAdapter.getAttribute(actionUID, TransactionEntry.COLUMN_DESCRIPTION);
                xmlSerializer.text(description);
            } else {
                xmlSerializer.text(actionType.name());
            }
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
            String scheduledActionUID = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_UID));
            long instanceCount = mScheduledActionDbAdapter.getActionInstanceCount(scheduledActionUID);
            xmlSerializer.text(Long.toString(instanceCount));
            xmlSerializer.endTag(null, TAG_SX_INSTANCE_COUNT);

            //start date
            String createdTimestamp = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_CREATED_AT));
            long scheduleStartTime = TimestampHelper.getTimestampFromUtcString(createdTimestamp).getTime();
            serializeDate(xmlSerializer, TAG_SX_START, scheduleStartTime);

            long lastRunTime = cursor.getLong(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_LAST_RUN));
            if (lastRunTime > 0) {
                serializeDate(xmlSerializer, TAG_SX_LAST, lastRunTime);
            }

            long endTime = cursor.getLong(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_END_TIME));
            if (endTime > 0) {
                //end date
                serializeDate(xmlSerializer, TAG_SX_END, endTime);
            } else { //add number of occurrences
                int totalFrequency = cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY));
                xmlSerializer.startTag(null, TAG_SX_NUM_OCCUR);
                xmlSerializer.text(Integer.toString(totalFrequency));
                xmlSerializer.endTag(null, TAG_SX_NUM_OCCUR);

                //remaining occurrences
                int executionCount = cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_EXECUTION_COUNT));
                xmlSerializer.startTag(null, TAG_SX_REM_OCCUR);
                xmlSerializer.text(Integer.toString(totalFrequency - executionCount));
                xmlSerializer.endTag(null, TAG_SX_REM_OCCUR);
            }

            String tag = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TAG));
            if (!TextUtils.isEmpty(tag)) {
                xmlSerializer.startTag(null, TAG_SX_TAG);
                xmlSerializer.text(tag);
                xmlSerializer.endTag(null, TAG_SX_TAG);
            }

            xmlSerializer.startTag(null, TAG_SX_TEMPL_ACCOUNT);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            xmlSerializer.text(accountUID.getUID());
            xmlSerializer.endTag(null, TAG_SX_TEMPL_ACCOUNT);

            //// FIXME: 11.10.2015 Retrieve the information for this section from the recurrence table
            xmlSerializer.startTag(null, TAG_SX_SCHEDULE);
            xmlSerializer.startTag(null, TAG_GNC_RECURRENCE);
            xmlSerializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION);

            String recurrenceUID = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_RECURRENCE_UID));
            Recurrence recurrence = mRecurrenceDbAdapter.getRecord(recurrenceUID);
            exportRecurrence(xmlSerializer, recurrence);
            xmlSerializer.endTag(null, TAG_GNC_RECURRENCE);
            xmlSerializer.endTag(null, TAG_SX_SCHEDULE);

            xmlSerializer.endTag(null, TAG_SCHEDULED_ACTION);
        }
        cursor.close();
    }

    /**
     * Serializes a date as a {@code tag} which has a nested {@link GncXmlHelper#TAG_GDATE} which
     * has the date as a text element formatted using {@link GncXmlHelper#DATE_FORMATTER}
     *
     * @param xmlSerializer XML serializer
     * @param tag           Enclosing tag
     * @param timeMillis    Date to be formatted and output
     * @throws IOException
     */
    private void serializeDate(XmlSerializer xmlSerializer, String tag, long timeMillis) throws IOException {
        xmlSerializer.startTag(null, tag);
        xmlSerializer.startTag(null, TAG_GDATE);
        xmlSerializer.text(formatDateTime(timeMillis));
        xmlSerializer.endTag(null, TAG_GDATE);
        xmlSerializer.endTag(null, tag);
    }

    private void exportCommodities(XmlSerializer xmlSerializer, List<Commodity> commodities) throws IOException {
        Timber.i("export commodities");
        for (Commodity commodity : commodities) {
            exportCommodity(xmlSerializer, commodity);
        }
    }

    private void exportCommodity(XmlSerializer xmlSerializer, Commodity commodity) throws IOException {
        xmlSerializer.startTag(null, TAG_COMMODITY);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        if (commodity.getFullname() != null) {
            xmlSerializer.startTag(null, TAG_COMMODITY_NAME);
            xmlSerializer.text(commodity.getFullname());
            xmlSerializer.endTag(null, TAG_COMMODITY_NAME);
        }
        xmlSerializer.startTag(null, TAG_COMMODITY_FRACTION);
        xmlSerializer.text(String.valueOf(commodity.getSmallestFraction()));
        xmlSerializer.endTag(null, TAG_COMMODITY_FRACTION);
        if (commodity.getCusip() != null) {
            xmlSerializer.startTag(null, TAG_COMMODITY_XCODE);
            xmlSerializer.text(commodity.getCusip());
            xmlSerializer.endTag(null, TAG_COMMODITY_XCODE);
        }
        if (commodity.getQuoteFlag()) {
            xmlSerializer.startTag(null, TAG_COMMODITY_GET_QUOTES);
            xmlSerializer.endTag(null, TAG_COMMODITY_GET_QUOTES);
            xmlSerializer.startTag(null, TAG_COMMODITY_QUOTE_SOURCE);
            xmlSerializer.text(commodity.getQuoteSource());
            xmlSerializer.endTag(null, TAG_COMMODITY_QUOTE_SOURCE);
            TimeZone tz = commodity.getQuoteTimeZone();
            if (tz != null) {
                xmlSerializer.startTag(null, TAG_COMMODITY_QUOTE_TZ);
                xmlSerializer.text(tz.getID());
                xmlSerializer.endTag(null, TAG_COMMODITY_QUOTE_TZ);
            }
        }
        xmlSerializer.endTag(null, TAG_COMMODITY);
    }

    private void exportPrices(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("export prices");
        xmlSerializer.startTag(null, TAG_PRICEDB);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, "1");
        List<Price> prices = mPricesDbAdapter.getAllRecords();
        for (Price price : prices) {
            exportPrice(xmlSerializer, price);
        }
        xmlSerializer.endTag(null, TAG_PRICEDB);
    }

    private void exportPrice(XmlSerializer xmlSerializer, Price price) throws IOException {
        xmlSerializer.startTag(null, TAG_PRICE);
        // GUID
        xmlSerializer.startTag(null, TAG_PRICE_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(price.getUID());
        xmlSerializer.endTag(null, TAG_PRICE_ID);
        // commodity
        String commodityUID = price.getCommodityUID();
        Commodity commodity = mCommoditiesDbAdapter.getRecord(commodityUID);
        xmlSerializer.startTag(null, TAG_PRICE_COMMODITY);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        xmlSerializer.endTag(null, TAG_PRICE_COMMODITY);
        // currency
        String currencyId = price.getCurrencyUID();
        Commodity currency = mCommoditiesDbAdapter.getRecord(currencyId);
        xmlSerializer.startTag(null, TAG_PRICE_CURRENCY);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(currency.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(currency.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        xmlSerializer.endTag(null, TAG_PRICE_CURRENCY);
        // time
        String strDate = formatDateTime(price.getDate());
        xmlSerializer.startTag(null, TAG_PRICE_TIME);
        xmlSerializer.startTag(null, TAG_TS_DATE);
        xmlSerializer.text(strDate);
        xmlSerializer.endTag(null, TAG_TS_DATE);
        xmlSerializer.endTag(null, TAG_PRICE_TIME);
        // source
        xmlSerializer.startTag(null, TAG_PRICE_SOURCE);
        xmlSerializer.text(price.getSource());
        xmlSerializer.endTag(null, TAG_PRICE_SOURCE);
        // type, optional
        String type = price.getType();
        if (!TextUtils.isEmpty(type)) {
            xmlSerializer.startTag(null, TAG_PRICE_TYPE);
            xmlSerializer.text(type);
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
    private void exportRecurrence(XmlSerializer xmlSerializer, Recurrence recurrence) throws IOException {
        PeriodType periodType = recurrence.getPeriodType();
        xmlSerializer.startTag(null, TAG_RX_MULT);
        xmlSerializer.text(String.valueOf(recurrence.getMultiplier()));
        xmlSerializer.endTag(null, TAG_RX_MULT);
        xmlSerializer.startTag(null, TAG_RX_PERIOD_TYPE);
        xmlSerializer.text(periodType.value);
        xmlSerializer.endTag(null, TAG_RX_PERIOD_TYPE);

        long recurrenceStartTime = recurrence.getPeriodStart();
        serializeDate(xmlSerializer, TAG_RX_START, recurrenceStartTime);

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

    private void exportBudgets(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("export budgets");
        Cursor cursor = mBudgetsDbAdapter.fetchAllRecords();
        while (cursor.moveToNext()) {
            Budget budget = mBudgetsDbAdapter.buildModelInstance(cursor);
            exportBudget(xmlSerializer, budget);
        }
        cursor.close();
    }

    private void exportBudget(XmlSerializer xmlSerializer, Budget budget) throws IOException {
        xmlSerializer.startTag(null, TAG_BUDGET);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        xmlSerializer.startTag(null, TAG_BUDGET_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(budget.getUID());
        xmlSerializer.endTag(null, TAG_BUDGET_ID);
        xmlSerializer.startTag(null, TAG_BUDGET_NAME);
        xmlSerializer.text(budget.getName());
        xmlSerializer.endTag(null, TAG_BUDGET_NAME);
        xmlSerializer.startTag(null, TAG_BUDGET_DESCRIPTION);
        xmlSerializer.text(budget.getDescription() == null ? "" : budget.getDescription());
        xmlSerializer.endTag(null, TAG_BUDGET_DESCRIPTION);
        xmlSerializer.startTag(null, TAG_BUDGET_NUM_PERIODS);
        xmlSerializer.text(String.valueOf(budget.getNumberOfPeriods()));
        xmlSerializer.endTag(null, TAG_BUDGET_NUM_PERIODS);
        xmlSerializer.startTag(null, TAG_BUDGET_RECURRENCE);
        exportRecurrence(xmlSerializer, budget.getRecurrence());
        xmlSerializer.endTag(null, TAG_BUDGET_RECURRENCE);

        //export budget slots
        List<String> slotKey = new ArrayList<>();
        List<String> slotType = new ArrayList<>();
        List<String> slotValue = new ArrayList<>();

        xmlSerializer.startTag(null, TAG_BUDGET_SLOTS);
        for (BudgetAmount budgetAmount : budget.getExpandedBudgetAmounts()) {
            xmlSerializer.startTag(null, TAG_SLOT);
            xmlSerializer.startTag(null, TAG_SLOT_KEY);
            xmlSerializer.text(budgetAmount.getAccountUID());
            xmlSerializer.endTag(null, TAG_SLOT_KEY);

            Money amount = budgetAmount.getAmount();
            slotKey.clear();
            slotType.clear();
            slotValue.clear();
            for (int period = 0; period < budget.getNumberOfPeriods(); period++) {
                slotKey.add(String.valueOf(period));
                slotType.add(ATTR_VALUE_NUMERIC);
                slotValue.add(amount.getNumerator() + "/" + amount.getDenominator());
            }
            //budget slots

            xmlSerializer.startTag(null, TAG_SLOT_VALUE);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_FRAME);
            exportSlots(xmlSerializer, slotKey, slotType, slotValue);
            xmlSerializer.endTag(null, TAG_SLOT_VALUE);
            xmlSerializer.endTag(null, TAG_SLOT);
        }

        xmlSerializer.endTag(null, TAG_BUDGET_SLOTS);
        xmlSerializer.endTag(null, TAG_BUDGET);
    }

    @Override
    public List<String> generateExport() throws ExporterException {
        OutputStreamWriter writer = null;
        String outputFile = getExportCacheFilePath();
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            writer = new OutputStreamWriter(bufferedOutputStream);

            generateExport(writer);
            close();
        } catch (IOException ex) {
            Timber.e(ex, "Error exporting XML");
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new ExporterException(mExportParams, e);
                }
            }
        }

        List<String> exportedFiles = new ArrayList<>();
        exportedFiles.add(outputFile);

        return exportedFiles;
    }

    /**
     * Generates an XML export of the database and writes it to the {@code writer} output stream
     *
     * @param writer Output stream
     * @throws ExporterException
     */
    public void generateExport(Writer writer) throws ExporterException {
        Timber.i("generate export");
        final long timeStart = SystemClock.elapsedRealtime();
        try {
            String[] namespaces = new String[]{"gnc", "act", "book", "cd", "cmdty", "price", "slot",
                "split", "trn", "ts", "sx", "bgt", "recurrence"};
            XmlSerializer xmlSerializer = XmlPullParserFactory.newInstance().newSerializer();
            try {
                xmlSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            } catch (IllegalStateException e) {
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
            xmlSerializer.startTag(null, TAG_COUNT_DATA);
            xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, ATTR_VALUE_BOOK);
            xmlSerializer.text("1");
            xmlSerializer.endTag(null, TAG_COUNT_DATA);
            // book
            xmlSerializer.startTag(null, TAG_BOOK);
            xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
            // book_id
            xmlSerializer.startTag(null, TAG_BOOK_ID);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            xmlSerializer.text(BaseModel.generateUID());
            xmlSerializer.endTag(null, TAG_BOOK_ID);
            //commodity count
            List<Commodity> commodities = mAccountsDbAdapter.getCommoditiesInUse();
            for (int i = 0; i < commodities.size(); i++) {
                if (commodities.get(i).getCurrencyCode().equals(NO_CURRENCY_CODE)) {
                    commodities.remove(i);
                }
            }
            xmlSerializer.startTag(null, TAG_COUNT_DATA);
            xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, "commodity");
            xmlSerializer.text(String.valueOf(commodities.size()));
            xmlSerializer.endTag(null, TAG_COUNT_DATA);
            //account count
            xmlSerializer.startTag(null, TAG_COUNT_DATA);
            xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, "account");
            xmlSerializer.text(String.valueOf(mAccountsDbAdapter.getRecordsCount()));
            xmlSerializer.endTag(null, TAG_COUNT_DATA);
            //transaction count
            xmlSerializer.startTag(null, TAG_COUNT_DATA);
            xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, "transaction");
            xmlSerializer.text(String.valueOf(mTransactionsDbAdapter.getRecordsCount()));
            xmlSerializer.endTag(null, TAG_COUNT_DATA);
            //price count
            long priceCount = mPricesDbAdapter.getRecordsCount();
            if (priceCount > 0) {
                xmlSerializer.startTag(null, TAG_COUNT_DATA);
                xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, "price");
                xmlSerializer.text(String.valueOf(priceCount));
                xmlSerializer.endTag(null, TAG_COUNT_DATA);
            }
            // export the commodities used in the DB
            exportCommodities(xmlSerializer, commodities);
            // prices
            if (priceCount > 0) {
                exportPrices(xmlSerializer);
            }
            // accounts.
            exportAccounts(xmlSerializer);
            // transactions.
            exportTransactions(xmlSerializer, false);

            //transaction templates
            if (mTransactionsDbAdapter.getTemplateTransactionsCount() > 0) {
                xmlSerializer.startTag(null, TAG_TEMPLATE_TRANSACTIONS);
                exportTransactions(xmlSerializer, true);
                xmlSerializer.endTag(null, TAG_TEMPLATE_TRANSACTIONS);
            }
            //scheduled actions
            exportScheduledTransactions(xmlSerializer);

            //budgets
            exportBudgets(xmlSerializer);

            xmlSerializer.endTag(null, TAG_BOOK);
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

    /**
     * Returns the MIME type for this exporter.
     *
     * @return MIME type as string
     */
    @NonNull
    public String getExportMimeType() {
        return "text/xml";
    }

}
