package org.gnucash.android.test.unit.db

import android.text.format.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Before
import org.junit.Test

/**
 * Test the scheduled actions database adapter
 */
class ScheduledActionDbAdapterTest : GnuCashTest() {
    private lateinit var scheduledActionDbAdapter: ScheduledActionDbAdapter

    @Before
    fun setUp() {
        scheduledActionDbAdapter = ScheduledActionDbAdapter.instance
    }

    fun shouldFetchOnlyEnabledScheduledActions() {
        var scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            setRecurrence(Recurrence(PeriodType.MONTH))
            isEnabled = false
        }

        scheduledActionDbAdapter.addRecord(scheduledAction)

        scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            setRecurrence(Recurrence(PeriodType.WEEK))
        }
        scheduledActionDbAdapter.addRecord(scheduledAction)

        assertThat(scheduledActionDbAdapter.allRecords).hasSize(2)

        val enabledActions = scheduledActionDbAdapter.allEnabledScheduledActions
        assertThat(enabledActions).hasSize(1)
        assertThat(enabledActions[0].recurrence.periodType).isEqualTo(PeriodType.WEEK)
    }

    //no recurrence is set
    @Test
    fun everyScheduledActionShouldHaveRecurrence() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            actionUID = generateUID()
        }
        val sx = scheduledActionDbAdapter.addRecord(scheduledAction)
        assertThat(sx.recurrence).isNotNull()
        assertThat(sx.recurrence.periodType).isEqualTo(PeriodType.ONCE)
    }

    @Test
    fun generate_repeat_string_count() {
        val recurrence = Recurrence(PeriodType.MONTH, 2)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            setRecurrence(recurrence)
            totalPlannedExecutionCount = 4
        }
        val repeatString = recurrence.formatRepeatString(context)

        assertThat(repeatString)
            .isEqualTo("Every 2 months ; for 4 times")
        assertThat(scheduledAction.getRepeatString(context))
            .isEqualTo(repeatString)
    }

    @Test
    fun generate_repeat_string_until() {
        val until = System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS
        val untilFormat = DateUtils.formatDateTime(context, until, DateUtils.FORMAT_NUMERIC_DATE)
        val recurrence = Recurrence(PeriodType.MONTH, 2)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            setRecurrence(recurrence)
            endDate = until
        }
        val repeatString = recurrence.formatRepeatString(context)

        assertThat(repeatString)
            .isEqualTo("Every 2 months ; until $untilFormat")
        assertThat(scheduledAction.getRepeatString(context))
            .isEqualTo(repeatString)
    }

    @Test
    fun testAddGetRecord() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.EXPORT) {
            name = "Some Name"
            actionUID = "Some TX UID"
            advanceCreateDays = 1
            advanceRemindDays = 2
            isAutoCreate = true
            isAutoCreateNotify = true
            isEnabled = true
            startDate = 11111
            endDate = 33333
            lastRunTime = 22222
            instanceCount = 3
            setRecurrence(Recurrence(PeriodType.MONTH))
            tag = "QIF;SD_CARD;2016-06-25 12:56:07.175;false"
        }
        scheduledActionDbAdapter.addRecord(scheduledAction)

        val scheduledActionFromDb = scheduledActionDbAdapter.getRecord(scheduledAction.uid)
        assertThat(scheduledActionFromDb.name).isEqualTo(scheduledAction.name)
        assertThat(scheduledActionFromDb.uid).isEqualTo(scheduledAction.uid)
        assertThat(scheduledActionFromDb.actionUID).isEqualTo(scheduledAction.actionUID)
        assertThat(scheduledActionFromDb.advanceCreateDays).isEqualTo(scheduledAction.advanceCreateDays)
        assertThat(scheduledActionFromDb.advanceRemindDays).isEqualTo(scheduledAction.advanceRemindDays)
        assertThat(scheduledActionFromDb.isAutoCreate).isEqualTo(scheduledAction.isAutoCreate)
        assertThat(scheduledActionFromDb.isAutoCreateNotify).isEqualTo(scheduledAction.isAutoCreateNotify)
        assertThat(scheduledActionFromDb.isEnabled).isEqualTo(scheduledAction.isEnabled)
        assertThat(scheduledActionFromDb.startDate).isEqualTo(scheduledAction.startDate)
        assertThat(scheduledActionFromDb.endDate).isEqualTo(scheduledAction.endDate)
        assertThat(scheduledActionFromDb.lastRunTime).isEqualTo(scheduledAction.lastRunTime)
        assertThat(scheduledActionFromDb.instanceCount).isEqualTo(scheduledAction.instanceCount)
        assertThat(scheduledActionFromDb.recurrence).isEqualTo(scheduledAction.recurrence)
        assertThat(scheduledActionFromDb.tag).isEqualTo(scheduledAction.tag)
    }
}
