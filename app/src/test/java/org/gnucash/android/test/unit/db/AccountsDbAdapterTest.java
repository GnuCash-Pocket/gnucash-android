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
package org.gnucash.android.test.unit.db;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;

import org.assertj.core.data.Index;
import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.BudgetAmountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.importer.GncXmlImporter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.test.unit.GnuCashTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import timber.log.Timber;

public class AccountsDbAdapterTest extends GnuCashTest {

    private static final String BRAVO_ACCOUNT_NAME = "Bravo";
    private static final String ALPHA_ACCOUNT_NAME = "Alpha";
    private AccountsDbAdapter mAccountsDbAdapter;
    private TransactionsDbAdapter mTransactionsDbAdapter;
    private SplitsDbAdapter mSplitsDbAdapter;
    private CommoditiesDbAdapter mCommoditiesDbAdapter;

    @Before
    public void setUp() throws Exception {
        initAdapters(null);
    }

    @After
    public void after() throws Exception {
        mAccountsDbAdapter.close();
        mCommoditiesDbAdapter.close();
        mSplitsDbAdapter.close();
        mTransactionsDbAdapter.close();
    }

    /**
     * Initialize database adapters for a specific book.
     * This method should be called everytime a new book is loaded into the database
     *
     * @param bookUID GUID of the GnuCash book
     */
    private void initAdapters(String bookUID) {
        if (bookUID == null) {
            mCommoditiesDbAdapter = CommoditiesDbAdapter.getInstance();
            mSplitsDbAdapter = SplitsDbAdapter.getInstance();
            mTransactionsDbAdapter = TransactionsDbAdapter.getInstance();
            mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        } else {
            DatabaseHelper databaseHelper = new DatabaseHelper(GnuCashApplication.getAppContext(), bookUID);
            SQLiteDatabase db = databaseHelper.getWritableDatabase();
            mCommoditiesDbAdapter = new CommoditiesDbAdapter(db);
            mSplitsDbAdapter = new SplitsDbAdapter(mCommoditiesDbAdapter);
            mTransactionsDbAdapter = new TransactionsDbAdapter(mSplitsDbAdapter);
            mAccountsDbAdapter = new AccountsDbAdapter(mTransactionsDbAdapter);
            BooksDbAdapter.getInstance().setActive(bookUID);
        }
    }

    /**
     * Test that the list of accounts is always returned sorted alphabetically
     */
    @Test
    public void shouldBeAlphabeticallySortedByDefault() {
        Account first = new Account(ALPHA_ACCOUNT_NAME);
        Account second = new Account(BRAVO_ACCOUNT_NAME);
        //purposefully added the second after the first
        mAccountsDbAdapter.addRecord(second);
        mAccountsDbAdapter.addRecord(first);

        List<Account> accountsList = mAccountsDbAdapter.getAllRecords();
        assertThat(accountsList.size()).isEqualTo(2);
        //bravo was saved first, but alpha should be first alphabetically
        assertThat(accountsList).contains(first, Index.atIndex(0));
        assertThat(accountsList).contains(second, Index.atIndex(1));
    }

    @Test
    public void bulkAddAccountsShouldNotModifyTransactions() {
        Account account1 = new Account("AlphaAccount");
        Account account2 = new Account("BetaAccount");
        Transaction transaction = new Transaction("MyTransaction");
        Split split = new Split(Money.createZeroInstance(account1.getCommodity()), account1.getUID());
        transaction.addSplit(split);
        transaction.addSplit(split.createPair(account2.getUID()));
        account1.addTransaction(transaction);
        account2.addTransaction(transaction);

        List<Account> accounts = new ArrayList<>();
        accounts.add(account1);
        accounts.add(account2);

        mAccountsDbAdapter.bulkAddRecords(accounts);

        SplitsDbAdapter splitsDbAdapter = SplitsDbAdapter.getInstance();
        assertThat(splitsDbAdapter.getSplitsForTransactionInAccount(transaction.getUID(), account1.getUID())).hasSize(1);
        assertThat(splitsDbAdapter.getSplitsForTransactionInAccount(transaction.getUID(), account2.getUID())).hasSize(1);

        assertThat(mAccountsDbAdapter.getRecord(account1.getUID()).getTransactions()).hasSize(1);
    }

