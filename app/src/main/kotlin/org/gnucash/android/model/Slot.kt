package org.gnucash.android.model

/**
 * Based on `KvpValue`
 */
data class Slot(
    var key: String,
    var type: Type,
    var value: Any? = null
) {
    val isDate: Boolean get() = (type === Type.GDATE) && (value is Long)

    val isDouble: Boolean get() = (type === Type.DOUBLE) && (value is Double)

    val isFrame: Boolean get() = (type === Type.FRAME) && (value is List<*>)

    val isGUID: Boolean get() = (type === Type.GUID) && (value!!.toString().length == 32)

    val isLong: Boolean get() = (type === Type.INT64) && (value is Long)

    val isNumeric: Boolean get() = (type === Type.NUMERIC) && (value is Numeric)

    val isString: Boolean get() = (type === Type.STRING) && ((value is String) || (value is String?))

    val isDateTime: Boolean get() = (type === Type.TIME64) && (value is Long)

    val asDate: Long
        get() = if (isDate) value as Long
        else throw TypeCastException(type.attribute)

    val asDateTime: Long
        get() = if (isDateTime) value as Long
        else throw TypeCastException(type.attribute)

    val asDouble: Double
        get() = if (isDouble) value as Double
        else throw TypeCastException(type.attribute)

    val asFrame: List<Slot>
        get() = if (isFrame) value as List<Slot>
        else throw TypeCastException(type.attribute)

    val asGUID: String
        get() = if (isGUID) value as String
        else throw TypeCastException(type.attribute)

    val asLong: Long
        get() = if (isLong) value as Long
        else throw TypeCastException(type.attribute)

    val asNumeric: Numeric
        get() = if (isNumeric) value as Numeric
        else throw TypeCastException(type.attribute)

    val asString: String
        get() = if (isString) value as String
        else throw TypeCastException(type.attribute)

    override fun toString(): String {
        return "$key=$value"
    }

    fun add(slot: Slot) {
        if (type === Type.FRAME) {
            value = if (value == null) {
                listOf(slot)
            } else {
                (value as List<*>) + slot
            }
        }
    }

    enum class Type(
        val value: Int,
        val attribute: String
    ) {
        INVALID(-1, ""),
        INT64(1, "int64"),
        DOUBLE(2, "double"),
        NUMERIC(3, "numeric"),
        STRING(4, "string"),
        GUID(5, "guid"),
        TIME64(6, "time64"),
        PLACEHOLDER_DONT_USE(7, "binary"),
        GLIST(8, "glist"),
        FRAME(9, "frame"),
        GDATE(10, "gdate");

        override fun toString(): String {
            return attribute
        }

        companion object {
            private val values = values()

            fun of(ordinal: Int): Type {
                return values.firstOrNull { it.value == ordinal } ?: INVALID
            }

            fun of(value: String): Type {
                return values.firstOrNull { it.attribute == value } ?: INVALID
            }
        }
    }

    companion object {
        fun frame(key: String, slots: List<Slot>): Slot = Slot(key, Type.FRAME, slots)

        fun gdate(key: String, date: Long): Slot = Slot(key, Type.GDATE, date)

        fun guid(key: String, guid: String): Slot = Slot(key, Type.GUID, guid)

        fun long(key: String, number: Long): Slot = Slot(key, Type.INT64, number)

        fun numeric(key: String, numerator: Long, denominator: Long): Slot =
            Slot(key, Type.NUMERIC, Numeric(numerator, denominator).reduce())

        fun numeric(key: String, numerator: String, denominator: String): Slot =
            numeric(key, numerator.toLong(), denominator.toLong())

        fun string(key: String, value: String): Slot = Slot(key, Type.STRING, value)

        fun numeric(key: String, value: Money) = numeric(key, value.numerator, value.denominator)

        fun empty() = Slot("", Type.STRING)
    }
}
