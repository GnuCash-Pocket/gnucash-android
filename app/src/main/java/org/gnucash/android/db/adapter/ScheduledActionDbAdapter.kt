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
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.bindBoolean
import org.gnucash.android.db.bindInt
import org.gnucash.android.db.getBoolean
import org.gnucash.android.db.getInt
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
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
) :
    DatabaseAdapter<ScheduledAction>(
        recurrenceDbAdapter.holder,
        ScheduledActionEntry.TABLE_NAME,
        arrayOf(
            ScheduledActionEntry.COLUMN_ACTION_UID,
            ScheduledActionEntry.COLUMN_TYPE,
            ScheduledActionEntry.COLUMN_START_TIME,
            ScheduledActionEntry.COLUMN_END_TIME,
            ScheduledActionEntry.COLUMN_LAST_OCCUR,
            ScheduledActionEntry.COLUMN_ENABLED,
            ScheduledActionEntry.COLUMN_CREATED_AT,
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
    ) {
    @Throws(IOException::class)
    override fun close() {
        super.close()
        recurrenceDbAdapter.close()
        transactionsDbAdapter.close()
    }

    override fun addRecord(scheduledAction: ScheduledAction, updateMethod: UpdateMethod) {
        recurrenceDbAdapter.addRecord(scheduledAction.recurrence!!, updateMethod)
        super.addRecord(scheduledAction, updateMethod)
    }

    override fun bulkAddRecords(
        scheduledActions: List<ScheduledAction>,
        updateMethod: UpdateMethod
    ): Long {
        val recurrences = mutableListOf<Recurrence>()
        for (scheduledAction in scheduledActions) {
            scheduledAction.recurrence?.let { recurrences.add(it) }
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
        recurrence!!.setUID(recurrenceUID)
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
        stmt.bindString(1, schedxAction.actionUID)
        stmt.bindString(2, schedxAction.actionType.value)
        stmt.bindLong(3, schedxAction.startTime)
        stmt.bindLong(4, schedxAction.endTime)
        stmt.bindLong(5, schedxAction.lastRunTime)
        stmt.bindBoolean(6, schedxAction.isEnabled)
        stmt.bindString(7, getUtcStringFromTimestamp(schedxAction.createdTimestamp))
        if (schedxAction.tag != null) {
            stmt.bindString(8, schedxAction.tag)
        }
        stmt.bindInt(9, schedxAction.totalPlannedExecutionCount)
        stmt.bindString(10, schedxAction.recurrence!!.uid)
        stmt.bindBoolean(11, schedxAction.isAutoCreate)
        stmt.bindBoolean(12, schedxAction.isAutoCreateNotify)
        stmt.bindInt(13, schedxAction.advanceCreateDays)
        stmt.bindInt(14, schedxAction.advanceRemindDays)
        stmt.bindString(15, schedxAction.templateAccountUID)
        stmt.bindInt(16, schedxAction.instanceCount)
        if (schedxAction.name != null) {
            stmt.bindString(17, schedxAction.name)
        }

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
        val actionUID = cursor.getString(ScheduledActionEntry.COLUMN_ACTION_UID)!!
        val startTime = cursor.getLong(ScheduledActionEntry.COLUMN_START_TIME)
        val endTime = cursor.getLong(ScheduledActionEntry.COLUMN_END_TIME)
        val lastRun = cursor.getLong(ScheduledActionEntry.COLUMN_LAST_OCCUR)
        val typeString = cursor.getString(ScheduledActionEntry.COLUMN_TYPE)!!
        val tag = cursor.getString(ScheduledActionEntry.COLUMN_TAG)
        val enabled = cursor.getBoolean(ScheduledActionEntry.COLUMN_ENABLED)
        val numOccurrences = cursor.getInt(ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY)
        val execCount = cursor.getInt(ScheduledActionEntry.COLUMN_INSTANCE_COUNT)
        val autoCreate = cursor.getBoolean(ScheduledActionEntry.COLUMN_AUTO_CREATE)
        val autoNotify = cursor.getBoolean(ScheduledActionEntry.COLUMN_AUTO_NOTIFY)
        val advanceCreate = cursor.getInt(ScheduledActionEntry.COLUMN_ADVANCE_CREATION)
        val advanceNotify = cursor.getInt(ScheduledActionEntry.COLUMN_ADVANCE_NOTIFY)
        val recurrenceUID = cursor.getString(ScheduledActionEntry.COLUMN_RECURRENCE_UID)!!
        val templateAccountUID = cursor.getString(ScheduledActionEntry.COLUMN_TEMPLATE_ACCT_UID)
        var name = cursor.getString(ScheduledActionEntry.COLUMN_NAME)

        val actionType = ScheduledAction.ActionType.of(typeString)
        if (name.isNullOrEmpty()) {
            name = if (actionType == ScheduledAction.ActionType.TRANSACTION) {
                transactionsDbAdapter.getAttribute(actionUID, TransactionEntry.COLUMN_DESCRIPTION)
            } else {
                try {
                    booksDbAdapter.getAttribute(actionUID, BookEntry.COLUMN_DISPLAY_NAME)
                } catch (_: IllegalArgumentException) {
                    actionType.name
                }
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
     * Returns all [ScheduledAction]s from the database with the specified action UID.
     * Note that the parameter is not of the the scheduled action record, but from the action table
     *
     * @param actionUID GUID of the event itself
     * @return List of ScheduledEvents
     */
    fun getScheduledActionsWithUID(actionUID: String): List<ScheduledAction> {
        val cursor: Cursor? = db.query(
            tableName, null,
            ScheduledActionEntry.COLUMN_ACTION_UID + "= ?",
            arrayOf<String?>(actionUID), null, null, null
        )
        return getRecords(cursor)
    }

    /**
     * Returns all enabled scheduled actions in the database
     *
     * @return List of enabled scheduled actions
     */
    val allEnabledScheduledActions: List<ScheduledAction>
        get() {
            val cursor: Cursor? = db.query(
                tableName, null, ScheduledActionEntry.COLUMN_ENABLED + "=1", null, null, null, null
            )
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
            DatabaseSchema.TransactionEntry.TABLE_NAME,
            DatabaseSchema.TransactionEntry.COLUMN_SCHEDX_ACTION_UID + "=?",
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
        val instance: ScheduledActionDbAdapter get() = GnuCashApplication.scheduledEventDbAdapter!!
    }
}
