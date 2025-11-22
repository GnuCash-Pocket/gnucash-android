package org.gnucash.android.ui.search

import android.database.DatabaseUtils.sqlEscapeString
import org.gnucash.android.db.DatabaseHelper.Companion.sqlEscapeLike
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.toDateTimeAtEndOfDay
import org.gnucash.android.util.toMillis
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

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

    fun addNumber(): SearchCriteria.Number {
        val criterion = SearchCriteria.Number()
        _criteria.add(criterion)
        return criterion
    }

    fun addDate(): SearchCriteria.Date {
        val criterion = SearchCriteria.Date()
        _criteria.add(criterion)
        return criterion
    }

    fun addNumeric(debcred: Boolean): SearchCriteria.Numeric {
        val match = if (debcred) NumericMatch.HasDebitsOrCredits else null
        val criterion = SearchCriteria.Numeric(match = match)
        _criteria.add(criterion)
        return criterion
    }

    fun addAccount(): SearchCriteria.Account {
        val criterion = SearchCriteria.Account()
        _criteria.add(criterion)
        return criterion
    }

    fun remove(criterion: SearchCriteria) {
        _criteria.remove(criterion)
    }

    fun buildSQL(): String {
        val compareAll = comparisonType == ComparisonType.All
        val where = StringBuilder()
        for (criterion in criteria) {
            if (criterion.isEmpty()) continue
            if (where.isNotEmpty()) {
                if (compareAll) {
                    where.append(" AND ")
                } else {
                    where.append(" OR ")
                }
            }
            where.append('(')
                .appendQuery(criterion)
                .append(')')
        }
        return where.toString()
    }

    companion object {
        private const val TRANSACTION_ALIAS = "t."
        private const val SPLIT1_ALIAS = "s1."
        private const val SPLIT2_ALIAS = "s2."

        private val amountFormatter by lazy {
            (NumberFormat.getInstance(Locale.ROOT) as DecimalFormat).apply {
                isGroupingUsed = false
                maximumFractionDigits = 6
            }
        }

        private fun StringBuilder.appendQuery(criterion: SearchCriteria): StringBuilder {
            when (criterion) {
                is SearchCriteria.Account -> appendQuery(criterion)
                is SearchCriteria.Date -> appendQuery(criterion)
                is SearchCriteria.Description -> appendQuery(criterion)
                is SearchCriteria.Memo -> appendQuery(criterion)
                is SearchCriteria.Note -> appendQuery(criterion)
                is SearchCriteria.Number -> appendQuery(criterion)
                is SearchCriteria.Numeric -> appendQuery(criterion)
            }
            return this
        }

        private fun StringBuilder.appendQuery(criterion: SearchCriteria.Account): StringBuilder {
            val s = criterion.value?.uid ?: return this
            when (criterion.compare) {
                ComparisonType.Any -> {
                    append('(')
                    append(SPLIT1_ALIAS).append(SplitEntry.COLUMN_ACCOUNT_UID)
                    append(" = ").append(sqlEscapeString(s))
                    append(") OR (")
                    append(SPLIT2_ALIAS).append(SplitEntry.COLUMN_ACCOUNT_UID)
                    append(" = ").append(sqlEscapeString(s))
                    append(')')
                }

                ComparisonType.None -> {
                    // THis is the culprit for joining `s1` and `s2`
                    append('(')
                    append(SPLIT1_ALIAS).append(SplitEntry.COLUMN_ACCOUNT_UID)
                    append(" <> ").append(sqlEscapeString(s))
                    append(") AND (")
                    append(SPLIT2_ALIAS).append(SplitEntry.COLUMN_ACCOUNT_UID)
                    append(" <> ").append(sqlEscapeString(s))
                    append(')')
                }

                ComparisonType.All -> Unit
            }

            return this
        }

        private fun StringBuilder.appendQuery(criterion: SearchCriteria.Date): StringBuilder {
            val date = criterion.value ?: return this
            val dayStart = date.toDateTimeAtStartOfDay()
            val dayEnd = date.toDateTimeAtEndOfDay()

            append(TRANSACTION_ALIAS).append(TransactionEntry.COLUMN_TIMESTAMP)

            when (criterion.compare) {
                Compare.LessThan -> {
                    append(" < ").append(dayEnd.toMillis())
                }

                Compare.LessThanOrEqualTo -> {
                    append(" <= ").append(dayEnd.toMillis())
                }

                Compare.EqualTo -> {
                    append(" BETWEEN ")
                    append(dayStart.toMillis())
                    append(" AND ")
                    append(dayEnd.toMillis())
                }

                Compare.NotEqualTo -> {
                    append(" NOT BETWEEN ")
                    append(dayStart.toMillis())
                    append(" AND ")
                    append(dayEnd.toMillis())
                }

                Compare.GreaterThan -> {
                    append(" > ").append(dayStart.toMillis())
                }

                Compare.GreaterThanOrEqualTo -> {
                    append(" >= ").append(dayStart.toMillis())
                }
            }

            return this
        }

        private fun StringBuilder.appendQuery(criterion: SearchCriteria.Description): StringBuilder {
            val s = criterion.value ?: return this
            append(TRANSACTION_ALIAS).append(TransactionEntry.COLUMN_DESCRIPTION)
            when (criterion.compare) {
                StringCompare.Contains -> append(" LIKE ").append(sqlEscapeLike(s))

                StringCompare.Equals -> append(" = ").append(sqlEscapeString(s))
            }
            return this
        }

        private fun StringBuilder.appendQuery(criterion: SearchCriteria.Memo): StringBuilder {
            val s = criterion.value ?: return this
            when (criterion.compare) {
                StringCompare.Contains -> {
                    val ss = sqlEscapeLike(s)
                    append('(')
                    append(SPLIT1_ALIAS).append(SplitEntry.COLUMN_MEMO)
                    append(" LIKE ").append(ss)
                    append(") OR (")
                    append(SPLIT2_ALIAS).append(SplitEntry.COLUMN_MEMO)
                    append(" LIKE ").append(ss)
                    append(')')
                }

                StringCompare.Equals -> {
                    val ss = sqlEscapeString(s)
                    append('(')
                    append(SPLIT1_ALIAS).append(SplitEntry.COLUMN_MEMO)
                    append(" = ").append(ss)
                    append(") OR (")
                    append(SPLIT2_ALIAS).append(SplitEntry.COLUMN_MEMO)
                    append(" = ").append(ss)
                    append(')')
                }
            }
            return this
        }

        private fun StringBuilder.appendQuery(criterion: SearchCriteria.Note): StringBuilder {
            val s = criterion.value ?: return this
            append(TRANSACTION_ALIAS).append(TransactionEntry.COLUMN_NOTES)
            when (criterion.compare) {
                StringCompare.Contains -> append(" LIKE ").append(sqlEscapeLike(s))

                StringCompare.Equals -> append(" = ").append(sqlEscapeString(s))
            }
            return this
        }

        private fun StringBuilder.appendQuery(criterion: SearchCriteria.Number): StringBuilder {
            val s = criterion.value ?: return this
            append(TRANSACTION_ALIAS).append(TransactionEntry.COLUMN_NUMBER)
            when (criterion.compare) {
                StringCompare.Contains -> append(" LIKE ").append(sqlEscapeLike(s))

                StringCompare.Equals -> append(" = ").append(sqlEscapeString(s))
            }
            return this
        }

        private fun StringBuilder.appendQuery(criterion: SearchCriteria.Numeric): StringBuilder {
            if (criterion.value == null) return this

            append('(')
            appendQuery(criterion, SPLIT1_ALIAS)
            append(") OR (")
            appendQuery(criterion, SPLIT2_ALIAS)
            append(')')

            return this
        }

        private fun StringBuilder.appendQuery(
            criterion: SearchCriteria.Numeric,
            alias: String
        ): StringBuilder {
            val amount = criterion.value!!
            val whereLength = this.length
            append('(')
            append(alias).append(SplitEntry.COLUMN_VALUE_NUM)
            append(" * 1.0 / ")
            append(alias).append(SplitEntry.COLUMN_VALUE_DENOM)
            append(')')
            when (criterion.compare) {
                Compare.LessThan -> append(" < ")
                Compare.LessThanOrEqualTo -> append(" <= ")
                Compare.EqualTo -> append(" = ")
                Compare.NotEqualTo -> append(" <> ")
                Compare.GreaterThan -> append(" > ")
                Compare.GreaterThanOrEqualTo -> append(" >= ")
            }
            append(amountFormatter.format(amount))

            if (criterion.match != null && criterion.match != NumericMatch.HasDebitsOrCredits) {
                val typeName = if (criterion.match == NumericMatch.HasDebits) {
                    TransactionType.DEBIT.value
                } else {
                    TransactionType.CREDIT.value
                }
                insert(whereLength, '(')
                append(") AND (")
                append(alias).append(SplitEntry.COLUMN_TYPE)
                append(" = ")
                append(sqlEscapeString(typeName))
                append(')')
            }

            return this
        }
    }
}
