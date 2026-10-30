/*
 * Copyright (c) 2026 GnuCash Pocket contributors
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
package org.gnucash.android.test.unit.db

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.CommonColumns
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.db.getBigDecimal
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Split.Companion.RECONCILED_YES
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics
import org.gnucash.android.util.TimestampHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * Regression tests for the trigger cascade fix introduced for issue #526.
 *
 * Covers the three components of the fix:
 *
 *  1. The scoped `reset_balances_*` triggers on `splits` invalidate the affected
 *     account **and all its ancestors** (the cached `balance` columns include the
 *     subtree totals, so a parent's cache must be invalidated whenever a descendant
 *     account changes).
 *  2. The fixed `update_time_trigger_*` rewrites `modified_at` only on the actually
 *     updated row (the old `WHERE OLD.uid = NEW.uid` clause always evaluated to
 *     true and rewrote the whole table on each firing).
 *  3. `reconcile_state` is in the split update trigger's `UPDATE OF` list, so
 *     reconciling a split invalidates the account's cached balance buckets
 *     (reconciling moves the amount between the cleared / reconciled / noclosing
 *     buckets of the cached balance).
 *
 * The class is standalone: it builds its own test database via [DatabaseHelper] in
 * [setUp] and tears it down in [tearDown], so it is independent of the singletons
 * used by the rest of the test suite (and works whether or not PR #528 is present).
 * It intentionally does not extend `GnuCashTest` — that base class only plants
 * Timber in `@BeforeClass` and offers no setup the tests here rely on; sharing the
 * same `@Config` keeps the SDK/shadow configuration consistent with the rest of
 * the db suite while avoiding any cross-test state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36], shadows = [ShadowCrashlytics::class])
class TriggerCascadeTest {
    private val context = GnuCashApplication.appContext
    private lateinit var databaseName: String
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private lateinit var transactionsDbAdapter: TransactionsDbAdapter
    private lateinit var splitsDbAdapter: SplitsDbAdapter

    @Before
    fun setUp() {
        // Unique DB name per test run keeps the tests isolated from each other and
        // from any singleton state the rest of the suite may have left behind.
        databaseName = "trigger_cascade_test_${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val helper = DatabaseHelper(context, databaseName)
        val holder = helper.holder
        // initCommon = true populates the Commodity.USD / .EUR / ... companion vars
        // used directly by the tests. DatabaseHelper.onCreate already imports the
        // commodities into the table; initCommon just wires the companion statics
        // (upsert on the same rows — harmless, idempotent).
        val commoditiesDbAdapter = CommoditiesDbAdapter(holder, initCommon = true)
        splitsDbAdapter = SplitsDbAdapter(commoditiesDbAdapter)
        transactionsDbAdapter = TransactionsDbAdapter(splitsDbAdapter)
        accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter)
    }

    @After
    fun tearDown() {
        // Closing the adapter chain cascades through the underlying SQLiteDatabase
        // (guarded by isOpen in DatabaseAdapter.close), so the database file can then
        // be deleted cleanly.
        accountsDbAdapter.closeQuietly()
        context.deleteDatabase(databaseName)
    }

    // -------------------------------------------------------------------------
    // Test 1 — ancestor invalidation on both sides of a transaction move
    // -------------------------------------------------------------------------

    /**
     * The scoped reset triggers invalidate the split's account and all its ancestors:
     * moving a transaction must NULL the cached balances of the source, the
     * destination and each of their parents (issue #526).
     *
     * `src` and `dst` sit under different parents so the OLD-side and NEW-side
     * ancestor matching are exercised independently: `parent1` is only an ancestor
     * of `src` (matched via `OLD.account_uid`) and `parent2` only of `dst` (via
     * `NEW.account_uid`). With a single shared parent, a bug in one of the two
     * clauses would be masked by the other.
     *
     * The cached balance of a parent is the subtree total built from its children's
     * cached balances (`computeBalance` caches children before parents), so the
     * caches are primed top-down by reading the parents first. A stale-cache bug
     * (an ancestor not matched by the move trigger) is then observable in the NULL
     * assertions below.
     */
    @Test
    fun `moving a transaction invalidates ancestors on both sides`() {
        val parent1 = Account("Parent1", Commodity.USD)
        parent1.type = AccountType.EXPENSE
        val parent2 = Account("Parent2", Commodity.USD)
        parent2.type = AccountType.EXPENSE
        val src = Account("Src", Commodity.USD)
        src.type = AccountType.EXPENSE
        src.parentUID = parent1.uid
        val dst = Account("Dst", Commodity.USD)
        dst.type = AccountType.EXPENSE
        dst.parentUID = parent2.uid
        accountsDbAdapter.addRecord(parent1)
        accountsDbAdapter.addRecord(parent2)
        accountsDbAdapter.addRecord(src)
        accountsDbAdapter.addRecord(dst)

        val transaction = Transaction("Move me")
        transactionsDbAdapter.addRecord(transaction)
        // Two splits on src: 10 + 5 = 15.
        val split1 = Split(Money("10", Commodity.USD), src)
        split1.transactionUID = transaction.uid
        split1.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(split1)
        val split2 = Split(Money("5", Commodity.USD), src)
        split2.transactionUID = transaction.uid
        split2.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(split2)

        // Prime the caches top-down: reading each parent recursively computes and
        // caches the child's COLUMN_BALANCE before the parent's own cache write.
        assertThat(accountsDbAdapter.getAccountBalance(parent1))
            .isEqualTo(Money("15", Commodity.USD))
        assertThat(accountsDbAdapter.getAccountBalance(parent2))
            .isEqualTo(Money("0", Commodity.USD))
        assertThat(cachedBalanceColumn(parent1.uid, AccountEntry.COLUMN_BALANCE))
            .isEqualByComparingTo("15")
        assertThat(cachedBalanceColumn(src.uid, AccountEntry.COLUMN_BALANCE))
            .isEqualByComparingTo("15")
        assertThat(cachedBalanceColumn(parent2.uid, AccountEntry.COLUMN_BALANCE))
            .isEqualByComparingTo("0")

        // The move rewrites splits.account_uid via the compiled UPDATE statement —
        // the hot path from issue #526.
        transactionsDbAdapter.moveTransaction(transaction.uid, src.uid, dst.uid)

        // The scoped reset trigger NULLed the cached columns of src, dst and both
        // ancestors. Right after the move, before any read, a stale ancestor cache
        // (not matched by the trigger) would still hold its pre-move value here.
        assertThat(cachedBalanceColumn(src.uid, AccountEntry.COLUMN_BALANCE)).isNull()
        assertThat(cachedBalanceColumn(dst.uid, AccountEntry.COLUMN_BALANCE)).isNull()
        assertThat(cachedBalanceColumn(parent1.uid, AccountEntry.COLUMN_BALANCE)).isNull()
        assertThat(cachedBalanceColumn(parent2.uid, AccountEntry.COLUMN_BALANCE)).isNull()

        // The reads recompute from splits (the caches were NULLed above).
        assertThat(accountsDbAdapter.getAccountBalance(src))
            .isEqualTo(Money("0", Commodity.USD))
        assertThat(accountsDbAdapter.getAccountBalance(dst))
            .isEqualTo(Money("15", Commodity.USD))
        assertThat(accountsDbAdapter.getAccountBalance(parent1))
            .isEqualTo(Money("0", Commodity.USD))
        assertThat(accountsDbAdapter.getAccountBalance(parent2))
            .isEqualTo(Money("15", Commodity.USD))
    }

    // -------------------------------------------------------------------------
    // Test 2 — modified_at trigger targets only the updated row
    // -------------------------------------------------------------------------

    /**
     * The fixed `update_time` trigger rewrites `modified_at` only on the updated
     * row. The old `WHERE OLD.uid = NEW.uid` clause always evaluated to true and
     * rewrote every row of the table on each firing (issue #526).
     *
     * `modified_at` is read straight from the DB: the adapter caches `Account`
     * models (`isCached = true`), so `getRecord()` would return the stale
     * pre-update model. Only the `name` column is updated — the trigger fires on
     * any `UPDATE` and rewrites `modified_at` itself, so setting an explicit
     * timestamp via SQL would never survive. `CURRENT_TIMESTAMP` has second
     * granularity, hence the sleep: it guarantees the trigger's rewrite is strictly
     * newer than the INSERT-time default, deterministically, with and without the
     * fix.
     */
    @Test
    fun `modified_at changes only on the updated record`() {
        val account1 = Account("One", Commodity.USD)
        val account2 = Account("Two", Commodity.USD)
        accountsDbAdapter.addRecord(account1)
        accountsDbAdapter.addRecord(account2)

        val initialModifiedAt1 = modifiedAtColumn(account1.uid)!!
        val initialModifiedAt2 = modifiedAtColumn(account2.uid)!!

        // CURRENT_TIMESTAMP has second granularity: wait so the trigger's rewrite
        // is guaranteed to be strictly newer than the INSERT-time default.
        Thread.sleep(1_100)

        // Rename only account1 via raw SQL. This fires update_time_trigger_accounts
        // for exactly one row; the trigger rewrites modified_at on the affected row(s).
        accountsDbAdapter.holder.db.execSQL(
            "UPDATE " + AccountEntry.TABLE_NAME +
                    " SET " + AccountEntry.COLUMN_NAME +
                    " = ? WHERE " + AccountEntry.COLUMN_UID + " = ?",
            arrayOf("OneRenamed", account1.uid)
        )

        // Read the DB directly (the adapter caches models, so getRecord would return
        // the pre-update model).
        val updated1 = modifiedAtColumn(account1.uid)!!
        val updated2 = modifiedAtColumn(account2.uid)!!

        // The updated row's modified_at was rewritten by the trigger to
        // CURRENT_TIMESTAMP (strictly newer than the INSERT default because of the
        // sleep).
        assertThat(updated1.time).isGreaterThan(initialModifiedAt1.time)
        // The unrelated row was NOT rewritten — with the old always-true WHERE
        // clause its modified_at would have been bumped to CURRENT_TIMESTAMP as well.
        assertThat(updated2).isEqualTo(initialModifiedAt2)
    }

    // -------------------------------------------------------------------------
    // Test 3 — reconcile_state is in the trigger's UPDATE OF list
    // -------------------------------------------------------------------------

    /**
     * `reconcile_state` is in the reset trigger's `UPDATE OF` list: reconciling a
     * split moves its amount into the reconciled bucket, so the account's cached
     * balance columns must be invalidated (issue #526).
     *
     * The test primes the cached `balance` column, seeds the `reconciled_balance`
     * bucket with a stale value by hand (`AccountsDbAdapter` never writes that
     * column — there is no public API that populates it, so the seed simulates a
     * leftover from a previous session), reconciles the split with a raw `UPDATE`
     * of only `reconcile_state`, and asserts both cached columns were invalidated
     * (back to `NULL`) — a stale cache would keep the old non-`NULL` values.
     */
    @Test
    fun `reconciling a split invalidates the cached balance`() {
        val account = Account("ReconcileMe", Commodity.USD)
        account.type = AccountType.ASSET
        accountsDbAdapter.addRecord(account)

        val transaction = Transaction("Reconcile tx")
        transactionsDbAdapter.addRecord(transaction)
        val split = Split(Money("25", Commodity.USD), account)
        split.transactionUID = transaction.uid
        split.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(split)

        // Prime the cache: computes and stores COLUMN_BALANCE. Also seed the
        // reconciled_balance bucket with a stale value by hand (a leftover from a
        // previous session — no public API populates it), so the invalidation below
        // is observable on a bucket that semantically depends on reconcile_state.
        assertThat(accountsDbAdapter.getAccountBalance(account))
            .isEqualTo(Money("25", Commodity.USD))
        assertThat(cachedBalanceColumn(account.uid, AccountEntry.COLUMN_BALANCE))
            .isEqualByComparingTo("25")
        // The balance columns are varchar(255) storing numeric strings (read back via
        // getString -> BigDecimal), so the seed is bound as a String — a BigDecimal
        // bind arg is at best truncated to a long by the bind layer (Number.longValue()).
        accountsDbAdapter.holder.db.execSQL(
            "UPDATE " + AccountEntry.TABLE_NAME +
                    " SET " + AccountEntry.COLUMN_RECONCILED_BALANCE +
                    " = ? WHERE " + AccountEntry.COLUMN_UID + " = ?",
            arrayOf("25.00", account.uid)
        )
        assertThat(cachedBalanceColumn(account.uid, AccountEntry.COLUMN_RECONCILED_BALANCE))
            .isEqualByComparingTo("25")

        // Reconcile the split via a raw UPDATE of only reconcile_state. The reset
        // trigger is `AFTER UPDATE OF ..., reconcile_state, reconcile_date, ...` on
        // splits, so touching only reconcile_state isolates exactly that column: if
        // reconcile_state were missing from the UPDATE OF list, the trigger would not
        // fire and the cached balance would stay 25. A raw SQL update is used (instead
        // of splitsDbAdapter.addRecord(split, UpdateMethod.Update)) because the
        // adapter's compiled UPDATE statement rewrites *every* column — including
        // value_num/quantity_num — which would also fire the trigger and mask a
        // missing reconcile_state entry.
        splitsDbAdapter.holder.db.execSQL(
            "UPDATE " + SplitEntry.TABLE_NAME +
                    " SET " + SplitEntry.COLUMN_RECONCILE_STATE +
                    " = ? WHERE " + SplitEntry.COLUMN_UID + " = ?",
            arrayOf(RECONCILED_YES.toString(), split.uid)
        )

        // The caches were invalidated — the reset trigger NULLs all four balance
        // columns together, including the semantically reconcile-dependent bucket
        // seeded above. The next read recomputes from splits.
        assertThat(cachedBalanceColumn(account.uid, AccountEntry.COLUMN_BALANCE))
            .isNull()
        assertThat(cachedBalanceColumn(account.uid, AccountEntry.COLUMN_RECONCILED_BALANCE))
            .isNull()
        // And the recomputed balance is still correct.
        assertThat(accountsDbAdapter.getAccountBalance(account))
            .isEqualTo(Money("25", Commodity.USD))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reads a cached balance column (`balance` / `cleared_balance` /
     * `noclosing_balance` / `reconciled_balance`) directly from the accounts
     * table. Returns `null` when the cache has not been populated (or has been
     * invalidated by a reset trigger).
     */
    private fun cachedBalanceColumn(accountUID: String, column: String): BigDecimal? {
        val db = accountsDbAdapter.holder.db
        val cursor = db.query(
            AccountEntry.TABLE_NAME,
            arrayOf(column),
            AccountEntry.COLUMN_UID + " = ?",
            arrayOf(accountUID),
            null, null, null
        )
        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getBigDecimal(0)
        }
    }

    /**
     * Reads `modified_at` directly from the accounts table. The adapter caches
     * `Account` models (`isCached = true`), so `getRecord()` would return the stale
     * pre-update model after a raw SQL update — the same reason
     * [cachedBalanceColumn] reads the DB directly. The parser accepts both
     * `yyyy-MM-dd HH:mm:ss.SSS` and the second-granularity
     * `yyyy-MM-dd HH:mm:ss` written by `CURRENT_TIMESTAMP`.
     */
    private fun modifiedAtColumn(accountUID: String): java.sql.Timestamp? {
        val cursor = accountsDbAdapter.holder.db.query(
            AccountEntry.TABLE_NAME,
            arrayOf(CommonColumns.COLUMN_MODIFIED_AT),
            AccountEntry.COLUMN_UID + " = ?",
            arrayOf(accountUID),
            null, null, null
        )
        cursor.use {
            if (!it.moveToFirst()) return null
            return TimestampHelper.getTimestampFromUtcString(it.getString(0))
        }
    }
}
