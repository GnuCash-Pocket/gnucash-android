package org.gnucash.android.ui.search

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.toDateTimeAtEndOfDay
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDate
import org.junit.Test
import java.math.BigDecimal

class SearchFormTest : GnuCashTest() {
    @Test
    fun `Query for single description - contains`() {
        val form = SearchForm()
        val criterion = form.addDescription()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Contains
        val where = form.buildSQL()
        val expected = "(t.${TransactionEntry.COLUMN_DESCRIPTION} LIKE '%zebra%')"
        assertThat(where).isEqualTo(expected)
    }

    @Test
    fun `Query for single description - equals`() {
        val form = SearchForm()
        val criterion = form.addDescription()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Equals
        val where = form.buildSQL()
        val expected = "(t.${TransactionEntry.COLUMN_DESCRIPTION} = 'zebra')"
        assertThat(where).isEqualTo(expected)
    }

    @Test
    fun `Query for single note - contains`() {
        val column = "t." + TransactionEntry.COLUMN_NOTES
        val form = SearchForm()
        val criterion = form.addNote()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Contains
        val where = form.buildSQL()
        val expected = "($column LIKE '%zebra%')"
        assertThat(where).isEqualTo(expected)
    }

    @Test
    fun `Query for single note - equals`() {
        val column = "t." + TransactionEntry.COLUMN_NOTES
        val form = SearchForm()
        val criterion = form.addNote()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Equals
        val where = form.buildSQL()
        val expected = "($column = 'zebra')"
        assertThat(where).isEqualTo(expected)
    }

    @Test
    fun `Query for single memo - contains`() {
        val column1 = "s1." + SplitEntry.COLUMN_MEMO
        val column2 = "s2." + SplitEntry.COLUMN_MEMO
        val form = SearchForm()
        val criterion = form.addMemo()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Contains
        val where = form.buildSQL()
        val expected = "(($column1 LIKE '%zebra%') OR ($column2 LIKE '%zebra%'))"
        assertThat(where).isEqualTo(expected)
    }

    @Test
    fun `Query for single memo - equals`() {
        val column1 = "s1." + SplitEntry.COLUMN_MEMO
        val column2 = "s2." + SplitEntry.COLUMN_MEMO
        val form = SearchForm()
        val criterion = form.addMemo()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Equals
        val where = form.buildSQL()
        val expected = "(($column1 = 'zebra') OR ($column2 = 'zebra'))"
        assertThat(where).isEqualTo(expected)
    }

    @Test
    fun `Query for single date`() {
        val column = "t." + TransactionEntry.COLUMN_TIMESTAMP
        val form = SearchForm()
        val criterion = form.addDate()
        val now = LocalDate.now()
        val dayStart = now.toDateTimeAtStartOfDay()
        val dayEnd = now.toDateTimeAtEndOfDay()

        criterion.value = now
        criterion.compare = Compare.GreaterThan
        var where = form.buildSQL()
        assertThat(where).isEqualTo("($column > ${dayStart.toMillis()})")

        criterion.compare = Compare.GreaterThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column >= ${dayStart.toMillis()})")

        criterion.compare = Compare.LessThan
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column < ${dayEnd.toMillis()})")

        criterion.compare = Compare.LessThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column <= ${dayEnd.toMillis()})")

        criterion.compare = Compare.EqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column BETWEEN ${dayStart.toMillis()} AND ${dayEnd.toMillis()})")

        criterion.compare = Compare.NotEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column NOT BETWEEN ${dayStart.toMillis()} AND ${dayEnd.toMillis()})")
    }

    @Test
    fun `Query for single amount`() {
        val columnN1 = "s1." + SplitEntry.COLUMN_VALUE_NUM
        val columnD1 = "s1." + SplitEntry.COLUMN_VALUE_DENOM
        val columnN2 = "s2." + SplitEntry.COLUMN_VALUE_NUM
        val columnD2 = "s2." + SplitEntry.COLUMN_VALUE_DENOM
        val column1 = "($columnN1 * 1.0 / $columnD1)"
        val column2 = "($columnN2 * 1.0 / $columnD2)"
        val form = SearchForm()
        val criterion = form.addNumeric(false)
        criterion.value = BigDecimal.valueOf(123.45)
        criterion.compare = Compare.GreaterThan
        var where = form.buildSQL()
        var expected = "(($column1 > 123.45) OR ($column2 > 123.45))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.GreaterThanOrEqualTo
        where = form.buildSQL()
        expected = "(($column1 >= 123.45) OR ($column2 >= 123.45))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.LessThan
        where = form.buildSQL()
        expected = "(($column1 < 123.45) OR ($column2 < 123.45))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.LessThanOrEqualTo
        where = form.buildSQL()
        expected = "(($column1 <= 123.45) OR ($column2 <= 123.45))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.EqualTo
        where = form.buildSQL()
        expected = "(($column1 = 123.45) OR ($column2 = 123.45))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.NotEqualTo
        where = form.buildSQL()
        expected = "(($column1 <> 123.45) OR ($column2 <> 123.45))"
        assertThat(where).isEqualTo(expected)
    }

    @Test
    fun `Query for single value - debits`() {
        `Query for single value`(NumericMatch.HasDebits)
    }

    @Test
    fun `Query for single value - credits`() {
        `Query for single value`(NumericMatch.HasCredits)
    }

    private fun `Query for single value`(match: NumericMatch) {
        val typeName = if (match == NumericMatch.HasDebits) {
            TransactionType.DEBIT.value
        } else {
            TransactionType.CREDIT.value
        }

        val columnN1 = "s1." + SplitEntry.COLUMN_VALUE_NUM
        val columnD1 = "s1." + SplitEntry.COLUMN_VALUE_DENOM
        val columnT1 = "s1." + SplitEntry.COLUMN_TYPE
        val column1 = "($columnN1 * 1.0 / $columnD1)"
        val whereT1 = "($columnT1 = '$typeName')"

        val columnN2 = "s2." + SplitEntry.COLUMN_VALUE_NUM
        val columnD2 = "s2." + SplitEntry.COLUMN_VALUE_DENOM
        val columnT2 = "s2." + SplitEntry.COLUMN_TYPE
        val column2 = "($columnN2 * 1.0 / $columnD2)"
        val whereT2 = "($columnT2 = '$typeName')"

        val form = SearchForm()
        val criterion = form.addNumeric(true)
        criterion.value = BigDecimal.valueOf(123.45)
        criterion.compare = Compare.GreaterThan
        criterion.match = match
        var where = form.buildSQL()
        var expected = "((($column1 > 123.45) AND $whereT1) OR (($column2 > 123.45) AND $whereT2))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.GreaterThanOrEqualTo
        where = form.buildSQL()
        expected = "((($column1 >= 123.45) AND $whereT1) OR (($column2 >= 123.45) AND $whereT2))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.LessThan
        where = form.buildSQL()
        expected = "((($column1 < 123.45) AND $whereT1) OR (($column2 < 123.45) AND $whereT2))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.LessThanOrEqualTo
        where = form.buildSQL()
        expected = "((($column1 <= 123.45) AND $whereT1) OR (($column2 <= 123.45) AND $whereT2))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.EqualTo
        where = form.buildSQL()
        expected = "((($column1 = 123.45) AND $whereT1) OR (($column2 = 123.45) AND $whereT2))"
        assertThat(where).isEqualTo(expected)

        criterion.compare = Compare.NotEqualTo
        where = form.buildSQL()
        expected = "((($column1 <> 123.45) AND $whereT1) OR (($column2 <> 123.45) AND $whereT2))"
        assertThat(where).isEqualTo(expected)
    }
}