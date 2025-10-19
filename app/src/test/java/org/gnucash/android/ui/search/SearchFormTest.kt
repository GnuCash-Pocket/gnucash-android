package org.gnucash.android.ui.search

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.toDateTimeAtEndOfDay
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDate
import org.junit.Test
import java.math.BigDecimal

class SearchFormTest : GnuCashTest() {
    @Test
    fun `Query for single description`() {
        val form = SearchForm()
        val criterion = form.addDescription()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Contains
        var where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_DESCRIPTION} LIKE '%zebra%')")

        criterion.compare = StringCompare.Equals
        where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_DESCRIPTION} = 'zebra')")
    }

    @Test
    fun `Query for single note`() {
        val form = SearchForm()
        val criterion = form.addNote()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Contains
        var where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_NOTES} LIKE '%zebra%')")

        criterion.compare = StringCompare.Equals
        where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_NOTES} = 'zebra')")
    }

    @Test
    fun `Query for single memo`() {
        val form = SearchForm()
        val criterion = form.addMemo()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Contains
        var where = form.buildSQL()
        assertThat(where).isEqualTo("(${SplitEntry.COLUMN_MEMO} LIKE '%zebra%')")

        criterion.compare = StringCompare.Equals
        where = form.buildSQL()
        assertThat(where).isEqualTo("(${SplitEntry.COLUMN_MEMO} = 'zebra')")
    }

    @Test
    fun `Query for single date`() {
        val form = SearchForm()
        val criterion = form.addDate()
        val now = LocalDate.now()
        val dayStart = now.toDateTimeAtStartOfDay()
        val dayEnd = now.toDateTimeAtEndOfDay()

        criterion.value = now
        criterion.compare = Compare.GreaterThan
        var where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_TIMESTAMP} > ${dayStart.toMillis()})")

        criterion.compare = Compare.GreaterThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_TIMESTAMP} >= ${dayStart.toMillis()})")

        criterion.compare = Compare.LessThan
        where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_TIMESTAMP} < ${dayEnd.toMillis()})")

        criterion.compare = Compare.LessThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_TIMESTAMP} <= ${dayEnd.toMillis()})")

        criterion.compare = Compare.EqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_TIMESTAMP} BETWEEN ${dayStart.toMillis()} AND ${dayEnd.toMillis()})")

        criterion.compare = Compare.NotEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_TIMESTAMP} NOT BETWEEN ${dayStart.toMillis()} AND ${dayEnd.toMillis()})")
    }

    @Test
    fun `Query for single amount`() {
        val form = SearchForm()
        val criterion = form.addNumeric()
        criterion.value = BigDecimal.valueOf(123.45)
        criterion.compare = Compare.GreaterThan
        var where = form.buildSQL()
        assertThat(where).isEqualTo("((${SplitEntry.COLUMN_VALUE_NUM}/${SplitEntry.COLUMN_VALUE_DENOM}) > 123.45)")

        criterion.compare = Compare.GreaterThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("((${SplitEntry.COLUMN_VALUE_NUM}/${SplitEntry.COLUMN_VALUE_DENOM}) >= 123.45)")

        criterion.compare = Compare.LessThan
        where = form.buildSQL()
        assertThat(where).isEqualTo("((${SplitEntry.COLUMN_VALUE_NUM}/${SplitEntry.COLUMN_VALUE_DENOM}) < 123.45)")

        criterion.compare = Compare.LessThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("((${SplitEntry.COLUMN_VALUE_NUM}/${SplitEntry.COLUMN_VALUE_DENOM}) <= 123.45)")

        criterion.compare = Compare.EqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("((${SplitEntry.COLUMN_VALUE_NUM}/${SplitEntry.COLUMN_VALUE_DENOM}) = 123.45)")

        criterion.compare = Compare.NotEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("((${SplitEntry.COLUMN_VALUE_NUM}/${SplitEntry.COLUMN_VALUE_DENOM}) <> 123.45)")
    }

}