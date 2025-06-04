package org.gnucash.android.test.unit.db

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
        scheduledActionDbAdapter = ScheduledActionDbAdapter.getInstance()
    }

    fun shouldFetchOnlyEnabledScheduledActions() {
        var scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction.setRecurrence(Recurrence(PeriodType.MONTH))
        scheduledAction.isEnabled = false

        scheduledActionDbAdapter.addRecord(scheduledAction)

        scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction.setRecurrence(Recurrence(PeriodType.WEEK))
        scheduledActionDbAdapter.addRecord(scheduledAction)

        assertThat(scheduledActionDbAdapter.allRecords).hasSize(2)

        val enabledActions = scheduledActionDbAdapter.allEnabledScheduledActions
        assertThat(enabledActions).hasSize(1)
        assertThat(enabledActions[0].recurrence!!.periodType).isEqualTo(PeriodType.WEEK)
    }

    @Test(expected = NullPointerException::class) //no recurrence is set
    fun everyScheduledActionShouldHaveRecurrence() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction.actionUID = generateUID()
        scheduledActionDbAdapter.addRecord(scheduledAction)
    }

    @Test
    fun testGenerateRepeatString() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        val periodType = PeriodType.MONTH
        val recurrence = Recurrence(periodType)
        recurrence.multiplier = 2
        scheduledAction.setRecurrence(recurrence)
        scheduledAction.totalPlannedExecutionCount = 4
        val res = context.resources
        val repeatString = recurrence.frequencyRepeatString(context) + ", " +
                res.getString(R.string.repeat_x_times, 4)

        assertThat(scheduledAction.getRepeatString(context).trim { it <= ' ' })
            .isEqualTo(repeatString)
    }

    @Test
    fun testAddGetRecord() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.BACKUP)
        scheduledAction.actionUID = "Some UID"
        scheduledAction.advanceCreateDays = 1
        scheduledAction.advanceNotifyDays = 2
        scheduledAction.setAutoCreate(true)
        scheduledAction.setAutoNotify(true)
        scheduledAction.isEnabled = true
        scheduledAction.startTime = 11111
        scheduledAction.endTime = 33333
        scheduledAction.lastRunTime = 22222
        scheduledAction.executionCount = 3
        scheduledAction.setRecurrence(Recurrence(PeriodType.MONTH))
        scheduledAction.tag = "QIF;SD_CARD;2016-06-25 12:56:07.175;false"
        scheduledActionDbAdapter.addRecord(scheduledAction)

        val scheduledActionFromDb = scheduledActionDbAdapter.getRecord(scheduledAction.uid)
        assertThat(scheduledActionFromDb.uid).isEqualTo(scheduledAction.uid)
        assertThat(scheduledActionFromDb.actionUID).isEqualTo(scheduledAction.actionUID)
        assertThat(scheduledActionFromDb.advanceCreateDays).isEqualTo(scheduledAction.advanceCreateDays)
        assertThat(scheduledActionFromDb.advanceNotifyDays).isEqualTo(scheduledAction.advanceNotifyDays)
        assertThat(scheduledActionFromDb.shouldAutoCreate()).isEqualTo(scheduledAction.shouldAutoCreate())
        assertThat(scheduledActionFromDb.shouldAutoNotify()).isEqualTo(scheduledAction.shouldAutoNotify())
        assertThat(scheduledActionFromDb.isEnabled).isEqualTo(scheduledAction.isEnabled)
        assertThat(scheduledActionFromDb.startTime).isEqualTo(scheduledAction.startTime)
        assertThat(scheduledActionFromDb.endTime).isEqualTo(scheduledAction.endTime)
        assertThat(scheduledActionFromDb.lastRunTime).isEqualTo(scheduledAction.lastRunTime)
        assertThat(scheduledActionFromDb.executionCount).isEqualTo(scheduledAction.executionCount)
        assertThat(scheduledActionFromDb.recurrence).isEqualTo(scheduledAction.recurrence)
        assertThat(scheduledActionFromDb.tag).isEqualTo(scheduledAction.tag)
    }
}
