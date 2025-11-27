/*
 * Copyright (c) 2012-2024 GnuCash Android Developers
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
package org.gnucash.android.export.ofx

import android.content.Context
import androidx.preference.PreferenceManager
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.ALWAYS
import org.gnucash.android.db.forEach
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.export.ofx.OfxHelper.APP_ID
import org.gnucash.android.export.ofx.OfxHelper.OFX_HEADER
import org.gnucash.android.export.ofx.OfxHelper.OFX_SGML_HEADER
import org.gnucash.android.export.ofx.OfxHelper.TAG_ACCOUNT_ID
import org.gnucash.android.export.ofx.OfxHelper.TAG_ACCOUNT_TYPE
import org.gnucash.android.export.ofx.OfxHelper.TAG_BALANCE_AMOUNT
import org.gnucash.android.export.ofx.OfxHelper.TAG_BANK_ACCOUNT_FROM
import org.gnucash.android.export.ofx.OfxHelper.TAG_BANK_ACCOUNT_TO
import org.gnucash.android.export.ofx.OfxHelper.TAG_BANK_ID
import org.gnucash.android.export.ofx.OfxHelper.TAG_BANK_MESSAGES_V1
import org.gnucash.android.export.ofx.OfxHelper.TAG_BANK_TRANSACTION_LIST
import org.gnucash.android.export.ofx.OfxHelper.TAG_CHECK_NUMBER
import org.gnucash.android.export.ofx.OfxHelper.TAG_CURRENCY_DEF
import org.gnucash.android.export.ofx.OfxHelper.TAG_DATE_AS_OF
import org.gnucash.android.export.ofx.OfxHelper.TAG_DATE_END
import org.gnucash.android.export.ofx.OfxHelper.TAG_DATE_POSTED
import org.gnucash.android.export.ofx.OfxHelper.TAG_DATE_START
import org.gnucash.android.export.ofx.OfxHelper.TAG_DATE_USER
import org.gnucash.android.export.ofx.OfxHelper.TAG_LEDGER_BALANCE
import org.gnucash.android.export.ofx.OfxHelper.TAG_MEMO
import org.gnucash.android.export.ofx.OfxHelper.TAG_NAME
import org.gnucash.android.export.ofx.OfxHelper.TAG_ROOT
import org.gnucash.android.export.ofx.OfxHelper.TAG_STATEMENT_TRANSACTION
import org.gnucash.android.export.ofx.OfxHelper.TAG_STATEMENT_TRANSACTIONS
import org.gnucash.android.export.ofx.OfxHelper.TAG_STATEMENT_TRANSACTION_RESPONSE
import org.gnucash.android.export.ofx.OfxHelper.TAG_TRANSACTION_AMOUNT
import org.gnucash.android.export.ofx.OfxHelper.TAG_TRANSACTION_FITID
import org.gnucash.android.export.ofx.OfxHelper.TAG_TRANSACTION_TYPE
import org.gnucash.android.export.ofx.OfxHelper.TAG_TRANSACTION_UID
import org.gnucash.android.export.ofx.OfxHelper.UNSOLICITED_TRANSACTION_ID
import org.gnucash.android.export.ofx.OfxHelper.formatTime
import org.gnucash.android.export.ofx.OfxHelper.formattedCurrentTime
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.model.Account
import org.gnucash.android.model.Money
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.PreferencesHelper.setLastExportTime
import org.gnucash.android.util.TimestampHelper
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.sql.Timestamp

/**
 * Exports the data in the database in OFX format.
 *
 * @author Ngewi Fet <ngewi.fet@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
class OfxExporter(
    context: Context,
    params: ExportParams,
    bookUID: String,
    listener: GncProgressListener? = null
) : Exporter(context, params, bookUID, listener) {

    override fun writeExport(writer: Writer, exportParams: ExportParams) {
        val accounts = accountsDbAdapter.getExportableAccounts(exportParams.exportStartTime)
        if (accounts.isEmpty()) {
            throw ExporterException(exportParams, "No accounts to export")
        }
        writeDocument(writer, exportParams, accounts)
        setLastExportTime(context, TimestampHelper.timestampFromNow, bookUID)
    }

    /**
     * Generate OFX export file from the transactions in the database.
     *
     * @param accounts List of accounts to export.
     * @throws ExporterException if an XML builder could not be created.
     */
    @Throws(ExporterException::class)
    private fun writeDocument(writer: Writer, exportParams: ExportParams, accounts: List<Account>) {
        val useXmlHeader = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.key_xml_ofx_header), true)

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val xmlSerializer = factory.newSerializer()
        try {
            xmlSerializer.setFeature(
                "http://xmlpull.org/v1/doc/features.html#indent-output",
                true
            )
        } catch (_: IllegalStateException) {
            // Feature not supported. No problem
        }
        xmlSerializer.setOutput(writer)
        if (useXmlHeader) {
            xmlSerializer.startDocument(StandardCharsets.UTF_8.name(), true)
            writer.write("\n")
            xmlSerializer.processingInstruction(OFX_HEADER)
        } else {
            writer.write(OFX_SGML_HEADER)
        }
        xmlSerializer.startTag(null, TAG_ROOT)
        writeAccounts(xmlSerializer, exportParams, accounts)
        xmlSerializer.endTag(null, TAG_ROOT)
        xmlSerializer.endDocument()
        xmlSerializer.flush()
    }

    /**
     * Converts all expenses into OFX XML format and adds them to the XML document.
     *
     * @param xmlSerializer the wXML writer.
     * @param accounts List of accounts to export.
     */
    private fun writeAccounts(
        xmlSerializer: XmlSerializer,
        exportParams: ExportParams,
        accounts: List<Account>
    ) {
        val isDoubleEntryEnabled = GnuCashApplication.isDoubleEntryEnabled(context)
        val nameImbalance = context.getString(R.string.imbalance_account_name)
        val modifiedSince = exportParams.exportStartTime

        xmlSerializer.startTag(null, TAG_BANK_MESSAGES_V1)
        xmlSerializer.startTag(null, TAG_STATEMENT_TRANSACTION_RESPONSE)

        // Unsolicited because the data exported is not as a result of a request.
        xmlSerializer.startTag(null, TAG_TRANSACTION_UID)
        xmlSerializer.text(UNSOLICITED_TRANSACTION_ID)
        xmlSerializer.endTag(null, TAG_TRANSACTION_UID)

        accounts
            .filter { !cancellationSignal.isCanceled }
            .filter { it.commodity.isCurrency }
            .filter { transactionsDbAdapter.getTransactionsCount(it.uid) > 0 }
            .filter {
                // TODO: investigate whether skipping the imbalance accounts makes sense.
                // Also, using locale-dependant names here is error-prone.
                isDoubleEntryEnabled || !it.name.contains(nameImbalance)
            }
            .forEach { account ->
                cancellationSignal.throwIfCanceled()
                // Add account details (transactions) to the XML document.
                writeAccount(xmlSerializer, account, modifiedSince)
            }

        xmlSerializer.endTag(null, TAG_STATEMENT_TRANSACTION_RESPONSE)
        xmlSerializer.endTag(null, TAG_BANK_MESSAGES_V1)
    }

    private fun writeAccount(
        xmlSerializer: XmlSerializer,
        account: Account,
        modifiedSince: Timestamp
    ) {
        xmlSerializer.startTag(null, TAG_STATEMENT_TRANSACTIONS)

        xmlSerializer.startTag(null, TAG_CURRENCY_DEF)
        xmlSerializer.text(account.commodity.currencyCode)
        xmlSerializer.endTag(null, TAG_CURRENCY_DEF)

        //================= BEGIN BANK ACCOUNT INFO =================================
        xmlSerializer.startTag(null, TAG_BANK_ACCOUNT_FROM)

        xmlSerializer.startTag(null, TAG_BANK_ID)
        xmlSerializer.text(APP_ID)
        xmlSerializer.endTag(null, TAG_BANK_ID)

        xmlSerializer.startTag(null, TAG_ACCOUNT_ID)
        xmlSerializer.text(account.fullName)
        xmlSerializer.endTag(null, TAG_ACCOUNT_ID)

        xmlSerializer.startTag(null, TAG_ACCOUNT_TYPE)
        xmlSerializer.text(OfxAccountType.of(account.accountType).value)
        xmlSerializer.endTag(null, TAG_ACCOUNT_TYPE)

        xmlSerializer.endTag(null, TAG_BANK_ACCOUNT_FROM)
        //================= END BANK ACCOUNT INFO ============================================

        writeTransactions(xmlSerializer, account, modifiedSince)

        //================= BEGIN ACCOUNT BALANCE INFO =================================
        val balance = getAccountBalance(account)
        xmlSerializer.startTag(null, TAG_LEDGER_BALANCE)

        xmlSerializer.startTag(null, TAG_BALANCE_AMOUNT)
        xmlSerializer.text(balance.toPlainString())
        xmlSerializer.endTag(null, TAG_BALANCE_AMOUNT)
        xmlSerializer.startTag(null, TAG_DATE_AS_OF)
        xmlSerializer.text(formattedCurrentTime)
        xmlSerializer.endTag(null, TAG_DATE_AS_OF)

        xmlSerializer.endTag(null, TAG_LEDGER_BALANCE)
        //================= END ACCOUNT BALANCE INFO =================================

        xmlSerializer.endTag(null, TAG_STATEMENT_TRANSACTIONS)

        listener?.onAccount(account)
    }

    /**
     * Returns the aggregate of all transactions in this account.
     * It takes into account debit and credit amounts, it does not however consider sub-accounts
     *
     * @return [Money] aggregate amount of all transactions in account.
     */
    private fun getAccountBalance(account: Account): Money {
        return accountsDbAdapter.getAccountBalance(account, ALWAYS, System.currentTimeMillis())
    }

    private fun writeTransactions(
        xmlSerializer: XmlSerializer,
        account: Account,
        modifiedSince: Timestamp
    ) {
        //================= BEGIN TRANSACTIONS LIST =================================
        xmlSerializer.startTag(null, TAG_BANK_TRANSACTION_LIST)

        xmlSerializer.startTag(null, TAG_DATE_START)
        xmlSerializer.text(formatTime(modifiedSince.time))
        xmlSerializer.endTag(null, TAG_DATE_START)
        xmlSerializer.startTag(null, TAG_DATE_END)
        xmlSerializer.text(formattedCurrentTime)
        xmlSerializer.endTag(null, TAG_DATE_END)

        transactionsDbAdapter.fetchTransactionsModifiedSince(modifiedSince).forEach { cursor ->
            val transaction = transactionsDbAdapter.buildModelInstance(cursor)
            writeTransaction(xmlSerializer, account, transaction)
            listener?.onTransaction(transaction)
        }

        xmlSerializer.endTag(null, TAG_BANK_TRANSACTION_LIST)
        //================= END TRANSACTIONS LIST =================================
    }

    private fun writeTransaction(
        xmlSerializer: XmlSerializer,
        account: Account,
        transaction: Transaction
    ) {
        val balance = transaction.getBalance(account, false)
        val transactionType = if (balance.isNegative) {
            TransactionType.CREDIT
        } else {
            TransactionType.DEBIT
        }

        xmlSerializer.startTag(null, TAG_STATEMENT_TRANSACTION)

        xmlSerializer.startTag(null, TAG_TRANSACTION_TYPE)
        xmlSerializer.text(transactionType.value)
        xmlSerializer.endTag(null, TAG_TRANSACTION_TYPE)

        xmlSerializer.startTag(null, TAG_DATE_POSTED)
        xmlSerializer.text(formatTime(transaction.time))
        xmlSerializer.endTag(null, TAG_DATE_POSTED)

        xmlSerializer.startTag(null, TAG_DATE_USER)
        xmlSerializer.text(formatTime(transaction.modifiedTimestamp.time))
        xmlSerializer.endTag(null, TAG_DATE_USER)

        // This amount should be specified as a positive number.
        xmlSerializer.startTag(null, TAG_TRANSACTION_AMOUNT)
        xmlSerializer.text(balance.abs().toPlainString())
        xmlSerializer.endTag(null, TAG_TRANSACTION_AMOUNT)

        xmlSerializer.startTag(null, TAG_TRANSACTION_FITID)
        xmlSerializer.text(transaction.uid)
        xmlSerializer.endTag(null, TAG_TRANSACTION_FITID)

        if (!transaction.number.isNullOrEmpty()) {
            xmlSerializer.startTag(null, TAG_CHECK_NUMBER)
            xmlSerializer.text(transaction.number)
            xmlSerializer.endTag(null, TAG_CHECK_NUMBER)
        }

        xmlSerializer.startTag(null, TAG_NAME)
        xmlSerializer.text(transaction.description)
        xmlSerializer.endTag(null, TAG_NAME)

        if (!transaction.note.isNullOrEmpty()) {
            xmlSerializer.startTag(null, TAG_MEMO)
            xmlSerializer.text(transaction.note)
            xmlSerializer.endTag(null, TAG_MEMO)
        }

        //if we have exactly one other split, then treat it like a transfer
        if (transaction.splits.size >= 2) {
            writeTransfer(xmlSerializer, account, transaction)
        }

        xmlSerializer.endTag(null, TAG_STATEMENT_TRANSACTION)
    }

    private fun writeTransfer(
        xmlSerializer: XmlSerializer,
        account: Account,
        transaction: Transaction
    ) {
        val accountUID: String = account.uid
        var transferAccount = account
        for (split in transaction.splits) {
            if (split.accountUID != accountUID) {
                transferAccount = accountsDbAdapter.getRecord(split.accountUID!!)
                break
            }
        }
        if (transferAccount == account) return

        xmlSerializer.startTag(null, TAG_BANK_ACCOUNT_TO)

        xmlSerializer.startTag(null, TAG_BANK_ID)
        xmlSerializer.text(APP_ID)
        xmlSerializer.endTag(null, TAG_BANK_ID)

        xmlSerializer.startTag(null, TAG_ACCOUNT_ID)
        xmlSerializer.text(transferAccount.fullName)
        xmlSerializer.endTag(null, TAG_ACCOUNT_ID)

        xmlSerializer.startTag(null, TAG_ACCOUNT_TYPE)
        xmlSerializer.text(OfxAccountType.of(transferAccount.accountType).value)
        xmlSerializer.endTag(null, TAG_ACCOUNT_TYPE)

        xmlSerializer.endTag(null, TAG_BANK_ACCOUNT_TO)
    }
}
