/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.db.adapter

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.bindBoolean
import org.gnucash.android.db.bindInt
import org.gnucash.android.db.getBoolean
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.util.set
import timber.log.Timber
import java.io.IOException

/**
 * Database adapter for fetching/saving/modifying scheduled events
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ScheduledActionDbAdapter(
    val recurrenceDbAdapter: RecurrenceDbAdapter,
    val transactionsDbAdapter: TransactionsDbAdapter,
    private val booksDbAdapter: BooksDbAdapter = BooksDbAdapter.instance
) : DatabaseAdapter<ScheduledAction>(
    recurrenceDbAdapter.holder,
    ScheduledActionEntry.TABLE_NAME,
    entryColumns
) {
    @Throws(IOException::class)
    override fun close() {
        super.close()
        recurrenceDbAdapter.close()
        transactionsDbAdapter.close()
    }

    override fun addRecord(
        scheduledAction: ScheduledAction,
        updateMethod: UpdateMethod
    ): ScheduledAction {
        recurrenceDbAdapter.addRecord(scheduledAction.recurrence, updateMethod)
        return super.addRecord(scheduledAction, updateMethod)
    }

    override fun bulkAddRecords(
        scheduledActions: List<ScheduledAction>,
        updateMethod: UpdateMethod
    ): Long {
        val recurrences = mutableListOf<Recurrence>()
        for (scheduledAction in scheduledActions) {
            recurrences.add(scheduledAction.recurrence)
        }

        //first add the recurrences, they have no dependencies (foreign key constraints)
        val nRecurrences = recurrenceDbAdapter.bulkAddRecords(recurrences, updateMethod)
        Timber.d("Added %d recurrences for scheduled actions", nRecurrences)

        return super.bulkAddRecords(scheduledActions, updateMethod)
    }

    /**
     * Updates only the recurrence attributes of the scheduled action.
     * The recurrence attributes are the period, start time, end time and/or total frequency.
     * All other properties of a scheduled event are only used for internal database tracking and are
     * not central to the recurrence schedule.
     *
     * **The GUID of the scheduled action should already exist in the database**
     *
     * @param scheduledAction Scheduled action
     * @return the number of rows affected
     */
    fun updateRecurrenceAttributes(scheduledAction: ScheduledAction): Int {
        //since we are updating, first fetch the existing recurrence UID and set it to the object
        //so that it will be updated and not a new one created
        val recurrenceUID =
            getAttribute(scheduledAction, ScheduledActionEntry.COLUMN_RECURRENCE_UID)

        val recurrence = scheduledAction.recurrence
        recurrence.setUID(recurrenceUID)
        recurrenceDbAdapter.update(recurrence)

        val contentValues = ContentValues()
        extractBaseModelAttributes(contentValues, scheduledAction)
        contentValues[ScheduledActionEntry.COLUMN_START_TIME] = scheduledAction.startDate
        contentValues[ScheduledActionEntry.COLUMN_END_TIME] = scheduledAction.endDate
        contentValues[ScheduledActionEntry.COLUMN_TAG] = scheduledAction.tag
        contentValues[ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY] =
            scheduledAction.totalPlannedExecutionCount

        Timber.d("Updating scheduled event recurrence attributes")
        val where = ScheduledActionEntry.COLUMN_UID + "=?"
        val whereArgs = arrayOf<String?>(scheduledAction.uid)
        return db.update(ScheduledActionEntry.TABLE_NAME, contentValues, where, whereArgs)
    }

    override fun bind(stmt: SQLiteStatement, schedxAction: ScheduledAction): SQLiteStatement {
        bindBaseModel(stmt, schedxAction)
        stmt.bindString(1 + INDEX_COLUMN_ACTION_UID, schedxAction.actionUID)
        stmt.bindString(1 + INDEX_COLUMN_TYPE, schedxAction.actionType.value)
        stmt.bindLong(1 + INDEX_COLUMN_START_TIME, schedxAction.startDate)
        stmt.bindLong(1 + INDEX_COLUMN_END_TIME, schedxAction.endDate)
        stmt.bindLong(1 + INDEX_COLUMN_LAST_OCCUR, schedxAction.lastRunDate)
        stmt.bindBoolean(1 + INDEX_COLUMN_ENABLED, schedxAction.isEnabled)
        if (schedxAction.tag != null) {
            stmt.bindString(1 + INDEX_COLUMN_TAG, schedxAction.tag)
        }
        stmt.bindInt(1 + INDEX_COLUMN_TOTAL_FREQUENCY, schedxAction.totalPlannedExecutionCount)
        stmt.bindString(1 + INDEX_COLUMN_RECURRENCE_UID, schedxAction.recurrence.uid)
        stmt.bindBoolean(1 + INDEX_COLUMN_AUTO_CREATE, schedxAction.isAutoCreate)
        stmt.bindBoolean(1 + INDEX_COLUMN_AUTO_NOTIFY, schedxAction.isAutoCreateNotify)
        stmt.bindInt(1 + INDEX_COLUMN_ADVANCE_CREATION, schedxAction.advanceCreateDays)
        stmt.bindInt(1 + INDEX_COLUMN_ADVANCE_NOTIFY, schedxAction.advanceRemindDays)
        stmt.bindString(1 + INDEX_COLUMN_TEMPLATE_ACCT_UID, schedxAction.templateAccountUID)
        stmt.bindInt(1 + INDEX_COLUMN_INSTANCE_COUNT, schedxAction.instanceCount)
        stmt.bindString(1 + INDEX_COLUMN_NAME, schedxAction.name)

        return stmt
    }

    /**
     * Builds a [ScheduledAction] instance from a row to cursor in the database.
     * The cursor should be already pointing to the right entry in the data set. It will not be modified in any way
     *
     * @param cursor Cursor pointing to data set
     * @return ScheduledEvent object instance
     */
    override fun buildModelInstance(cursor: Cursor): ScheduledAction {
        val actionUID = cursor.getString(INDEX_COLUMN_ACTION_UID)!!
        val startTime = cursor.getLong(INDEX_COLUMN_START_TIME)
        val endTime = cursor.getLong(INDEX_COLUMN_END_TIME)
        val lastRun = cursor.getLong(INDEX_COLUMN_LAST_OCCUR)
        val typeString = cursor.getString(INDEX_COLUMN_TYPE)!!
        val tag = cursor.getString(INDEX_COLUMN_TAG)
        val enabled = cursor.getBoolean(INDEX_COLUMN_ENABLED)
        val numOccurrences = cursor.getInt(INDEX_COLUMN_TOTAL_FREQUENCY)
        val execCount = cursor.getInt(INDEX_COLUMN_INSTANCE_COUNT)
        val autoCreate = cursor.getBoolean(INDEX_COLUMN_AUTO_CREATE)
        val autoNotify = cursor.getBoolean(INDEX_COLUMN_AUTO_NOTIFY)
        val advanceCreate = cursor.getInt(INDEX_COLUMN_ADVANCE_CREATION)
        val advanceNotify = cursor.getInt(INDEX_COLUMN_ADVANCE_NOTIFY)
        val recurrenceUID = cursor.getString(INDEX_COLUMN_RECURRENCE_UID)!!
        val templateAccountUID = cursor.getString(INDEX_COLUMN_TEMPLATE_ACCT_UID)
        var name = cursor.getString(INDEX_COLUMN_NAME)

        val actionType = ScheduledAction.ActionType.of(typeString)
        if (name.isNullOrEmpty()) {
            name = try {
                if (actionType == ScheduledAction.ActionType.TRANSACTION) {
                    transactionsDbAdapter.getAttribute(
                        actionUID,
                        TransactionEntry.COLUMN_DESCRIPTION
                    )
                } else {
                    booksDbAdapter.getAttribute(actionUID, BookEntry.COLUMN_DISPLAY_NAME)
                }
            } catch (_: Exception) {
                actionType.value
            }
        }

        val scheduledAction = ScheduledAction(actionType)
        populateBaseModelAttributes(cursor, scheduledAction)
        scheduledAction.startDate = startTime
        scheduledAction.endDate = endTime
        scheduledAction.actionUID = actionUID
        scheduledAction.lastRunTime = lastRun
        scheduledAction.tag = tag
        scheduledAction.isEnabled = enabled
        scheduledAction.totalPlannedExecutionCount = numOccurrences
        scheduledAction.instanceCount = execCount
        scheduledAction.isAutoCreate = autoCreate
        scheduledAction.isAutoCreateNotify = autoNotify
        scheduledAction.advanceCreateDays = advanceCreate
        scheduledAction.advanceRemindDays = advanceNotify
        //TODO: optimize by doing overriding fetchRecord(String) and join the two tables
        scheduledAction.setRecurrence(recurrenceDbAdapter.getRecord(recurrenceUID))
        scheduledAction.setTemplateAccountUID(templateAccountUID)
        scheduledAction.name = name

        return scheduledAction
    }

    /**
     * Returns all enabled scheduled actions in the database
     *
     * @return List of enabled scheduled actions
     */
    val allEnabledScheduledActions: List<ScheduledAction>
        get() {
            val where = ScheduledActionEntry.COLUMN_ENABLED + "=1"
            val cursor = db.query(tableName, allColumns, where, null, null, null, null)
            return getRecords(cursor)
        }

    /**
     * Returns the number of instances of the action which have been created from this scheduled action
     *
     * @param scheduledActionUID GUID of scheduled action
     * @return Number of transactions created from scheduled action
     */
    fun getActionInstanceCount(scheduledActionUID: String?): Long {
        return DatabaseUtils.queryNumEntries(
            db,
            TransactionEntry.TABLE_NAME,
            TransactionEntry.COLUMN_SCHEDX_ACTION_UID + "=?",
            arrayOf<String?>(scheduledActionUID)
        )
    }

    fun getRecords(actionType: ScheduledAction.ActionType): List<ScheduledAction> {
        val where = ScheduledActionEntry.COLUMN_TYPE + "=?"
        val whereArgs = arrayOf<String?>(actionType.value)
        return getAllRecords(where, whereArgs)
    }

    fun getRecordsCount(actionType: ScheduledAction.ActionType): Long {
        val where = ScheduledActionEntry.COLUMN_TYPE + "=?"
        val whereArgs = arrayOf<String?>(actionType.value)
        return getRecordsCount(where, whereArgs)
    }

    companion object {
        private val entryColumns = arrayOf(
            ScheduledActionEntry.COLUMN_ACTION_UID,
            ScheduledActionEntry.COLUMN_TYPE,
            ScheduledActionEntry.COLUMN_START_TIME,
            ScheduledActionEntry.COLUMN_END_TIME,
            ScheduledActionEntry.COLUMN_LAST_OCCUR,
            ScheduledActionEntry.COLUMN_ENABLED,
            ScheduledActionEntry.COLUMN_TAG,
            ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY,
            ScheduledActionEntry.COLUMN_RECURRENCE_UID,
            ScheduledActionEntry.COLUMN_AUTO_CREATE,
            ScheduledActionEntry.COLUMN_AUTO_NOTIFY,
            ScheduledActionEntry.COLUMN_ADVANCE_CREATION,
            ScheduledActionEntry.COLUMN_ADVANCE_NOTIFY,
            ScheduledActionEntry.COLUMN_TEMPLATE_ACCT_UID,
            ScheduledActionEntry.COLUMN_INSTANCE_COUNT,
            ScheduledActionEntry.COLUMN_NAME
        )
        private const val INDEX_COLUMN_ACTION_UID = 0
        private const val INDEX_COLUMN_TYPE = INDEX_COLUMN_ACTION_UID + 1
        private const val INDEX_COLUMN_START_TIME = INDEX_COLUMN_TYPE + 1
        private const val INDEX_COLUMN_END_TIME = INDEX_COLUMN_START_TIME + 1
        private const val INDEX_COLUMN_LAST_OCCUR = INDEX_COLUMN_END_TIME + 1
        private const val INDEX_COLUMN_ENABLED = INDEX_COLUMN_LAST_OCCUR + 1
        private const val INDEX_COLUMN_TAG = INDEX_COLUMN_ENABLED + 1
        private const val INDEX_COLUMN_TOTAL_FREQUENCY = INDEX_COLUMN_TAG + 1
        private const val INDEX_COLUMN_RECURRENCE_UID = INDEX_COLUMN_TOTAL_FREQUENCY + 1
        private const val INDEX_COLUMN_AUTO_CREATE = INDEX_COLUMN_RECURRENCE_UID + 1
        private const val INDEX_COLUMN_AUTO_NOTIFY = INDEX_COLUMN_AUTO_CREATE + 1
        private const val INDEX_COLUMN_ADVANCE_CREATION = INDEX_COLUMN_AUTO_NOTIFY + 1
        private const val INDEX_COLUMN_ADVANCE_NOTIFY = INDEX_COLUMN_ADVANCE_CREATION + 1
        private const val INDEX_COLUMN_TEMPLATE_ACCT_UID = INDEX_COLUMN_ADVANCE_NOTIFY + 1
        private const val INDEX_COLUMN_INSTANCE_COUNT = INDEX_COLUMN_TEMPLATE_ACCT_UID + 1
        private const val INDEX_COLUMN_NAME = INDEX_COLUMN_INSTANCE_COUNT + 1

        val instance: ScheduledActionDbAdapter get() = GnuCashApplication.scheduledEventDbAdapter!!
    }
}
