package org.gnucash.android.ui.search

import android.database.DatabaseUtils.sqlEscapeString
import org.gnucash.android.db.DatabaseHelper.Companion.sqlEscapeLike
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.util.toDateTimeAtEndOfDay
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDate
import java.math.BigDecimal

data class SearchForm(
    var comparisonType: ComparisonType = ComparisonType.All
) {
    private val _criteria: MutableList<SearchCriteria> = mutableListOf()
    val criteria: List<SearchCriteria> get() = _criteria

    fun addDescription(): SearchCriteria.Description {
        val criterion = SearchCriteria.Description()
        _criteria.add(criterion)
        return criterion
    }

    fun addNote(): SearchCriteria.Note {
        val criterion = SearchCriteria.Note()
        _criteria.add(criterion)
        return criterion
    }

    fun addMemo(): SearchCriteria.Memo {
        val criterion = SearchCriteria.Memo()
        _criteria.add(criterion)
        return criterion
    }

    fun addDate(): SearchCriteria.Date {
        val criterion = SearchCriteria.Date()
        _criteria.add(criterion)
        return criterion
    }

    fun addNumeric(): SearchCriteria.Numeric {
        val criterion = SearchCriteria.Numeric()
        _criteria.add(criterion)
        return criterion
    }

    fun remove(criterion: SearchCriteria) {
        _criteria.remove(criterion)
    }

    fun buildSQL(): String {
        val where = StringBuilder()
        for (criterion in criteria) {
            if (where.isNotEmpty()) {
                if (comparisonType == ComparisonType.All) {
                    where.append(" AND ")
                } else {
                    where.append(" OR ")
                }
            }
            appendQuery(criterion, where)
        }

        return where.toString()
    }

    private fun appendQuery(criterion: SearchCriteria, where: StringBuilder) {
        when (criterion) {
            is SearchCriteria.Numeric -> appendQuery(criterion, where)
            is SearchCriteria.Date -> appendQuery(criterion, where)
            is SearchCriteria.Description -> appendQuery(criterion, where)
            is SearchCriteria.Memo -> appendQuery(criterion, where)
            is SearchCriteria.Note -> appendQuery(criterion, where)
        }
    }

    private fun appendQuery(criterion: SearchCriteria.Numeric, where: StringBuilder) {
        val a = criterion.value ?: BigDecimal.ZERO
        where.append('(')
            .append('(')
            .append(SplitEntry.COLUMN_VALUE_NUM)
            .append('/')
            .append(SplitEntry.COLUMN_VALUE_DENOM)
            .append(')')
        when (criterion.compare) {
            Compare.LessThan -> where.append(" < ")
            Compare.LessThanOrEqualTo -> where.append(" <= ")
            Compare.EqualTo -> where.append(" = ")
            Compare.NotEqualTo -> where.append(" <> ")
            Compare.GreaterThan -> where.append(" > ")
            Compare.GreaterThanOrEqualTo -> where.append(" >= ")
        }
        where.append(a)
            .append(')')
    }

    private fun appendQuery(criterion: SearchCriteria.Date, where: StringBuilder) {
        val d = criterion.value ?: LocalDate.now()
        val dayStart = d.toDateTimeAtStartOfDay()
        val dayEnd = d.toDateTimeAtEndOfDay()

        where.append('(')
            .append(TransactionEntry.COLUMN_TIMESTAMP)

        when (criterion.compare) {
            Compare.LessThan -> {
                where.append(" < ").append(dayEnd.toMillis())
            }

            Compare.LessThanOrEqualTo -> {
                where.append(" <= ").append(dayEnd.toMillis())
            }

            Compare.EqualTo -> {
                where.append(" BETWEEN ")
                    .append(dayStart.toMillis())
                    .append(" AND ")
                    .append(dayEnd.toMillis())
            }

            Compare.NotEqualTo -> {
                where.append(" NOT BETWEEN ")
                    .append(dayStart.toMillis())
                    .append(" AND ")
                    .append(dayEnd.toMillis())
            }

            Compare.GreaterThan -> {
                where.append(" > ").append(dayStart.toMillis())
            }

            Compare.GreaterThanOrEqualTo -> {
                where.append(" >= ").append(dayStart.toMillis())
            }
        }

        where.append(')')
    }

    private fun appendQuery(
        criterion: SearchCriteria.Description,
        where: StringBuilder
    ) {
        val s = criterion.value.orEmpty()
        where.append('(')
            .append(TransactionEntry.COLUMN_DESCRIPTION)
        when (criterion.compare) {
            StringCompare.Contains -> where.append(" LIKE ")
                .append(sqlEscapeLike(s))

            StringCompare.Equals -> where.append(" = ")
                .append(sqlEscapeString(s))
        }
        where.append(')')
    }

    private fun appendQuery(criterion: SearchCriteria.Memo, where: StringBuilder) {
        val s = criterion.value.orEmpty()
        where.append('(')
            .append(SplitEntry.COLUMN_MEMO)
        when (criterion.compare) {
            StringCompare.Contains -> where.append(" LIKE ")
                .append(sqlEscapeLike(s))

            StringCompare.Equals -> where.append(" = ")
                .append(sqlEscapeString(s))
        }
        where.append(')')
    }

    private fun appendQuery(criterion: SearchCriteria.Note, where: StringBuilder) {
        val s = criterion.value.orEmpty()
        where.append('(')
            .append(TransactionEntry.COLUMN_NOTES)
        when (criterion.compare) {
            StringCompare.Contains -> where.append(" LIKE ")
                .append(sqlEscapeLike(s))

            StringCompare.Equals -> where.append(" = ")
                .append(sqlEscapeString(s))
        }
        where.append(')')
    }
}
