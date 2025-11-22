package org.gnucash.android.ui.search

import org.gnucash.android.util.toMillis
import org.joda.time.LocalDate
import java.math.BigDecimal

enum class ComparisonType {
    All,
    Any,
    None
}

enum class StringCompare {
    Contains,
    Equals
    //TODO Regex
}

enum class Compare {
    // QOF_COMPARE_LT
    LessThan,

    // QOF_COMPARE_LTE
    LessThanOrEqualTo,

    // QOF_COMPARE_EQUAL
    EqualTo,

    // QOF_COMPARE_NEQ
    NotEqualTo,

    // QOF_COMPARE_GT
    GreaterThan,

    // QOF_COMPARE_GTE
    GreaterThanOrEqualTo
}

enum class NumericMatch {
    // QOF_NUMERIC_MATCH_ANY
    HasDebitsOrCredits,

    // QOF_NUMERIC_MATCH_CREDIT
    HasCredits,

    // QOF_NUMERIC_MATCH_DEBIT
    HasDebits
}

sealed class SearchCriteria {
    abstract fun isEmpty(): Boolean

    data class Description(
        var value: String? = null,
        var compare: StringCompare = StringCompare.Contains
    ) : SearchCriteria() {
        override fun isEmpty(): Boolean = value == null
    }

    data class Note(
        var value: String? = null,
        var compare: StringCompare = StringCompare.Contains
    ) : SearchCriteria() {
        override fun isEmpty(): Boolean = value == null
    }

    data class Memo(
        var value: String? = null,
        var compare: StringCompare = StringCompare.Contains
    ) : SearchCriteria() {
        override fun isEmpty(): Boolean = value == null
    }

    data class Number(
        var value: String? = null,
        var compare: StringCompare = StringCompare.Contains
    ) : SearchCriteria() {
        override fun isEmpty(): Boolean = value == null
    }

    data class Date(
        var value: LocalDate? = null,
        var compare: Compare = Compare.LessThanOrEqualTo
    ) : SearchCriteria() {
        override fun isEmpty(): Boolean = (value == null) || value.toMillis() <= 0L

        fun set(year: Int, monthOfYear: Int, dayOfMonth: Int): LocalDate {
            val date = LocalDate(year, monthOfYear, dayOfMonth)
            value = date
            return date
        }
    }

    data class Numeric(
        var value: BigDecimal? = null,
        var match: NumericMatch? = null,
        var compare: Compare = Compare.EqualTo
    ) : SearchCriteria() {
        override fun isEmpty(): Boolean = value == null
    }

    data class Account(
        var value: org.gnucash.android.model.Account? = null,
        var compare: ComparisonType = ComparisonType.Any
    ) : SearchCriteria() {
        override fun isEmpty(): Boolean = (value == null) || value?.uid.isNullOrEmpty()
    }
}