    @Test
    public void shouldAddAccountsToDatabase() {
        Account account1 = new Account("AlphaAccount");
        Account account2 = new Account("BetaAccount");
        Transaction transaction = new Transaction("MyTransaction");
        Split split = new Split(Money.createZeroInstance(account1.getCommodity()), account1.getUID());
        transaction.addSplit(split);
        transaction.addSplit(split.createPair(account2.getUID()));
        account1.addTransaction(transaction);
        account2.addTransaction(transaction);

        // Disable foreign key validation because the second split,
        // which is added during 1st account,
        // references the second account which has not been added yet.
        mAccountsDbAdapter.enableForeignKey(false);
        mAccountsDbAdapter.addRecord(account1);
        mAccountsDbAdapter.addRecord(account2);
        mAccountsDbAdapter.enableForeignKey(true);
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(3);//root+account1+account2

        Account firstAccount = mAccountsDbAdapter.getRecord(account1.getUID());
        assertThat(firstAccount).isNotNull();
        assertThat(firstAccount.getUID()).isEqualTo(account1.getUID());
        assertThat(firstAccount.getFullName()).isEqualTo(account1.getFullName());

        Account secondAccount = mAccountsDbAdapter.getRecord(account2.getUID());
        assertThat(secondAccount).isNotNull();
        assertThat(secondAccount.getUID()).isEqualTo(account2.getUID());

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);
    }

    /**
     * Tests the foreign key constraint "ON DELETE CASCADE" between accounts and splits
     */
    @Test
    public void shouldDeleteSplitsWhenAccountDeleted() {
        Account first = new Account(ALPHA_ACCOUNT_NAME);
        first.setUID(ALPHA_ACCOUNT_NAME);
        Account second = new Account(BRAVO_ACCOUNT_NAME);
        second.setUID(BRAVO_ACCOUNT_NAME);

        mAccountsDbAdapter.addRecord(second);
        mAccountsDbAdapter.addRecord(first);

        Transaction transaction = new Transaction("TestTrn");
        Split split = new Split(Money.createZeroInstance(first.getCommodity()), ALPHA_ACCOUNT_NAME);
        transaction.addSplit(split);
        transaction.addSplit(split.createPair(BRAVO_ACCOUNT_NAME));

        mTransactionsDbAdapter.addRecord(transaction);

        mAccountsDbAdapter.deleteRecord(ALPHA_ACCOUNT_NAME);

        Transaction trxn = mTransactionsDbAdapter.getRecord(transaction.getUID());
        assertThat(trxn.getSplits().size()).isEqualTo(1);
        assertThat(trxn.getSplits().get(0).getAccountUID()).isEqualTo(BRAVO_ACCOUNT_NAME);
    }

    /**
     * Tests that a ROOT account will always be created in the system
     */
    @Test
    public void shouldCreateDefaultRootAccount() {
        Account account = new Account("Some account");
        mAccountsDbAdapter.addRecord(account);
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(2L);

        List<Account> accounts = mAccountsDbAdapter.getSimpleAccountList();
        assertThat(accounts).extracting("accountType").contains(AccountType.ROOT);

        String rootAccountUID = mAccountsDbAdapter.getOrCreateGnuCashRootAccountUID();
        assertThat(rootAccountUID).isEqualTo(accounts.get(1).getParentUID());
    }

    @Test
    public void shouldUpdateFullNameAfterParentChange() {
        Account parent = new Account("Test");
        Account child = new Account("Child");

        mAccountsDbAdapter.addRecord(parent);
        mAccountsDbAdapter.addRecord(child);
        assertThat(child.getFullName()).isEqualTo("Child");

        child.setParentUID(parent.getUID());
        mAccountsDbAdapter.addRecord(child);

        child = mAccountsDbAdapter.getRecord(child.getUID());
        parent = mAccountsDbAdapter.getRecord(parent.getUID());

        assertThat(mAccountsDbAdapter.getSubAccountCount(parent.getUID())).isEqualTo(1);
        assertThat(parent.getUID()).isEqualTo(child.getParentUID());

        assertThat(child.getFullName()).isEqualTo("Test:Child");
    }

    @Test
    public void shouldAddTransactionsAndSplitsWhenAddingAccounts() {
        Account account = new Account("Test");
        mAccountsDbAdapter.addRecord(account);

        Transaction transaction = new Transaction("Test description");
        Split split = new Split(Money.createZeroInstance(account.getCommodity()), account.getUID());
        transaction.addSplit(split);
        Account account1 = new Account("Transfer account");
        transaction.addSplit(split.createPair(account1.getUID()));
        account1.addTransaction(transaction);

        mAccountsDbAdapter.addRecord(account1);

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);
        assertThat(mSplitsDbAdapter.getRecordsCount()).isEqualTo(2);
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(3); //ROOT account automatically added

    }

    @Test
    public void shouldClearAllTablesWhenDeletingAllAccounts() {
        Account account = new Account("Test");
        Transaction transaction = new Transaction("Test description");
        Split split = new Split(Money.createZeroInstance(account.getCommodity()), account.getUID());
        transaction.addSplit(split);
        Account account2 = new Account("Transfer account");
        transaction.addSplit(split.createPair(account2.getUID()));

        mAccountsDbAdapter.addRecord(account);
        mAccountsDbAdapter.addRecord(account2);

        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.BACKUP);
        scheduledAction.setActionUID("Test-uid");
        scheduledAction.setRecurrence(new Recurrence(PeriodType.WEEK));
        ScheduledActionDbAdapter scheduledActionDbAdapter = ScheduledActionDbAdapter.getInstance();

        scheduledActionDbAdapter.addRecord(scheduledAction);

        Budget budget = new Budget("Test");
        BudgetAmount budgetAmount = new BudgetAmount(Money.createZeroInstance(account.getCommodity()), account.getUID());
        budget.addAmount(budgetAmount);
        budget.setRecurrence(new Recurrence(PeriodType.MONTH));
        BudgetsDbAdapter.getInstance().addRecord(budget);

        mAccountsDbAdapter.deleteAllRecords();

        assertThat(mAccountsDbAdapter.getRecordsCount()).isZero();
        assertThat(mTransactionsDbAdapter.getRecordsCount()).isZero();
        assertThat(mSplitsDbAdapter.getRecordsCount()).isZero();
        assertThat(scheduledActionDbAdapter.getRecordsCount()).isZero();
        assertThat(BudgetAmountsDbAdapter.getInstance().getRecordsCount()).isZero();
        assertThat(BudgetsDbAdapter.getInstance().getRecordsCount()).isZero();
        assertThat(PricesDbAdapter.getInstance().getRecordsCount()).isZero(); //prices should remain
        assertThat(CommoditiesDbAdapter.getInstance().getRecordsCount()).isGreaterThan(50); //commodities should remain
    }

    @Test
    public void simpleAccountListShouldNotContainTransactions() {
        Account account = new Account("Test");
        Transaction transaction = new Transaction("Test description");
        Split split = new Split(Money.createZeroInstance(account.getCommodity()), account.getUID());
        transaction.addSplit(split);
        Account account1 = new Account("Transfer");
        transaction.addSplit(split.createPair(account1.getUID()));

        mAccountsDbAdapter.addRecord(account);
        mAccountsDbAdapter.addRecord(account1);

        List<Account> accounts = mAccountsDbAdapter.getSimpleAccountList();
        for (Account testAcct : accounts) {
            assertThat(testAcct.getTransactionCount()).isZero();
        }
    }

    @Test
    public void shouldComputeAccountBalanceCorrectly() {
        Account account = new Account("Test", Commodity.USD);
        account.setAccountType(AccountType.ASSET); //debit normal account balance
        Account transferAcct = new Account("Transfer");

        mAccountsDbAdapter.addRecord(account);
        mAccountsDbAdapter.addRecord(transferAcct);

        Transaction transaction = new Transaction("Test description");
        mTransactionsDbAdapter.addRecord(transaction);
        Split split = new Split(new Money(BigDecimal.TEN, Commodity.USD), account.getUID());
        split.setTransactionUID(transaction.getUID());
        split.setType(TransactionType.DEBIT);
        mSplitsDbAdapter.addRecord(split);

        split = new Split(new Money("4.99", "USD"), account.getUID());
        split.setTransactionUID(transaction.getUID());
        split.setType(TransactionType.DEBIT);
        mSplitsDbAdapter.addRecord(split);

        split = new Split(new Money("1.19", "USD"), account.getUID());
        split.setTransactionUID(transaction.getUID());
        split.setType(TransactionType.CREDIT);
        mSplitsDbAdapter.addRecord(split);

        split = new Split(new Money("3.49", "EUR"), account.getUID());
        split.setTransactionUID(transaction.getUID());
        split.setType(TransactionType.DEBIT);
        mSplitsDbAdapter.addRecord(split);

        split = new Split(new Money("8.39", "USD"), transferAcct.getUID());
        split.setTransactionUID(transaction.getUID());
        mSplitsDbAdapter.addRecord(split);

        //balance computation ignores the currency of the split
        Money balance = mAccountsDbAdapter.getAccountBalance(account.getUID());
        Money expectedBalance = new Money("17.29", "USD"); //EUR splits should be ignored

        assertThat(balance).isEqualTo(expectedBalance);
    }

    /**
     * Test creating an account hierarchy by specifying fully qualified name
     */
    @Test
    public void shouldCreateAccountHierarchy() {
        String uid = mAccountsDbAdapter.createAccountHierarchy("Assets:Current Assets:Cash in Wallet", AccountType.ASSET);

        List<Account> accounts = mAccountsDbAdapter.getAllRecords();
        assertThat(accounts).hasSize(3);
        assertThat(accounts).extracting("_uid").contains(uid);
    }

    @Test
    public void shouldRecursivelyDeleteAccount() {
        Account account = new Account("Parent");
        Account account2 = new Account("Child");
        account2.setParentUID(account.getUID());

        Transaction transaction = new Transaction("Random");
        account2.addTransaction(transaction);

        Split split = new Split(Money.createZeroInstance(account.getCommodity()), account.getUID());
        transaction.addSplit(split);
        transaction.addSplit(split.createPair(account2.getUID()));

        mAccountsDbAdapter.addRecord(account);
        mAccountsDbAdapter.addRecord(account2);

        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(3);
        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);
        assertThat(mSplitsDbAdapter.getRecordsCount()).isEqualTo(2);

        boolean result = mAccountsDbAdapter.recursiveDeleteAccount(account.getUID());
        assertThat(result).isTrue();

        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(1); //the root account
        assertThat(mTransactionsDbAdapter.getRecordsCount()).isZero();
        assertThat(mSplitsDbAdapter.getRecordsCount()).isZero();

    }

    @Test
    public void shouldGetDescendantAccounts() {
        loadDefaultAccounts();

        String uid = mAccountsDbAdapter.findAccountUidByFullName("Expenses:Auto");
        List<String> descendants = mAccountsDbAdapter.getDescendantAccountUIDs(uid, null, null);

        assertThat(descendants).hasSize(4);
    }

    @Test
    public void shouldReassignDescendantAccounts() {
        loadDefaultAccounts();

        String assetsUID = mAccountsDbAdapter.findAccountUidByFullName("Assets");
        String savingsAcctUID = mAccountsDbAdapter.findAccountUidByFullName("Assets:Current Assets:Savings Account");
        String currentAssetsUID = mAccountsDbAdapter.findAccountUidByFullName("Assets:Current Assets");

        assertThat(mAccountsDbAdapter.getParentAccountUID(savingsAcctUID)).isEqualTo(currentAssetsUID);
        mAccountsDbAdapter.reassignDescendantAccounts(currentAssetsUID, assetsUID);
        assertThat(mAccountsDbAdapter.getParentAccountUID(savingsAcctUID)).isEqualTo(assetsUID);
        assertThat(mAccountsDbAdapter.getFullyQualifiedAccountName(savingsAcctUID)).isEqualTo("Assets:Savings Account");

    }

    @Test
    public void shouldCreateImbalanceAccountOnDemand() {
        Context context = GnuCashApplication.getAppContext();
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(1L);

        Commodity usd = mCommoditiesDbAdapter.getCommodity("USD");
        String imbalanceUID = mAccountsDbAdapter.getImbalanceAccountUID(context, usd);
        assertThat(imbalanceUID).isNull();
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(1L);

        imbalanceUID = mAccountsDbAdapter.getOrCreateImbalanceAccountUID(context, usd);
        assertThat(imbalanceUID).isNotNull().isNotEmpty();
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(2);
    }


    @Test
    public void editingAccountShouldNotDeleteTemplateSplits() {
        Account account = new Account("First", Commodity.EUR);
        Account transferAccount = new Account("Transfer", Commodity.EUR);
        mAccountsDbAdapter.addRecord(account);
        mAccountsDbAdapter.addRecord(transferAccount);

        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(3); //plus root account

        Money money = new Money(BigDecimal.TEN, Commodity.EUR);
        Transaction transaction = new Transaction("Template");
        transaction.setTemplate(true);
        transaction.setCommodity(Commodity.EUR);
        Split split = new Split(money, account.getUID());
        transaction.addSplit(split);
        transaction.addSplit(split.createPair(transferAccount.getUID()));

        mTransactionsDbAdapter.addRecord(transaction);
        List<Transaction> transactions = mTransactionsDbAdapter.getAllRecords();
        assertThat(transactions).hasSize(1);

        assertThat(mTransactionsDbAdapter.getScheduledTransactionsForAccount(account.getUID())).hasSize(1);

        //edit the account
        account.setName("Edited account");
        mAccountsDbAdapter.addRecord(account, DatabaseAdapter.UpdateMethod.update);

        assertThat(mTransactionsDbAdapter.getScheduledTransactionsForAccount(account.getUID())).hasSize(1);
        assertThat(mSplitsDbAdapter.getSplitsForTransaction(transaction.getUID())).hasSize(2);
    }

    @Test
    public void shouldSetDefaultTransferColumnToNull_WhenTheAccountIsDeleted() {
        mAccountsDbAdapter.deleteAllRecords();
        assertThat(mAccountsDbAdapter.getRecordsCount()).isZero();

        Account account1 = new Account("Test");
        Account account2 = new Account("Transfer Account");
        account1.setDefaultTransferAccountUID(account2.getUID());

        mAccountsDbAdapter.addRecord(account1);
        mAccountsDbAdapter.addRecord(account2);

        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(3L); //plus ROOT account
        mAccountsDbAdapter.deleteRecord(account2.getUID());

        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(2L);
        assertThat(mAccountsDbAdapter.getRecord(account1.getUID()).getDefaultTransferAccountUID()).isNull();

        Account account3 = new Account("Sub-test");
        account3.setParentUID(account1.getUID());
        Account account4 = new Account("Third-party");
        account4.setDefaultTransferAccountUID(account3.getUID());

        mAccountsDbAdapter.addRecord(account3);
        mAccountsDbAdapter.addRecord(account4);
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(4L);

        mAccountsDbAdapter.recursiveDeleteAccount(account1.getUID());
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(2L);
        assertThat(mAccountsDbAdapter.getRecord(account4.getUID()).getDefaultTransferAccountUID()).isNull();
    }

    /**
     * Opening an XML file should set the default currency to that used by the most accounts in the file
     */
    @Test
    public void importingXml_shouldSetDefaultCurrencyFromXml() {
        GnuCashApplication.setDefaultCurrencyCode("JPY");

        assertThat(GnuCashApplication.getDefaultCurrencyCode()).isEqualTo("JPY");
        assertThat(Commodity.DEFAULT_COMMODITY).isEqualTo(Commodity.JPY);

        mAccountsDbAdapter.deleteAllRecords();
        loadDefaultAccounts();

        assertThat(GnuCashApplication.getDefaultCurrencyCode()).isNotEqualTo("JPY");
        //the book has USD occuring most often and this will be used as the default currency
        assertThat(GnuCashApplication.getDefaultCurrencyCode()).isEqualTo("USD");
        assertThat(Commodity.DEFAULT_COMMODITY).isEqualTo(Commodity.USD);

        System.out.println("Default currency is now: " + Commodity.DEFAULT_COMMODITY);
    }

    @Test
    public void testChangesToAccount() {
        mAccountsDbAdapter.deleteAllRecords();
        assertThat(mAccountsDbAdapter.getRecordsCount()).isZero();

        Account account1 = new Account("Test");
        mAccountsDbAdapter.addRecord(account1, DatabaseAdapter.UpdateMethod.insert);
        assertThat(account1.id).isNotEqualTo(0); //plus ROOT account
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(2); //plus ROOT account

        Account account2 = mAccountsDbAdapter.getRecord(account1.getUID());
        assertThat(account2).isEqualTo(account1);
        assertThat(account2.isPlaceholder()).isFalse();
        assertThat(account2.isFavorite()).isFalse();
        assertThat(account2.getColor()).isEqualTo(Account.DEFAULT_COLOR);

        account2.setPlaceholder(true);
        account2.setFavorite(true);
        account2.setColor(Color.MAGENTA);
        mAccountsDbAdapter.addRecord(account2, DatabaseAdapter.UpdateMethod.replace);
        Account account3 = mAccountsDbAdapter.getRecord(account2.getUID());
        assertThat(account3).isEqualTo(account2);
        assertThat(account3.isPlaceholder()).isTrue();
        assertThat(account3.isFavorite()).isTrue();
        assertThat(account3.getColor()).isEqualTo(Color.MAGENTA);

        account3.setPlaceholder(true);
        account3.setFavorite(false);
        account3.setColor(Color.YELLOW);
        mAccountsDbAdapter.addRecord(account3, DatabaseAdapter.UpdateMethod.update);
        Account account4 = mAccountsDbAdapter.getRecord(account3.getUID());
        assertThat(account4).isEqualTo(account3);
        assertThat(account4.isPlaceholder()).isTrue();
        assertThat(account4.isFavorite()).isFalse();
        assertThat(account4.getColor()).isEqualTo(Color.YELLOW);
    }

    /**
     * Loads the default accounts from file resource
     */
    private void loadDefaultAccounts() {
        try {
            Context context = GnuCashApplication.getAppContext();
            String bookUID = GncXmlImporter.parse(context, context.getResources().openRawResource(R.raw.default_accounts));
            initAdapters(bookUID);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Timber.e(e);
            throw new RuntimeException("Could not create default accounts");
        }
    }

    @After
    public void tearDown() throws Exception {
        mAccountsDbAdapter.deleteAllRecords();
    }
}
